package com.tripbudgetplanner.backend.savings.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// A filtered view of a trip's savings contributions (UC7).
//
// The filters only choose which contributions are listed. Everything under "goal" is
// for the whole goal and is exactly what GET /api/trips/{tripId}/savings returns,
// whatever filters are used.
public record ContributionHistoryResponse(
    Long tripId,

    // The filters that were applied (null = not used)
    LocalDate from,
    LocalDate to,
    Long bankAccountId,

    // Only the listed contributions
    int filteredCount,
    BigDecimal filteredTotal,
    List<SavingsResponse.Contribution> contributions,

    // The whole goal, never changed by the filters (null if the trip has no goal yet)
    BigDecimal goalTargetAmount,
    BigDecimal goalSavedAmount,
    BigDecimal goalRemainingAmount,
    Integer goalPercentFunded,
    String goalStatus,
    LocalDate projectedCompletionDate,
    String projectionStatus,

    // Set when nothing is listed, e.g. "No contributions match these filters"
    String message
) {}
