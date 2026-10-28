package com.tripbudgetplanner.backend.recommendation.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// allocations add up to less than recommendedTotal: the difference is a buffer
public record BudgetRecommendation(
    BigDecimal recommendedTotal,
    BigDecimal lowTotal,
    BigDecimal highTotal,
    String currency,
    Map<String, BigDecimal> allocations,
    List<String> assumptions,
    String source
) {}
