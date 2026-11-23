package com.tripbudgetplanner.backend.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// UC11: actual expenses compared with the planned trip budget.
//
// All amounts are in "currency" (the trip's currency, which expenses are recorded in).
// If the budget was saved in a different supported currency, its amounts are converted for
// this response only (nothing is saved) and "conversion" says which rate was used.
//
// status:
//   UNDER_BUDGET, ON_BUDGET, OVER_BUDGET  - normal comparison
//   NO_BUDGET             - the trip has no budget yet; only spending is shown
//   CURRENCY_UNSUPPORTED  - the budget's currency (e.g. MAD) can't be converted, so budget
//                           amounts are left out (null) instead of showing wrong numbers
// comparable: true only for the first three statuses.
// Budget fields (plannedBudget, remainingBudget, ...) are null when comparable is false.
// percentSpent is null when there is nothing to divide by (a zero budget).
public record BudgetComparisonResponse(
    Long tripId,
    String currency,
    String status,
    boolean comparable,
    String message,
    BigDecimal plannedBudget,
    BigDecimal totalAllocated,
    BigDecimal unallocatedBudget,
    BigDecimal totalSpent,
    BigDecimal remainingBudget,
    BigDecimal overBudgetAmount,
    BigDecimal percentSpent,
    BigDecimal unbudgetedSpent,
    int expenseCount,
    String budgetCurrency,
    Conversion conversion,
    List<CategoryComparison> categories
) {

    // How the budget was converted into the trip's currency (null when no conversion was needed)
    public record Conversion(
        String from,
        String to,
        BigDecimal rate,
        LocalDate rateDate,
        String source,      // "live" or "fallback"
        String notice       // warning when demo rates were used, otherwise null
    ) {}

    // One budget category, or one group of spending with no budget category.
    // status: UNDER_BUDGET, ON_BUDGET, OVER_BUDGET, UNBUDGETED (spending with no allocation)
    //         or NOT_COMPARABLE (budget amounts unavailable).
    // expenseCategories: the names typed on the expenses counted here, e.g. ["Hotel", "hotel"].
    public record CategoryComparison(
        String category,
        boolean budgeted,
        BigDecimal allocated,
        BigDecimal spent,
        BigDecimal remaining,
        BigDecimal overBudgetAmount,
        BigDecimal percentSpent,
        String status,
        List<String> expenseCategories
    ) {}
}
