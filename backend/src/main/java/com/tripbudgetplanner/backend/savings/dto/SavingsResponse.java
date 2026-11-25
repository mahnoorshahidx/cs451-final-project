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
    List<Contribution> contributions,

    // UC7: when the goal will be funded at the recent savings pace (see SavingsProjection).
    // The date is null unless projectionStatus is ON_PACE, BEHIND_PACE or PROJECTED.
    LocalDate projectedCompletionDate,
    BigDecimal projectedMonthlyRate,  // recent savings per month; null without enough history
    String projectionStatus           // FUNDED, ON_PACE, BEHIND_PACE, PROJECTED,
                                      // INSUFFICIENT_HISTORY, NO_RECENT_SAVINGS, RATE_TOO_LOW
) {
    // The fields from before UC7 (no projection), kept so older code still compiles
    public SavingsResponse(Long tripId, BigDecimal targetAmount, BigDecimal initialSavedAmount,
                           BigDecimal contributedAmount, BigDecimal savedAmount, BigDecimal remainingAmount,
                           int percentFunded, LocalDate targetDate, BigDecimal plannedMonthlyContribution,
                           BigDecimal requiredMonthly, BigDecimal requiredWeekly, String status,
                           List<Contribution> contributions) {
        this(tripId, targetAmount, initialSavedAmount, contributedAmount, savedAmount, remainingAmount,
            percentFunded, targetDate, plannedMonthlyContribution, requiredMonthly, requiredWeekly, status,
            contributions, null, null, null);
    }

    // bankAccountId is only shown to the person who owns that bank account; others get null.
    // addedByYou / canDelete tell the page whether to show a delete button (UC9).
    public record Contribution(Long id, BigDecimal amount, LocalDate date, String description, Long bankAccountId,
                               boolean addedByYou, boolean canDelete) {}
}
