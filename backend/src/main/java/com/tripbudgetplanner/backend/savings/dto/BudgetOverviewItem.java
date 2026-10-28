package com.tripbudgetplanner.backend.savings.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// One row on the Budget page overview: a trip, its budget and its savings progress.
// Budget and savings fields are null when the trip doesn't have them yet.
public record BudgetOverviewItem(
    Long tripId,
    String name,
    String destination,
    LocalDate startDate,
    LocalDate endDate,
    int numberOfTravelers,
    BigDecimal budgetTotal,
    String currency,
    BigDecimal allocatedTotal,
    BigDecimal savingsTarget,
    BigDecimal savedAmount,
    Integer percentFunded,
    LocalDate savingsTargetDate,
    String savingsStatus
) {}
