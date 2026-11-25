package com.tripbudgetplanner.backend.savings;

import static com.tripbudgetplanner.backend.savings.SavingsProjection.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

// UC7: projected completion date from the recent savings pace
class SavingsProjectionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final LocalDate FAR_TARGET = LocalDate.of(2030, 1, 1);

    // ---------- Funded or not enough history ----------

    @Test
    void aFundedGoalNeedsNoProjection() {
        Result r = project("0.00", List.of(saved("100.00", 30), saved("100.00", 15)), FAR_TARGET, 2);

        assertEquals(FUNDED, r.status());
        assertNull(r.projectedCompletionDate());
        assertEquals(FUNDED, project(null, List.of(), FAR_TARGET, 2).status());
    }

    @Test
    void noContributionsMeansNotEnoughHistory() {
        Result r = project("500.00", List.of(), FAR_TARGET, 2);

        assertEquals(INSUFFICIENT_HISTORY, r.status());
        assertNull(r.projectedCompletionDate());
        assertNull(r.monthlyRate());
    }

    @Test
    void oneContributionIsNotEnough() {
        assertEquals(INSUFFICIENT_HISTORY, project("500.00", List.of(saved("100.00", 60)), FAR_TARGET, 2).status());
    }

    @Test
    void lessThan14DaysOfHistoryIsNotEnough() {
        Result r = project("500.00", List.of(saved("100.00", 13), saved("100.00", 1)), FAR_TARGET, 2);

        assertEquals(INSUFFICIENT_HISTORY, r.status());
        assertNull(r.projectedCompletionDate());
    }

    @Test
    void exactly14DaysOfHistoryIsEnough() {
        // 200 over 14 days; 500 left -> ceil(500 * 14 / 200) = 35 days
        Result r = project("500.00", List.of(saved("100.00", 14), saved("100.00", 0)), FAR_TARGET, 2);

        assertEquals(ON_PACE, r.status());
        assertEquals(TODAY.plusDays(35), r.projectedCompletionDate());
    }

    @Test
    void onlyOldContributionsMeansNoRecentSavings() {
        Result r = project("500.00", List.of(saved("100.00", 200), saved("100.00", 120)), FAR_TARGET, 2);

        assertEquals(NO_RECENT_SAVINGS, r.status());
        assertNull(r.projectedCompletionDate());
        assertEquals(new BigDecimal("0.00"), r.monthlyRate());
    }

    // ---------- The projected date ----------

    @Test
    void projectsFromTheRecentDailyRate() {
        // 200 saved over 30 days; 1000 left -> 1000 * 30 / 200 = 150 days
        Result r = project("1000.00", List.of(saved("100.00", 30), saved("100.00", 15)), FAR_TARGET, 2);

        assertEquals(TODAY.plusDays(150), r.projectedCompletionDate());
        // 200 * 30.44 / 30 = 202.9333 -> 202.93 per month
        assertEquals(new BigDecimal("202.93"), r.monthlyRate());
    }

    @Test
    void partialDaysAlwaysRoundUp() {
        // 1001 * 30 / 200 = 150.15 -> 151 days (never promise a day too early)
        Result r = project("1001.00", List.of(saved("100.00", 30), saved("100.00", 15)), FAR_TARGET, 2);

        assertEquals(TODAY.plusDays(151), r.projectedCompletionDate());
    }

    @Test
    void onPaceWhenTheProjectedDateIsOnOrBeforeTheTargetDate() {
        List<SavingsContribution> history = List.of(saved("100.00", 30), saved("100.00", 15));

        assertEquals(ON_PACE, project("1000.00", history, TODAY.plusDays(150), 2).status());   // same day
        assertEquals(ON_PACE, project("1000.00", history, TODAY.plusDays(200), 2).status());
        assertEquals(BEHIND_PACE, project("1000.00", history, TODAY.plusDays(149), 2).status());
    }

    @Test
    void aTargetDateThatHasPassedIsBehindPace() {
        Result r = project("1000.00", List.of(saved("100.00", 30), saved("100.00", 15)), TODAY.minusDays(5), 2);

        assertEquals(BEHIND_PACE, r.status());
        assertEquals(TODAY.plusDays(150), r.projectedCompletionDate());
    }

    @Test
    void withoutATargetDateTheDateIsProjectedButNotLabelledOnOrBehindPace() {
        Result r = project("1000.00", List.of(saved("100.00", 30), saved("100.00", 15)), null, 2);

        assertEquals(PROJECTED, r.status());
        assertEquals(TODAY.plusDays(150), r.projectedCompletionDate());
    }

    // ---------- Which contributions count ----------

    @Test
    void contributionsOlderThan90DaysAreIgnored() {
        // The 5000 from 91 days ago doesn't count; 90 days ago is the first day that does
        List<SavingsContribution> history = List.of(
            saved("5000.00", 91), saved("100.00", 90), saved("80.00", 30));

        Result r = project("540.00", history, FAR_TARGET, 2);

        // 180 over 90 days = 2 per day; 540 left -> 270 days
        assertEquals(TODAY.plusDays(270), r.projectedCompletionDate());
    }

    @Test
    void futureDatedContributionsDontCountTowardsThePace() {
        List<SavingsContribution> history = new ArrayList<>(List.of(saved("100.00", 30), saved("100.00", 15)));
        history.add(saved("9999.00", -10));   // dated 10 days from now

        assertEquals(TODAY.plusDays(150), project("1000.00", history, FAR_TARGET, 2).projectedCompletionDate());
    }

    @Test
    void incompleteRowsAreSkipped() {
        List<SavingsContribution> history = new ArrayList<>(List.of(saved("100.00", 30), saved("100.00", 15)));
        history.add(new SavingsContribution(1L, null, null, TODAY.minusDays(3), null, null));
        history.add(new SavingsContribution(1L, null, new BigDecimal("50.00"), null, null, null));

        assertEquals(TODAY.plusDays(150), project("1000.00", history, FAR_TARGET, 2).projectedCompletionDate());
    }

    // ---------- Very slow savings, overflow and rounding ----------

    @Test
    void aVerySmallRateNeverDividesByZeroOrOverflows() {
        // 0.02 over 89 days, with almost ten billion still needed
        Result r = project("9999999999.99", List.of(saved("0.01", 89), saved("0.01", 1)), FAR_TARGET, 2);

        assertEquals(RATE_TOO_LOW, r.status());
        assertNull(r.projectedCompletionDate());
        assertEquals(new BigDecimal("0.01"), r.monthlyRate());
    }

    @Test
    void justUnder100YearsIsStillProjected() {
        // 1 per day for 20 days; 36,500 left -> exactly MAX_DAYS_AHEAD
        Result r = project("36500.00", List.of(saved("10.00", 20), saved("10.00", 10)), null, 2);
        assertEquals(TODAY.plusDays(MAX_DAYS_AHEAD), r.projectedCompletionDate());

        assertEquals(RATE_TOO_LOW, project("36500.01", List.of(saved("10.00", 20), saved("10.00", 10)), null, 2).status());
    }

    @Test
    void theMonthlyRateUsesTheCurrencysDecimals() {
        List<SavingsContribution> history = List.of(saved("100.00", 30), saved("100.00", 15));

        assertEquals(new BigDecimal("203"), project("1000.00", history, FAR_TARGET, 0).monthlyRate());   // JPY, KRW
        assertEquals(new BigDecimal("202.93"), project("1000.00", history, FAR_TARGET, 2).monthlyRate());
    }

    @Test
    void aTinyRemainingAmountNeedsOneDay() {
        Result r = project("0.01", List.of(saved("100.00", 30), saved("100.00", 15)), FAR_TARGET, 2);

        assertEquals(TODAY.plusDays(1), r.projectedCompletionDate());
    }

    // ---------- Helpers ----------

    private static Result project(String remaining, List<SavingsContribution> history, LocalDate target, int decimals) {
        return SavingsProjection.project(remaining == null ? null : new BigDecimal(remaining), history, target, TODAY, decimals);
    }

    // A contribution made the given number of days before today (negative = in the future)
    private static SavingsContribution saved(String amount, int daysAgo) {
        return new SavingsContribution(1L, null, new BigDecimal(amount), TODAY.minusDays(daysAgo), null, 1L);
    }
}
