package com.tripbudgetplanner.backend.expense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.tripbudgetplanner.backend.budget.TripBudget;
import com.tripbudgetplanner.backend.budget.TripBudgetRepository;
import com.tripbudgetplanner.backend.currency.CurrencyService;
import com.tripbudgetplanner.backend.currency.ExchangeRateProvider;
import com.tripbudgetplanner.backend.currency.ExchangeRates;
import com.tripbudgetplanner.backend.currency.FixedExchangeRateProvider;
import com.tripbudgetplanner.backend.currency.SupportedCurrency;
import com.tripbudgetplanner.backend.expense.dto.BudgetComparisonResponse;
import com.tripbudgetplanner.backend.expense.dto.BudgetComparisonResponse.CategoryComparison;
import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.trip.TripAccessService;
import com.tripbudgetplanner.backend.trip.TripRepository;
import com.tripbudgetplanner.backend.member.TripMemberRepository;
import com.tripbudgetplanner.backend.trip.exception.TripNotFoundException;

// UC11: actual expenses vs. the planned budget
class ExpenseBudgetComparisonTest {

    private final Long tripId = 1L;
    private final Long userId = 10L;

    private ExpenseRepository expenseRepository;
    private TripRepository tripRepository;
    private TripBudgetRepository budgetRepository;
    private ExpenseService service;
    private Trip trip;
    private final List<Expense> expenses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        expenseRepository = mock(ExpenseRepository.class);
        tripRepository = mock(TripRepository.class);
        budgetRepository = mock(TripBudgetRepository.class);

        // Fixed demo rates (1 USD = 0.92 EUR = 150 JPY), so no network is needed
        service = serviceWith(fixedRates());

        // A USD trip owned by user 10
        trip = new Trip();
        trip.setCurrency("USD");
        when(tripRepository.findByIdAndUserId(tripId, userId)).thenReturn(Optional.of(trip));
        when(expenseRepository.findByTripIdOrderByExpenseDateDescIdDesc(tripId)).thenReturn(expenses);
    }

    // ---------- Under, on and over budget ----------

    @Test
    void underBudget() {
        budget("USD", "1000.00", Map.of("Flights", "400.00", "Accommodation", "300.00", "Food", "200.00"));
        expense("Flights", "350.00");
        expense("Accommodation", "250.00");
        expense("Food", "100.00");

        BudgetComparisonResponse result = compare();

        assertEquals("UNDER_BUDGET", result.status());
        assertTrue(result.comparable());
        assertEquals("USD", result.currency());
        assertMoney("1000.00", result.plannedBudget());
        assertMoney("900.00", result.totalAllocated());
        assertMoney("100.00", result.unallocatedBudget());
        assertMoney("700.00", result.totalSpent());
        assertMoney("300.00", result.remainingBudget());
        assertMoney("0", result.overBudgetAmount());
        assertMoney("70.0", result.percentSpent());
        assertEquals(3, result.expenseCount());
        assertNull(result.conversion(), "same currency, nothing converted");
        assertTrue(result.message().contains("300.00 USD left"));

        CategoryComparison flights = category(result, "Flights");
        assertMoney("50.00", flights.remaining());
        assertMoney("87.5", flights.percentSpent());
        assertEquals("UNDER_BUDGET", flights.status());
    }

    @Test
    void onBudget() {
        budget("USD", "500.00", Map.of("Food", "500.00"));
        expense("Food", "200.00");
        expense("Food", "300");   // different scale, same value

        BudgetComparisonResponse result = compare();

        assertEquals("ON_BUDGET", result.status());
        assertMoney("0", result.remainingBudget());
        assertMoney("0", result.overBudgetAmount());
        assertMoney("100.0", result.percentSpent());
        assertEquals("ON_BUDGET", category(result, "Food").status());
    }

    @Test
    void overBudget() {
        budget("USD", "500.00", Map.of("Food", "300.00", "Activities", "200.00"));
        expense("Food", "450.00");
        expense("Activities", "200.00");

        BudgetComparisonResponse result = compare();

        assertEquals("OVER_BUDGET", result.status());
        assertMoney("150.00", result.overBudgetAmount());
        assertMoney("0", result.remainingBudget());
        assertMoney("130.0", result.percentSpent());
        assertTrue(result.message().contains("over budget by 150.00 USD"));

        CategoryComparison food = category(result, "Food");
        assertEquals("OVER_BUDGET", food.status());
        assertMoney("150.00", food.overBudgetAmount());
        assertMoney("0", food.remaining());
        assertEquals("ON_BUDGET", category(result, "Activities").status());
    }

    // ---------- No expenses, no budget, zero budget ----------

    @Test
    void noExpensesShowsEveryBudgetCategoryWithZeroSpent() {
        budget("USD", "800.00", Map.of("Flights", "500.00", "Food", "300.00"));

        BudgetComparisonResponse result = compare();

        assertEquals("UNDER_BUDGET", result.status());
        assertMoney("0", result.totalSpent());
        assertMoney("800.00", result.remainingBudget());
        assertMoney("0.0", result.percentSpent());
        assertEquals(0, result.expenseCount());
        assertEquals(2, result.categories().size());
        assertMoney("0", category(result, "Flights").spent());
    }

    @Test
    void noBudgetReturnsSpendingOnly() {
        expense("Hotel", "120.00");
        expense("Food", "30.00");

        BudgetComparisonResponse result = compare();

        assertEquals("NO_BUDGET", result.status());
        assertFalse(result.comparable());
        assertNull(result.plannedBudget());
        assertNull(result.remainingBudget());
        assertNull(result.percentSpent());
        assertMoney("150.00", result.totalSpent());
        assertMoney("150.00", result.unbudgetedSpent());
        assertTrue(result.message().contains("no budget"));

        // Still grouped with the standard names
        CategoryComparison stay = category(result, "Accommodation");
        assertEquals("UNBUDGETED", stay.status());
        assertNull(stay.allocated());
        assertEquals(List.of("Hotel"), stay.expenseCategories());
    }

    @Test
    void noBudgetAndNoExpenses() {
        BudgetComparisonResponse result = compare();

        assertEquals("NO_BUDGET", result.status());
        assertMoney("0", result.totalSpent());
        assertEquals(0, result.expenseCount());
        assertTrue(result.categories().isEmpty());
    }

    @Test
    void zeroBudgetDoesNotDivideByZero() {
        budget("USD", "0.00", Map.of());

        BudgetComparisonResponse empty = compare();
        assertEquals("ON_BUDGET", empty.status());
        assertNull(empty.percentSpent());

        expense("Food", "10.00");
        BudgetComparisonResponse spent = compare();
        assertEquals("OVER_BUDGET", spent.status());
        assertNull(spent.percentSpent());
        assertMoney("10.00", spent.overBudgetAmount());
    }

    // ---------- Category mapping and unbudgeted spending ----------

    @Test
    void mapsExpenseNamesToBudgetCategories() {
        budget("USD", "2000.00", Map.of(
            "Flights", "800.00", "Accommodation", "600.00", "Local transport", "200.00", "Food", "400.00"));
        expense("Flight", "500.00");
        expense("Hotel", "300.00");
        expense("hotel", "100.00");
        expense("Transportation", "50.00");
        expense("  food ", "20.00");

        BudgetComparisonResponse result = compare();

        assertMoney("500.00", category(result, "Flights").spent());
        assertMoney("400.00", category(result, "Accommodation").spent());
        assertEquals(List.of("Hotel", "hotel"), category(result, "Accommodation").expenseCategories());
        assertMoney("50.00", category(result, "Local transport").spent());
        assertMoney("20.00", category(result, "Food").spent());
        assertMoney("0", result.unbudgetedSpent());

        // Standard categories come back in the frontend's order
        assertEquals(List.of("Flights", "Accommodation", "Food", "Local transport"),
            result.categories().stream().map(CategoryComparison::category).toList());
    }

    @Test
    void customBudgetCategoryMatchesByName() {
        budget("USD", "300.00", Map.of("Shopping", "100.00"));
        expense("shopping", "40.00");

        CategoryComparison shopping = category(compare(), "Shopping");

        assertTrue(shopping.budgeted());
        assertMoney("60.00", shopping.remaining());
    }

    @Test
    void expensesWithoutABudgetCategoryAreReportedAsUnbudgeted() {
        budget("USD", "1000.00", Map.of("Food", "300.00"));
        expense("Food", "100.00");
        expense("Shopping", "60.00");
        expense("shopping", "15.00");
        expense("Souvenirs", "25.00");

        BudgetComparisonResponse result = compare();

        assertMoney("200.00", result.totalSpent());
        assertMoney("100.00", result.unbudgetedSpent());
        assertEquals("UNDER_BUDGET", result.status(), "the unallocated buffer still covers it");

        CategoryComparison shopping = category(result, "Shopping");
        assertFalse(shopping.budgeted());
        assertEquals("UNBUDGETED", shopping.status());
        assertMoney("75.00", shopping.spent());
        assertMoney("0", shopping.allocated());
        assertMoney("75.00", shopping.overBudgetAmount());
        assertNull(shopping.percentSpent());

        // Budget categories first, then unbudgeted ones A-Z
        assertEquals(List.of("Food", "Shopping", "Souvenirs"),
            result.categories().stream().map(CategoryComparison::category).toList());
    }

    // ---------- Currencies ----------

    @Test
    void convertsABudgetInAnotherCurrencyWithoutChangingIt() {
        TripBudget budget = budget("EUR", "920.00", Map.of("Food", "460.00"));
        expense("Food", "100.00");

        BudgetComparisonResponse result = compare();

        // 920 EUR / 0.92 = 1000 USD, 460 EUR = 500 USD
        assertEquals("USD", result.currency());
        assertEquals("EUR", result.budgetCurrency());
        assertMoney("1000.00", result.plannedBudget());
        assertMoney("500.00", category(result, "Food").allocated());
        assertMoney("900.00", result.remainingBudget());

        assertEquals("EUR", result.conversion().from());
        assertEquals("USD", result.conversion().to());
        assertEquals("fallback", result.conversion().source());
        assertTrue(result.conversion().notice() != null, "demo rates are labeled");

        // The stored budget keeps its own amounts and currency
        assertMoney("920.00", budget.getTotalAmount());
        assertMoney("460.00", budget.getAllocations().get("Food"));
        assertEquals("EUR", budget.getCurrency());
        verify(budgetRepository, never()).save(any());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void convertsIntoAZeroDecimalTripCurrency() {
        trip.setCurrency("JPY");
        budget("USD", "100.00", Map.of("Food", "40.00"));
        expense("Food", "3000");

        BudgetComparisonResponse result = compare();

        assertEquals("JPY", result.currency());
        assertMoney("15000", result.plannedBudget());          // 100 USD * 150
        assertMoney("6000", category(result, "Food").allocated());
        assertMoney("12000", result.remainingBudget());
    }

    @Test
    void reportsLiveRatesWhenTheyAreUsed() {
        service = serviceWith(liveRates());
        budget("EUR", "95.00", Map.of());

        BudgetComparisonResponse result = compare();

        assertEquals("live", result.conversion().source());
        assertNull(result.conversion().notice());
        assertEquals(LocalDate.of(2026, 11, 20), result.conversion().rateDate());
        assertMoney("100.00", result.plannedBudget());          // 95 EUR / 0.95
    }

    @Test
    void usesOneExchangeRateSnapshotForTheWholeComparison() {
        // This provider gives a different EUR rate (and date) every time it is asked.
        // In "fixed" mode CurrencyService asks it on every lookup (no cache), so separate
        // lookups would give the total and each category different rates.
        ChangingRatesProvider provider = new ChangingRatesProvider();
        service = serviceWith(new CurrencyService(provider, provider, "fixed", 60));
        budget("EUR", "950.00", Map.of("Flights", "475.00", "Food", "190.00"));

        BudgetComparisonResponse result = compare();

        assertEquals(1, provider.calls, "rates are loaded once for the whole comparison");

        // Every amount uses the first snapshot: 1 USD = 0.95 EUR
        assertMoney("1000.00", result.plannedBudget());
        assertMoney("500.00", category(result, "Flights").allocated());
        assertMoney("200.00", category(result, "Food").allocated());
        assertMoney("700.00", result.totalAllocated());

        // Rate details come from that same snapshot
        assertMoney("1.052632", result.conversion().rate());   // 1 / 0.95, 6 decimals
        assertEquals(LocalDate.of(2026, 11, 20), result.conversion().rateDate());
        assertEquals("live", result.conversion().source());
        assertNull(result.conversion().notice());
    }

    @Test
    void unsupportedBudgetCurrencyIsNotCompared() {
        TripBudget budget = budget("MAD", "10000.00", Map.of("Food", "4000.00"));
        expense("Food", "120.00");
        expense("Shopping", "30.00");

        BudgetComparisonResponse result = compare();

        assertEquals("CURRENCY_UNSUPPORTED", result.status());
        assertFalse(result.comparable());
        assertEquals("MAD", result.budgetCurrency());
        assertTrue(result.message().contains("MAD"));

        // No made-up numbers: every budget amount is left out
        assertNull(result.plannedBudget());
        assertNull(result.remainingBudget());
        assertNull(result.overBudgetAmount());
        assertNull(result.percentSpent());
        assertNull(result.conversion());

        // Spending is still shown, in the trip's currency
        assertMoney("150.00", result.totalSpent());
        CategoryComparison food = category(result, "Food");
        assertTrue(food.budgeted());
        assertNull(food.allocated());
        assertEquals("NOT_COMPARABLE", food.status());
        assertMoney("120.00", food.spent());
        assertEquals("UNBUDGETED", category(result, "Shopping").status());

        assertMoney("10000.00", budget.getTotalAmount());
    }

    // ---------- Security ----------

    @Test
    void rejectsUsersWhoAreNotSignedIn() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.compareToBudget(tripId, null));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(expenseRepository, budgetRepository);
    }

    @Test
    void rejectsAnotherUsersTrip() {
        when(tripRepository.findByIdAndUserId(tripId, 99L)).thenReturn(Optional.empty());

        assertThrows(TripNotFoundException.class, () -> service.compareToBudget(tripId, 99L));
        verifyNoInteractions(expenseRepository, budgetRepository);
    }

    // ---------- Helpers ----------

    private BudgetComparisonResponse compare() {
        return service.compareToBudget(tripId, userId);
    }

    private ExpenseService serviceWith(CurrencyService currencyService) {
        return new ExpenseService(expenseRepository,
            new TripAccessService(tripRepository, mock(TripMemberRepository.class)), budgetRepository, currencyService);
    }

    private static CurrencyService fixedRates() {
        return new CurrencyService(new FixedExchangeRateProvider(), new FixedExchangeRateProvider(), "fixed", 60);
    }

    // Live-style rates: 1 USD = 0.95 EUR
    private static CurrencyService liveRates() {
        Map<SupportedCurrency, BigDecimal> rates = new EnumMap<>(SupportedCurrency.class);
        for (SupportedCurrency c : SupportedCurrency.values()) {
            rates.put(c, BigDecimal.ONE);
        }
        rates.put(SupportedCurrency.EUR, new BigDecimal("0.95"));
        ExchangeRates live = new ExchangeRates(rates, LocalDate.of(2026, 11, 20), ExchangeRates.LIVE);
        return new CurrencyService(() -> live, new FixedExchangeRateProvider(), "live", 60);
    }

    // Returns 1 USD = 0.95 EUR (dated 2026-11-20) the first time, then a new rate and date on every call
    private static class ChangingRatesProvider implements ExchangeRateProvider {
        int calls = 0;

        @Override
        public ExchangeRates fetchRates() {
            calls++;
            Map<SupportedCurrency, BigDecimal> rates = new EnumMap<>(SupportedCurrency.class);
            for (SupportedCurrency c : SupportedCurrency.values()) {
                rates.put(c, BigDecimal.ONE);
            }
            rates.put(SupportedCurrency.EUR, calls == 1 ? new BigDecimal("0.95") : new BigDecimal("0.50"));
            return new ExchangeRates(rates, LocalDate.of(2026, 11, 19 + calls), ExchangeRates.LIVE);
        }
    }

    private TripBudget budget(String currency, String total, Map<String, String> allocations) {
        TripBudget budget = new TripBudget(tripId);
        budget.setCurrency(currency);
        budget.setTotalAmount(new BigDecimal(total));
        allocations.forEach((category, amount) -> budget.getAllocations().put(category, new BigDecimal(amount)));
        when(budgetRepository.findByTripId(tripId)).thenReturn(Optional.of(budget));
        return budget;
    }

    private void expense(String category, String amount) {
        Expense expense = new Expense(trip);
        expense.setCategory(category);
        expense.setAmount(new BigDecimal(amount));
        expense.setExpenseDate(LocalDate.of(2027, 6, 2));
        expenses.add(expense);
    }

    private static CategoryComparison category(BudgetComparisonResponse result, String name) {
        return result.categories().stream()
            .filter(c -> c.category().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No category " + name + " in " + result.categories()));
    }

    // Compares the value only, so 0 equals 0.00
    private static void assertMoney(String expected, BigDecimal actual) {
        assertTrue(actual != null && new BigDecimal(expected).compareTo(actual) == 0,
            "expected " + expected + " but was " + actual);
    }
}
