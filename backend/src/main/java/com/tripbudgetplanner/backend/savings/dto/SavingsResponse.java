package com.tripbudgetplanner.backend.savings.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// A trip's savings goal with progress and contribution history
public record SavingsResponse(
    Long tripId,
    BigDecimal targetAmount,
    BigDecimal initialSavedAmount,
    BigDecimal contributedAmount,
    BigDecimal savedAmount,        // initial + contributions
    BigDecimal remainingAmount,    // never negative
    int percentFunded,             // 0-100
    LocalDate targetDate,
    BigDecimal plannedMonthlyContribution,
    BigDecimal requiredMonthly,    // to reach the target by the target date
    BigDecimal requiredWeekly,
    String status,                 // FUNDED, ON_TRACK, BEHIND, NO_PLAN, PAST_DUE
    List<Contribution> contributions
) {
    public record Contribution(Long id, BigDecimal amount, LocalDate date, String description, Long bankAccountId) {}
}
