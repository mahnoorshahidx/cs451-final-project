package com.tripbudgetplanner.backend.expense;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tripbudgetplanner.backend.budget.TripBudget;
import com.tripbudgetplanner.backend.budget.TripBudgetRepository;
import com.tripbudgetplanner.backend.currency.CurrencyService;
import com.tripbudgetplanner.backend.currency.ExchangeRates;
import com.tripbudgetplanner.backend.currency.SupportedCurrency;
import com.tripbudgetplanner.backend.currency.dto.ConversionResponse;
import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.trip.TripAccessService;
import com.tripbudgetplanner.backend.expense.dto.*;
import com.tripbudgetplanner.backend.expense.dto.BudgetComparisonResponse.CategoryComparison;
import com.tripbudgetplanner.backend.expense.dto.BudgetComparisonResponse.Conversion;

@Service
public class ExpenseService {

    // Comparison statuses (see BudgetComparisonResponse)
    static final String UNDER_BUDGET = "UNDER_BUDGET";
    static final String ON_BUDGET = "ON_BUDGET";
    static final String OVER_BUDGET = "OVER_BUDGET";
    static final String NO_BUDGET = "NO_BUDGET";
    static final String CURRENCY_UNSUPPORTED = "CURRENCY_UNSUPPORTED";
    static final String UNBUDGETED = "UNBUDGETED";
    static final String NOT_COMPARABLE = "NOT_COMPARABLE";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ExpenseRepository expenses;
    private final TripAccessService tripAccess;
    private final TripBudgetRepository budgets;
    private final CurrencyService currencyService;

    public ExpenseService(ExpenseRepository expenses, TripAccessService tripAccess,
                          TripBudgetRepository budgets, CurrencyService currencyService) {
        this.expenses = expenses;
        this.tripAccess = tripAccess;
        this.budgets = budgets;
        this.currencyService = currencyService;
    }

    // Any trip member can read expenses (401 if not signed in, 404 without access).
    private Trip viewableTrip(Long tripId, Long userId) {
        return tripAccess.viewableTrip(tripId, userId);
    }

    // Only the owner or an editor can change expenses (403 for viewers).
    private Trip editableTrip(Long tripId, Long userId) {
        return tripAccess.editableTrip(tripId, userId);
    }

    // Find an expense that belongs to the trip.
    private Expense ownedExpense(Long tripId, Long expenseId) {
        return expenses.findByIdAndTripId(expenseId, tripId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
    }

    // Convert an expense into an API response.
    private ExpenseResponse response(Expense expense) {
        return new ExpenseResponse(expense.getId(), expense.getTrip().getId(),
            expense.getCategory(), expense.getAmount(), expense.getExpenseDate(), expense.getDescription());
    }

    // Apply the expense details from the request.
    private void apply(Expense expense, ExpenseRequest request) {
        expense.setCategory(request.category().trim());
        expense.setAmount(request.amount());
        expense.setExpenseDate(request.expenseDate());
        expense.setDescription(request.description() == null || request.description().isBlank()
            ? null : request.description().trim());
    }

    // Get all expenses for a trip.
    @Transactional(readOnly = true)
    public List<ExpenseResponse> list(Long tripId, Long userId) {
        viewableTrip(tripId, userId);

        return expenses.findByTripIdOrderByExpenseDateDescIdDesc(tripId)
            .stream().map(this::response).toList();
    }

    // Get one expense by ID.
    @Transactional(readOnly = true)
    public ExpenseResponse get(Long tripId, Long expenseId, Long userId) {
        viewableTrip(tripId, userId);
        return response(ownedExpense(tripId, expenseId));
    }

    // Create and save a new expense.
    @Transactional
    public ExpenseResponse create(Long tripId, ExpenseRequest request, Long userId) {
        Trip trip = editableTrip(tripId, userId);
        Expense expense = new Expense(trip);

        apply(expense, request);
        return response(expenses.save(expense));
    }

    // Update an existing expense.
    @Transactional
    public ExpenseResponse update(Long tripId, Long expenseId, ExpenseRequest request, Long userId) {
        editableTrip(tripId, userId);
        Expense expense = ownedExpense(tripId, expenseId);

        apply(expense, request);
        return response(expenses.save(expense));
    }

    // Delete an expense from the trip.
    @Transactional
    public void delete(Long tripId, Long expenseId, Long userId) {
        editableTrip(tripId, userId);
        expenses.delete(ownedExpense(tripId, expenseId));
    }

    // Calculate total spending and spending by category.
    @Transactional(readOnly = true)
    public ExpenseSummaryResponse summary(Long tripId, Long userId) {
        viewableTrip(tripId, userId);

        List<Expense> items = expenses.findByTripIdOrderByExpenseDateDescIdDesc(tripId);
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Expense item : items) {
            total = total.add(item.getAmount());
            totals.merge(item.getCategory(), item.getAmount(), BigDecimal::add);
        }

        return new ExpenseSummaryResponse(tripId, total, items.size(), totals);
    }

    // ---------- UC11: compare actual spending with the planned budget ----------

    // Expenses are recorded in the trip's currency. If the budget was saved in another
    // supported currency, its amounts are converted to the trip's currency for this
    // response only. Nothing stored in the database is changed.
    @Transactional(readOnly = true)
    public BudgetComparisonResponse compareToBudget(Long tripId, Long userId) {
        Trip trip = viewableTrip(tripId, userId);
        SupportedCurrency tripCurrency = SupportedCurrency.fromCode(trip.getCurrency());

        List<Expense> items = expenses.findByTripIdOrderByExpenseDateDescIdDesc(tripId);
        BigDecimal totalSpent = items.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Optional<TripBudget> savedBudget = budgets.findByTripId(tripId);

        // No budget yet: show the spending, grouped by category, with nothing to compare against
        if (savedBudget.isEmpty()) {
            List<Group> groups = groupExpenses(items, List.of());
            return notComparable(tripId, tripCurrency, NO_BUDGET,
                "This trip has no budget yet. Create a budget to compare your spending.",
                totalSpent, items.size(), null, groups);
        }

        TripBudget budget = savedBudget.get();

        // Budget in a currency we can't convert (e.g. MAD from the Edit Trip page):
        // leave the budget amounts out rather than compare different currencies.
        if (!SupportedCurrency.isSupported(budget.getCurrency())) {
            List<Group> groups = groupExpenses(items, budget.getAllocations().keySet());
            return notComparable(tripId, tripCurrency, CURRENCY_UNSUPPORTED,
                "Your budget is in " + budget.getCurrency() + ", which can't be converted to "
                    + tripCurrency + ". Save the budget in one of these currencies to compare spending: "
                    + String.join(", ", SupportedCurrency.codes()) + ".",
                totalSpent, items.size(), budget.getCurrency(), groups);
        }

        // Copy the budget amounts in the trip's currency (converting if needed)
        SupportedCurrency budgetCurrency = SupportedCurrency.fromCode(budget.getCurrency());
        BigDecimal planned;
        Map<String, BigDecimal> allocations = new LinkedHashMap<>();
        Conversion conversion = null;

        if (budgetCurrency == tripCurrency) {
            planned = budget.getTotalAmount();
            allocations.putAll(budget.getAllocations());
        } else {
            // Load the rates once, so the total and every category use the same rate,
            // source and date (the cache could refresh between separate lookups)
            ExchangeRates rates = currencyService.currentRates();

            ConversionResponse total = currencyService.convert(budget.getTotalAmount(), budgetCurrency, tripCurrency, rates);
            planned = total.convertedAmount();
            conversion = new Conversion(total.from(), total.to(), total.rate(),
                total.rateDate(), total.source(), total.notice());
            budget.getAllocations().forEach((category, amount) -> allocations.put(category,
                currencyService.convert(amount, budgetCurrency, tripCurrency, rates).convertedAmount()));
        }

        return compare(tripId, tripCurrency, budgetCurrency.name(), conversion,
            planned, allocations, items, totalSpent);
    }

    // Builds the full comparison once all budget amounts are in the trip's currency
    private BudgetComparisonResponse compare(Long tripId, SupportedCurrency currency, String budgetCurrency,
                                             Conversion conversion, BigDecimal planned,
                                             Map<String, BigDecimal> allocations,
                                             List<Expense> items, BigDecimal totalSpent) {
        List<CategoryComparison> categories = new ArrayList<>();
        BigDecimal unbudgetedSpent = BigDecimal.ZERO;

        for (Group group : groupExpenses(items, allocations.keySet())) {
            if (group.budgeted) {
                BigDecimal allocated = allocations.get(group.name);
                categories.add(new CategoryComparison(group.name, true, allocated, group.spent,
                    remaining(allocated, group.spent), overBy(allocated, group.spent),
                    percent(group.spent, allocated), status(group.spent, allocated), group.expenseNames()));
            } else {
                // Spending with no budget category: nothing was allocated, so all of it is "over"
                unbudgetedSpent = unbudgetedSpent.add(group.spent);
                categories.add(new CategoryComparison(group.name, false, BigDecimal.ZERO, group.spent,
                    BigDecimal.ZERO, group.spent, null, UNBUDGETED, group.expenseNames()));
            }
        }

        BigDecimal totalAllocated = allocations.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = remaining(planned, totalSpent);
        BigDecimal over = overBy(planned, totalSpent);
        String status = status(totalSpent, planned);

        String message = switch (status) {
            case OVER_BUDGET -> "You are over budget by " + over.toPlainString() + " " + currency + ".";
            case ON_BUDGET -> "You have spent exactly your budget.";
            default -> "You have " + remaining.toPlainString() + " " + currency + " left in your budget.";
        };

        return new BudgetComparisonResponse(
            tripId,
            currency.name(),
            status,
            true,
            message,
            planned,
            totalAllocated,
            // Converting each category separately can round a cent above the total, so never go below 0
            planned.subtract(totalAllocated).max(BigDecimal.ZERO),
            totalSpent,
            remaining,
            over,
            percent(totalSpent, planned),
            unbudgetedSpent,
            items.size(),
            budgetCurrency,
            conversion,
            categories);
    }

    // Response for NO_BUDGET and CURRENCY_UNSUPPORTED: spending only, budget fields null
    private BudgetComparisonResponse notComparable(Long tripId, SupportedCurrency currency, String status,
                                                   String message, BigDecimal totalSpent, int expenseCount,
                                                   String budgetCurrency, List<Group> groups) {
        List<CategoryComparison> categories = new ArrayList<>();
        BigDecimal unbudgetedSpent = BigDecimal.ZERO;

        for (Group group : groups) {
            if (!group.budgeted) {
                unbudgetedSpent = unbudgetedSpent.add(group.spent);
            }
            categories.add(new CategoryComparison(group.name, group.budgeted, null, group.spent,
                null, null, null, group.budgeted ? NOT_COMPARABLE : UNBUDGETED, group.expenseNames()));
        }

        return new BudgetComparisonResponse(tripId, currency.name(), status, false, message,
            null, null, null, totalSpent, null, null, null, unbudgetedSpent, expenseCount,
            budgetCurrency, null, categories);
    }

    // ---------- Grouping expenses by budget category ----------

    // Spending for one budget category, or for one category that isn't in the budget
    private static final class Group {
        final String name;
        final boolean budgeted;
        BigDecimal spent = BigDecimal.ZERO;
        final TreeSet<String> names = new TreeSet<>();   // expense category names counted here

        Group(String name, boolean budgeted) {
            this.name = name;
            this.budgeted = budgeted;
        }

        List<String> expenseNames() {
            return List.copyOf(names);
        }
    }

    // One group per budget category (even with no spending), then one per unbudgeted category.
    // Budget categories come first in the standard order, then custom ones A-Z, then unbudgeted A-Z.
    private static List<Group> groupExpenses(List<Expense> items, Collection<String> budgetCategories) {
        Map<String, Group> budgeted = new LinkedHashMap<>();
        budgetCategories.stream()
            .sorted(categoryOrder())
            .forEach(category -> budgeted.put(category, new Group(category, true)));

        // Keyed by lowercase name so "shopping" and "Shopping" are one group
        Map<String, Group> unbudgeted = new LinkedHashMap<>();

        for (Expense item : items) {
            String match = ExpenseCategoryMapper.matchBudgetCategory(item.getCategory(), budgetCategories);
            Group group;
            if (match != null) {
                group = budgeted.get(match);
            } else {
                String name = ExpenseCategoryMapper.displayName(item.getCategory());
                group = unbudgeted.computeIfAbsent(ExpenseCategoryMapper.normalize(name), key -> new Group(name, false));
            }
            group.spent = group.spent.add(item.getAmount());
            group.names.add(item.getCategory().trim());
        }

        List<Group> result = new ArrayList<>(budgeted.values());
        unbudgeted.values().stream()
            .sorted(Comparator.comparing(g -> g.name.toLowerCase(Locale.ROOT)))
            .forEach(result::add);
        return result;
    }

    // Standard categories in their usual order, then everything else alphabetically
    private static Comparator<String> categoryOrder() {
        return Comparator
            .comparingInt((String c) -> {
                int index = ExpenseCategoryMapper.STANDARD_CATEGORIES.indexOf(c);
                return index < 0 ? Integer.MAX_VALUE : index;
            })
            .thenComparing(c -> c.toLowerCase(Locale.ROOT));
    }

    // ---------- Small money helpers ----------

    // Money left, never negative
    private static BigDecimal remaining(BigDecimal planned, BigDecimal spent) {
        return planned.subtract(spent).max(BigDecimal.ZERO);
    }

    // Money spent above the plan, never negative
    private static BigDecimal overBy(BigDecimal planned, BigDecimal spent) {
        return spent.subtract(planned).max(BigDecimal.ZERO);
    }

    // Percent of the plan that was spent, e.g. 87.5. Null for a zero plan (can't divide by 0).
    private static BigDecimal percent(BigDecimal spent, BigDecimal planned) {
        if (planned == null || planned.signum() == 0) {
            return null;
        }
        return spent.multiply(HUNDRED).divide(planned, 1, RoundingMode.HALF_UP);
    }

    // UNDER_BUDGET, ON_BUDGET or OVER_BUDGET. compareTo ignores scale, so 100 equals 100.00.
    private static String status(BigDecimal spent, BigDecimal planned) {
        int result = spent.compareTo(planned);
        return result < 0 ? UNDER_BUDGET : result == 0 ? ON_BUDGET : OVER_BUDGET;
    }
}
