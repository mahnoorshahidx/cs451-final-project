package com.tripbudgetplanner.backend.savings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

// Estimates when a savings goal will be fully funded, based on how fast
// members have actually been saving recently (UC7).
//
// How it works:
//   1. Look at contributions dated in the last 90 days (up to today; future-dated ones are ignored).
//   2. Daily rate = their total / days since the first of them.
//   3. Projected date = today + (amount still needed / daily rate), rounded up to a whole day.
//
// It always uses ALL of the goal's contributions, never a filtered list,
// so filtering the history can't change the projection.
public final class SavingsProjection {

    public static final int WINDOW_DAYS = 90;
    public static final int MIN_CONTRIBUTIONS = 2;
    public static final int MIN_HISTORY_DAYS = 14;

    // Projections further away than this are not meaningful (and protect LocalDate from overflow)
    public static final long MAX_DAYS_AHEAD = 36_500;   // about 100 years

    // Average days per month, the same value the "required monthly" amount uses
    private static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30.44");

    // FUNDED               the goal is already met
    // ON_PACE              the projected date is on or before the target date
    // BEHIND_PACE          the projected date is after the target date
    // PROJECTED            a date could be projected, but the goal has no target date to compare with
    // INSUFFICIENT_HISTORY fewer than 2 recent contributions, or less than 14 days of history
    // NO_RECENT_SAVINGS    nothing saved in the last 90 days
    // RATE_TOO_LOW         saving so slowly the goal would take more than 100 years
    public static final String FUNDED = "FUNDED";
    public static final String ON_PACE = "ON_PACE";
    public static final String BEHIND_PACE = "BEHIND_PACE";
    public static final String PROJECTED = "PROJECTED";
    public static final String INSUFFICIENT_HISTORY = "INSUFFICIENT_HISTORY";
    public static final String NO_RECENT_SAVINGS = "NO_RECENT_SAVINGS";
    public static final String RATE_TOO_LOW = "RATE_TOO_LOW";

    // projectedCompletionDate is null unless status is ON_PACE, BEHIND_PACE or PROJECTED.
    // monthlyRate is the recent savings pace per month (null when there isn't enough history).
    public record Result(LocalDate projectedCompletionDate, BigDecimal monthlyRate, String status) {}

    private SavingsProjection() {}

    // remaining:     amount still needed (never negative)
    // contributions: all of the goal's contributions
    // targetDate:    may be null
    // decimals:      digits after the decimal point for the trip's currency (JPY 0, USD 2)
    public static Result project(BigDecimal remaining, List<SavingsContribution> contributions,
                                 LocalDate targetDate, LocalDate today, int decimals) {
        if (remaining == null || remaining.signum() <= 0) {
            return new Result(null, null, FUNDED);
        }

        LocalDate windowStart = today.minusDays(WINDOW_DAYS);
        List<SavingsContribution> recent = contributions.stream()
            .filter(c -> c.getTransactionDate() != null && c.getAmount() != null)
            .filter(c -> !c.getTransactionDate().isBefore(windowStart) && !c.getTransactionDate().isAfter(today))
            .toList();

        if (recent.isEmpty()) {
            // Saved before, but not lately: say so instead of "not enough history"
            boolean savedBefore = contributions.stream()
                .anyMatch(c -> c.getTransactionDate() != null && c.getTransactionDate().isBefore(windowStart));
            return savedBefore
                ? new Result(null, zero(decimals), NO_RECENT_SAVINGS)
                : new Result(null, null, INSUFFICIENT_HISTORY);
        }

        LocalDate first = recent.stream()
            .map(SavingsContribution::getTransactionDate)
            .min(Comparator.naturalOrder())
            .orElseThrow();
        long days = ChronoUnit.DAYS.between(first, today);   // at least 14 below, so never 0
        if (recent.size() < MIN_CONTRIBUTIONS || days < MIN_HISTORY_DAYS) {
            return new Result(null, null, INSUFFICIENT_HISTORY);
        }

        BigDecimal saved = recent.stream()
            .map(SavingsContribution::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (saved.signum() <= 0) {
            return new Result(null, zero(decimals), NO_RECENT_SAVINGS);
        }

        BigDecimal monthlyRate = saved.multiply(DAYS_PER_MONTH)
            .divide(BigDecimal.valueOf(days), decimals, RoundingMode.HALF_UP);

        // Days needed = remaining / (saved / days) = remaining * days / saved.
        // Calculated in one exact step (no rounded daily rate), then rounded UP to a whole day,
        // so a very small rate can never become a division by zero.
        BigDecimal daysNeeded = remaining.multiply(BigDecimal.valueOf(days))
            .divide(saved, 0, RoundingMode.CEILING);
        if (daysNeeded.compareTo(BigDecimal.valueOf(MAX_DAYS_AHEAD)) > 0) {
            return new Result(null, monthlyRate, RATE_TOO_LOW);
        }

        LocalDate projected = today.plusDays(daysNeeded.longValueExact());
        String status;
        if (targetDate == null) {
            status = PROJECTED;
        } else if (projected.isAfter(targetDate)) {
            status = BEHIND_PACE;
        } else {
            status = ON_PACE;
        }
        return new Result(projected, monthlyRate, status);
    }

    private static BigDecimal zero(int decimals) {
        return BigDecimal.ZERO.setScale(decimals);
    }
}
