package com.tripbudgetplanner.backend.savings;

import static com.tripbudgetplanner.backend.member.SharedTripFixture.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.tripbudgetplanner.backend.bank.BankAccountRepository;
import com.tripbudgetplanner.backend.budget.TripBudgetRepository;
import com.tripbudgetplanner.backend.expense.ExpenseRepository;
import com.tripbudgetplanner.backend.member.SharedTripFixture;
import com.tripbudgetplanner.backend.member.TripInvitationRepository;
import com.tripbudgetplanner.backend.savings.dto.ContributionRequest;
import com.tripbudgetplanner.backend.savings.dto.SavingsGoalRequest;
import com.tripbudgetplanner.backend.savings.dto.SavingsResponse;
import com.tripbudgetplanner.backend.trip.TripService;
import com.tripbudgetplanner.backend.trip.exception.TripAccessDeniedException;
import com.tripbudgetplanner.backend.user.UserRepository;

import jakarta.validation.constraints.NotNull;

// UC8: a savings goal's target date can't be set to a past date,
// but a date that has already passed can stay when other fields are updated.
class SavingsTargetDateTest {

    private static final Long GOAL_ID = 100L;
    private static final String PAST_MESSAGE = "Target date can't be in the past. Choose today or a later date.";

    // Today is 25 Nov 2026 on the server
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T18:00:00Z"), ZoneId.of("America/Chicago"));

    private SharedTripFixture f;
    private SavingsGoalRepository goals;
    private SavingsContributionRepository contributions;
    private SavingsService service;
    private final List<SavingsGoal> saved = new ArrayList<>();
    private final List<SavingsContribution> added = new ArrayList<>();

    @BeforeEach
    void setUp() {
        f = new SharedTripFixture();
        goals = mock(SavingsGoalRepository.class);
        contributions = mock(SavingsContributionRepository.class);
        BankAccountRepository bankAccounts = mock(BankAccountRepository.class);
        TripBudgetRepository budgets = mock(TripBudgetRepository.class);
        TripService tripService = new TripService(f.tripRepository, mock(UserRepository.class), goals, contributions,
            budgets, mock(ExpenseRepository.class), f.access, f.memberRepository, mock(TripInvitationRepository.class));
        service = new SavingsService(goals, contributions, tripService, budgets, bankAccounts, f.access, CLOCK);

        when(goals.save(any())).thenAnswer(call -> {
            saved.add(call.getArgument(0));
            return call.getArgument(0);
        });
        when(contributions.sumByGoalId(any())).thenReturn(BigDecimal.ZERO);
        when(contributions.save(any())).thenAnswer(call -> {
            added.add(call.getArgument(0));
            return call.getArgument(0);
        });
    }

    // ---------- A new goal ----------

    @Test
    void aNewGoalCantStartWithAPastDate() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.saveGoal(TRIP_ID, request("2000.00", TODAY.minusDays(1)), OWNER_ID));

        assertEquals(PAST_MESSAGE, e.getMessage());
        verify(goals, never()).save(any());
    }

    @Test
    void aNewGoalCanUseTodayOrALaterDate() {
        assertEquals(TODAY, service.saveGoal(TRIP_ID, request("2000.00", TODAY), OWNER_ID).targetDate());
        assertEquals(TODAY.plusMonths(6),
            service.saveGoal(TRIP_ID, request("2000.00", TODAY.plusMonths(6)), OWNER_ID).targetDate());
    }

    // ---------- An existing goal ----------

    @Test
    void aGoalWhoseDateAlreadyPassedCanStillBeUpdated() {
        SavingsGoal goal = existingGoal(LocalDate.of(2026, 10, 1));

        // Same (past) date sent back with a new amount and monthly plan
        SavingsResponse r = service.saveGoal(TRIP_ID,
            new SavingsGoalRequest(new BigDecimal("2500.00"), new BigDecimal("100.00"), LocalDate.of(2026, 10, 1),
                new BigDecimal("250.00")), OWNER_ID);

        assertEquals(LocalDate.of(2026, 10, 1), r.targetDate());
        assertEquals(0, new BigDecimal("2500.00").compareTo(goal.getTargetAmount()));
        assertEquals(0, new BigDecimal("250.00").compareTo(goal.getPlannedMonthlyContribution()));
        assertEquals("PAST_DUE", r.status(), "the status still says the date has passed");
        assertEquals(1, saved.size());
    }

    @Test
    void anEditorCanAlsoUpdateAGoalWhoseDateAlreadyPassed() {
        existingGoal(LocalDate.of(2026, 1, 15));

        service.saveGoal(TRIP_ID, request("900.00", LocalDate.of(2026, 1, 15)), EDITOR_ID);

        assertEquals(1, saved.size());
    }

    @Test
    void changingTheDateToAnotherPastDateIs400() {
        existingGoal(LocalDate.of(2026, 10, 1));

        assertEquals(PAST_MESSAGE, assertThrows(IllegalArgumentException.class,
            () -> service.saveGoal(TRIP_ID, request("2000.00", LocalDate.of(2026, 9, 1)), OWNER_ID)).getMessage());
        verify(goals, never()).save(any());
    }

    @Test
    void changingAFutureDateToAPastDateIs400AndChangesNothing() {
        SavingsGoal goal = existingGoal(LocalDate.of(2027, 5, 1));

        assertThrows(IllegalArgumentException.class,
            () -> service.saveGoal(TRIP_ID, request("3000.00", TODAY.minusDays(1)), OWNER_ID));

        // Checked before anything is changed
        assertEquals(LocalDate.of(2027, 5, 1), goal.getTargetDate());
        assertEquals(0, new BigDecimal("2000.00").compareTo(goal.getTargetAmount()));
    }

    @Test
    void movingAPastDateToTodayOrLaterIsAllowed() {
        SavingsGoal goal = existingGoal(LocalDate.of(2026, 10, 1));

        service.saveGoal(TRIP_ID, request("2000.00", TODAY), OWNER_ID);
        assertEquals(TODAY, goal.getTargetDate());

        service.saveGoal(TRIP_ID, request("2000.00", LocalDate.of(2027, 3, 1)), OWNER_ID);
        assertEquals(LocalDate.of(2027, 3, 1), goal.getTargetDate());
    }

    // ---------- Unchanged behaviour ----------

    @Test
    void theTargetDateIsStillRequired() throws Exception {
        NotNull notNull = SavingsGoalRequest.class
                .getDeclaredField("targetDate")
                .getAnnotation(NotNull.class);

        assertEquals("Target date is required", notNull.message());
    }

    @Test
    void aMissingDateIsNotReportedAsAPastDate() {
        existingGoal(LocalDate.of(2026, 10, 1));

        // If a missing date ever got past validation, the new UC8 rule doesn't report it as "in the past":
        // it is handled exactly as before (the database rejects the empty NOT NULL column)
        try {
            service.saveGoal(TRIP_ID, request("2000.00", null), OWNER_ID);
        } catch (IllegalArgumentException e) {
            throw new AssertionError("UC8 must not reject a missing date: " + e.getMessage());
        } catch (RuntimeException expectedBeforeUc8Too) {
            // same as before UC8
        }
        assertEquals(1, saved.size(), "it reached the save, as before");
    }

    @Test
    void viewersStillCantChangeTheGoal() {
        assertThrows(TripAccessDeniedException.class,
            () -> service.saveGoal(TRIP_ID, request("2000.00", TODAY.plusDays(30)), VIEWER_ID));
        assertTrue(saved.isEmpty());
    }

    @Test
    void contributionsWithoutADateUseTheServersToday() {
        existingGoal(LocalDate.of(2027, 5, 1));

        service.addContribution(TRIP_ID, new ContributionRequest(new BigDecimal("40.00"), null, null, null), OWNER_ID);

        assertEquals(TODAY, added.get(0).getTransactionDate());
    }

    // ---------- Helpers ----------

    private SavingsGoal existingGoal(LocalDate targetDate) {
        SavingsGoal goal = new SavingsGoal(TRIP_ID);
        ReflectionTestUtils.setField(goal, "id", GOAL_ID);
        goal.setTargetAmount(new BigDecimal("2000.00"));
        goal.setTargetDate(targetDate);
        when(goals.findByTripId(TRIP_ID)).thenReturn(Optional.of(goal));
        return goal;
    }

    private static SavingsGoalRequest request(String target, LocalDate targetDate) {
        return new SavingsGoalRequest(new BigDecimal(target), BigDecimal.ZERO, targetDate, null);
    }
}
