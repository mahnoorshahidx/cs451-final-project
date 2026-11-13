package com.tripbudgetplanner.backend.expense.dto;

import java.math.BigDecimal;
import java.util.Map;

// Returns total trip spending and spending by category.
public record ExpenseSummaryResponse(
    Long tripId,
    BigDecimal totalSpent,
    int expenseCount,
    Map<String, BigDecimal> spentByCategory
) {}
