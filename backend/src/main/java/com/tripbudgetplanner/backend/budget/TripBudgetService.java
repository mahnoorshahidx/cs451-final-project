package com.tripbudgetplanner.backend.budget;

import com.tripbudgetplanner.backend.budget.dto.BudgetChange;
import com.tripbudgetplanner.backend.budget.dto.BudgetRequest;
import com.tripbudgetplanner.backend.budget.exception.BudgetConflictException;
import com.tripbudgetplanner.backend.currency.CurrencyService;
import com.tripbudgetplanner.backend.currency.ExchangeRates;
import com.tripbudgetplanner.backend.currency.SupportedCurrency;
import com.tripbudgetplanner.backend.currency.dto.ConversionResponse;
import com.tripbudgetplanner.backend.currency.dto.RateInfo;
import com.tripbudgetplanner.backend.expense.ExpenseCategoryMapper;
import com.tripbudgetplanner.backend.trip.TripAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class TripBudgetService {

    // Same limit as the budget form: 10 digits and 2 decimals
    static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    private final TripBudgetRepository budgetRepository;
    private final TripAccessService tripAccess;
    private final CurrencyService currencyService;

    // Used by Spring
    @Autowired
    public TripBudgetService(TripBudgetRepository budgetRepository, TripAccessService tripAccess,
                             CurrencyService currencyService) {
        this.budgetRepository = budgetRepository;
        this.tripAccess = tripAccess;
        this.currencyService = currencyService;
    }

    // The original constructor, kept for existing tests (no currency conversion available)
    public TripBudgetService(TripBudgetRepository budgetRepository, TripAccessService tripAccess) {
        this(budgetRepository, tripAccess, null);
    }

    // Returns the trip's budget, or empty if none is saved yet.
    // Any trip member can view it; 404 if the user has no access to the trip.
    @Transactional(readOnly = true)
    public Optional<TripBudget> getBudget(Long tripId, Long userId) {
        tripAccess.viewableTrip(tripId, userId);
        return budgetRepository.findByTripId(tripId);
    }

    // Creates the budget, or replaces it if it already exists
    @Transactional
    public TripBudget saveBudget(Long tripId, BudgetRequest request, Long userId) {
        // Owner or editor (403 for viewers)
        tripAccess.editableTrip(tripId, userId);

        BigDecimal allocated = request.allocations().values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.compareTo(request.totalBudget()) > 0) {
            throw new IllegalArgumentException("Your allocations exceed your total budget");
        }

        // Wait for any other budget change on this trip to finish (no lost updates)
        budgetRepository.lockTrip(tripId);

        TripBudget budget = budgetRepository.findByTripId(tripId)
            .orElseGet(() -> new TripBudget(tripId));

        // If the page sent the version it loaded, refuse to overwrite a newer budget
        if (request.version() != null && budget.getId() != null && request.version() != budget.getVersion()) {
            throw new BudgetConflictException(
                "Someone else changed this budget since you opened it. Reload it and try again.");
        }

        budget.setTotalAmount(request.totalBudget());
        budget.setCurrency(request.currency());
        budget.getAllocations().clear();
        request.allocations().forEach((category, amount) -> {
            if (amount.signum() > 0) budget.getAllocations().put(category.trim(), amount);
        });
        budget.nextVersion();

        return budgetRepository.save(budget);
    }

    // UC6: adds an amount to one budget category (updateBudget=true).
    //  - amount is in amountCurrency (the trip's currency) and is converted to the budget's
    //    currency with the given rates, so the whole request uses one rate snapshot
    //  - an existing category with a matching name is reused ("flight" -> "Flights")
    //  - if the allocations would go over the total, the total grows to match
    //  - with no budget yet, one is created in amountCurrency
    //  - a budget in an unsupported currency (e.g. MAD) is refused with 409
    // Must run inside the caller's transaction, after the caller locked the trip.
    @Transactional
    public BudgetChange addToCategory(Long tripId, String standardCategory, BigDecimal amount,
                                      SupportedCurrency amountCurrency, ExchangeRates rates, Long userId) {
        tripAccess.editableTrip(tripId, userId);
        budgetRepository.lockTrip(tripId);

        Optional<TripBudget> existing = budgetRepository.findByTripId(tripId);
        boolean created = existing.isEmpty();
        TripBudget budget = existing.orElseGet(() -> {
            TripBudget fresh = new TripBudget(tripId);
            fresh.setCurrency(amountCurrency.name());
            fresh.setTotalAmount(BigDecimal.ZERO);
            return fresh;
        });

        if (!SupportedCurrency.isSupported(budget.getCurrency())) {
            throw new BudgetConflictException("Your budget is in " + budget.getCurrency()
                + ", which can't be converted, so this item wasn't added. Add it without updating the budget,"
                + " or save the budget in one of: " + String.join(", ", SupportedCurrency.codes()) + ".");
        }
        SupportedCurrency budgetCurrency = SupportedCurrency.fromCode(budget.getCurrency());

        // Convert into the budget's currency (same snapshot as the rest of the request)
        BigDecimal added = amount;
        RateInfo conversion = null;
        if (budgetCurrency != amountCurrency) {
            if (currencyService == null) {
                throw new IllegalStateException("Currency conversion is not available");
            }
            // Use the caller's snapshot; load one only if the caller didn't need rates itself
            ExchangeRates snapshot = rates != null ? rates : currencyService.currentRates();
            ConversionResponse converted = currencyService.convert(amount, amountCurrency, budgetCurrency, snapshot);
            added = converted.convertedAmount();
            conversion = RateInfo.of(converted);
        }

        // Reuse a matching category if the budget already has one ("Flights", "flights" or "flight")
        String category = ExpenseCategoryMapper.matchBudgetCategory(standardCategory, budget.getAllocations().keySet());
        if (category == null) {
            category = budget.getAllocations().keySet().stream()
                .filter(name -> standardCategory.equals(ExpenseCategoryMapper.standardName(name)))
                .findFirst()
                .orElse(standardCategory);
        }

        BigDecimal allocationBefore = budget.getAllocations().getOrDefault(category, BigDecimal.ZERO);
        BigDecimal allocationAfter = allocationBefore.add(added);
        BigDecimal totalBefore = budget.getTotalAmount();
        BigDecimal allocatedAfter = totalAllocated(budget).add(added);
        BigDecimal totalAfter = allocatedAfter.max(totalBefore);

        if (totalAfter.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("The budget would become too large");
        }

        budget.getAllocations().put(category, allocationAfter);
        budget.setTotalAmount(totalAfter);
        budget.nextVersion();
        budgetRepository.save(budget);

        return new BudgetChange(category, added, budgetCurrency.name(), allocationBefore, allocationAfter,
            created ? BigDecimal.ZERO : totalBefore, totalAfter, created, budget.getVersion(), conversion);
    }

    // UC6: takes back what a search item added, when the user deletes that item and asks for it.
    // The category's allocation goes down by the amount (never below zero) and the total goes down
    // with it, but never below what is still allocated. Returns the amount taken back (zero when
    // there's no budget, no matching category, or the amount was in another currency).
    @Transactional
    public BigDecimal takeBackFromCategory(Long tripId, String standardCategory, BigDecimal amount, String amountCurrency) {
        if (amount == null || amount.signum() <= 0 || standardCategory == null) {
            return BigDecimal.ZERO;
        }
        budgetRepository.lockTrip(tripId);
        TripBudget budget = budgetRepository.findByTripId(tripId).orElse(null);
        if (budget == null || (amountCurrency != null && !amountCurrency.equals(budget.getCurrency()))) {
            return BigDecimal.ZERO;
        }

        String category = ExpenseCategoryMapper.matchBudgetCategory(standardCategory, budget.getAllocations().keySet());
        if (category == null) {
            category = budget.getAllocations().keySet().stream()
                .filter(name -> standardCategory.equals(ExpenseCategoryMapper.standardName(name)))
                .findFirst()
                .orElse(null);
        }
        if (category == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal before = budget.getAllocations().getOrDefault(category, BigDecimal.ZERO);
        BigDecimal after = before.subtract(amount).max(BigDecimal.ZERO);
        BigDecimal removed = before.subtract(after);
        if (removed.signum() == 0) {
            return BigDecimal.ZERO;
        }

        budget.getAllocations().put(category, after);
        budget.setTotalAmount(budget.getTotalAmount().subtract(removed).max(totalAllocated(budget)));
        budget.nextVersion();
        budgetRepository.save(budget);
        return removed;
    }

    public static BigDecimal totalAllocated(TripBudget budget) {
        return budget.getAllocations().values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
