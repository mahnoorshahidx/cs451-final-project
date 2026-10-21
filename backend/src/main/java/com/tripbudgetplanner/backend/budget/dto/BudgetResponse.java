package com.tripbudgetplanner.backend.budget.dto;

import java.math.BigDecimal;
import java.util.Map;

public record BudgetResponse(
    Long tripId,
    BigDecimal totalBudget,
    String currency,
    BigDecimal totalAllocated,
    Map<String, BigDecimal> allocations
) {}
