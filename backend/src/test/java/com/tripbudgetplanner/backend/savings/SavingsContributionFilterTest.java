package com.tripbudgetplanner.backend.savings;

import static com.tripbudgetplanner.backend.member.SharedTripFixture.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.tripbudgetplanner.backend.bank.BankAccount;
import com.tripbudgetplanner.backend.bank.BankAccountRepository;
import com.tripbudgetplanner.backend.bank.exception.BankAccountNotFoundException;
import com.tripbudgetplanner.backend.budget.TripBudgetRepository;
import com.tripbudgetplanner.backend.expense.ExpenseRepository;
import com.tripbudgetplanner.backend.member.SharedTripFixture;
import com.tripbudgetplanner.backend.member.TripInvitationRepository;
import com.tripbudgetplanner.backend.savings.dto.ContributionHistoryResponse;
import com.tripbudgetplanner.backend.savings.dto.SavingsResponse;
import com.tripbudgetplanner.backend.trip.TripService;
import com.tripbudgetplanner.backend.trip.exception.TripNotFoundException;
import com.tripbudgetplanner.backend.user.UserRepository;

// UC7: filtering a trip's savings contribution history by date range and bank account.
// Trip 10: Olivia owns it, Eddie is an editor, Vera a viewer, Sam has no access.
class SavingsContributionFilterTest {

    private static final Long GOAL_ID = 100L;
    private static final Long OWNER_BANK = 501L;
    private static final Long EDITOR_BANK = 502L;
    private static final Long STRANGER_BANK = 504L;

    // Today is 25 Nov 2026 (noon in Chicago)
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T18:00:00Z"), ZoneId.of("America/Chicago"));

    private SharedTripFixture f;
    private SavingsGoalRepository goals;
    private SavingsContributionRepository contributions;
    private SavingsService service;

    // Newest first, like the repository returns them. Total 250.
    private final SavingsContribution nov20 = contribution(1L, OWNER_BANK, "50.00", "2026-11-20", OWNER_ID);
    private final SavingsContribution nov10 = contribution(2L, EDITOR_BANK, "100.00", "2026-11-10", EDITOR_ID);
    private final SavingsContribution oct15 = contribution(3L, null, "25.00", "2026-10-15", null);   // legacy: no creator
    private final SavingsContribution sep01 = contribution(4L, OWNER_BANK, "75.00", "2026-09-01", OWNER_ID);

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

        SavingsGoal goal = new SavingsGoal(TRIP_ID);
        ReflectionTestUtils.setField(goal, "id", GOAL_ID);
        goal.setTargetAmount(new BigDecimal("2000.00"));
        goal.setTargetDate(LocalDate.of(2027, 5, 1));
        goal.setPlannedMonthlyContribution(new BigDecimal("300.00"));
        when(goals.findByTripId(TRIP_ID)).thenReturn(Optional.of(goal));
        when(contributions.sumByGoalId(GOAL_ID)).thenReturn(new BigDecimal("250.00"));
        when(contributions.findBySavingsGoalIdOrderByTransactionDateDescIdDesc(GOAL_ID))
            .thenReturn(List.of(nov20, nov10, oct15, sep01));

        BankAccount ownerAccount = bankAccount(OWNER_BANK);
        BankAccount editorAccount = bankAccount(EDITOR_BANK);
        BankAccount strangerAccount = bankAccount(STRANGER_BANK);
        when(bankAccounts.findByUserId(OWNER_ID)).thenReturn(List.of(ownerAccount));
        when(bankAccounts.findByUserId(EDITOR_ID)).thenReturn(List.of(editorAccount));
        when(bankAccounts.findByUserId(STRANGER_ID)).thenReturn(List.of(strangerAccount));
        when(bankAccounts.findByIdAndUserId(OWNER_BANK, OWNER_ID)).thenReturn(Optional.of(ownerAccount));
        when(bankAccounts.findByIdAndUserId(EDITOR_BANK, EDITOR_ID)).thenReturn(Optional.of(editorAccount));
        when(bankAccounts.findByIdAndUserId(STRANGER_BANK, STRANGER_ID)).thenReturn(Optional.of(strangerAccount));
    }

    // ---------- No filters = the existing history ----------

    @Test
    void withoutFiltersEveryContributionIsListedLikeTheSavingsPage() {
        ContributionHistoryResponse r = history(null, null, null, OWNER_ID);
        SavingsResponse savings = service.getSavings(TRIP_ID, OWNER_ID).orElseThrow();

        assertEquals(List.of(1L, 2L, 3L, 4L), ids(r));
        assertEquals(4, r.filteredCount());
        assertMoney("250.00", r.filteredTotal());
        assertEquals(savings.contributions(), r.contributions());
        assertNull(r.message());
    }

    @Test
    void blankParametersCountAsNotGiven() {
        assertEquals(List.of(1L, 2L, 3L, 4L), ids(history("", "  ", "", OWNER_ID)));
    }

    // ---------- Date filters ----------

    @Test
    void fromOnlyIncludesThatDay() {
        assertEquals(List.of(1L, 2L), ids(history("2026-11-10", null, null, OWNER_ID)));
    }

    @Test
    void toOnlyIncludesThatDay() {
        assertEquals(List.of(3L, 4L), ids(history(null, "2026-10-15", null, OWNER_ID)));
    }

    @Test
    void aDateRangeIncludesBothEndsAndKeepsNewestFirst() {
        ContributionHistoryResponse r = history("2026-10-15", "2026-11-10", null, OWNER_ID);

        assertEquals(List.of(2L, 3L), ids(r));
        assertMoney("125.00", r.filteredTotal());
        assertEquals(LocalDate.of(2026, 10, 15), r.from());
        assertEquals(LocalDate.of(2026, 11, 10), r.to());
    }

    @Test
    void aSingleDayRangeWorks() {
        assertEquals(List.of(1L), ids(history("2026-11-20", "2026-11-20", null, OWNER_ID)));
    }

    @Test
    void startAfterEndIs400() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> history("2026-11-21", "2026-11-20", null, OWNER_ID));

        assertEquals("Start date (from) can't be after end date (to)", e.getMessage());
    }

    @Test
    void badlyWrittenDatesAre400WithAClearMessage() {
        assertEquals("from must be a date like 2027-01-31", assertThrows(IllegalArgumentException.class,
            () -> history("11/20/2026", null, null, OWNER_ID)).getMessage());
        assertEquals("to must be a date like 2027-01-31", assertThrows(IllegalArgumentException.class,
            () -> history(null, "2026-02-30", null, OWNER_ID)).getMessage());
    }

    @Test
    void nothingMatchingGivesAnEmptyListAndAMessage() {
        ContributionHistoryResponse r = history("2027-01-01", null, null, OWNER_ID);

        assertTrue(r.contributions().isEmpty());
        assertEquals(0, r.filteredCount());
        assertMoney("0", r.filteredTotal());
        assertEquals("No contributions match these filters", r.message());
    }

    // ---------- Bank account filter ----------

    @Test
    void filterByYourOwnBankAccount() {
        ContributionHistoryResponse r = history(null, null, "501", OWNER_ID);

        assertEquals(List.of(1L, 4L), ids(r));
        assertMoney("125.00", r.filteredTotal());
        r.contributions().forEach(c -> assertEquals(OWNER_BANK, c.bankAccountId()));
        assertEquals(OWNER_BANK, r.bankAccountId());
    }

    @Test
    void anEditorCanFilterByTheirOwnBankAccount() {
        ContributionHistoryResponse r = history(null, null, "502", EDITOR_ID);

        assertEquals(List.of(2L), ids(r));
        assertEquals(EDITOR_BANK, r.contributions().get(0).bankAccountId());
    }

    @Test
    void dateAndBankFiltersCombine() {
        assertEquals(List.of(1L), ids(history("2026-10-01", "2026-11-30", "501", OWNER_ID)));
    }

    @Test
    void someoneElsesBankAccountIs404SoItsExistenceStaysPrivate() {
        // The editor tries the owner's account: same answer as an account that doesn't exist
        BankAccountNotFoundException e = assertThrows(BankAccountNotFoundException.class,
            () -> history(null, null, "501", EDITOR_ID));
        assertEquals("Bank account not found", e.getMessage());

        assertThrows(BankAccountNotFoundException.class, () -> history(null, null, "999", EDITOR_ID));
        assertThrows(BankAccountNotFoundException.class, () -> history(null, null, "502", VIEWER_ID));
    }

    @Test
    void aBankAccountIdThatIsNotANumberIs400() {
        assertEquals("bankAccountId must be a number", assertThrows(IllegalArgumentException.class,
            () -> history(null, null, "abc", OWNER_ID)).getMessage());
    }

    // ---------- Filters never change the goal's numbers ----------

    @Test
    void goalTotalsStatusAndProjectionIgnoreTheFilters() {
        SavingsResponse savings = service.getSavings(TRIP_ID, OWNER_ID).orElseThrow();
        List<ContributionHistoryResponse> views = List.of(
            history(null, null, null, OWNER_ID),
            history("2026-11-15", null, null, OWNER_ID),
            history(null, "2026-09-30", null, OWNER_ID),
            history(null, null, "501", OWNER_ID),
            history("2030-01-01", null, null, OWNER_ID),          // nothing matches
            history("2026-11-01", "2026-11-30", null, VIEWER_ID));

        for (ContributionHistoryResponse r : views) {
            assertMoney("2000.00", r.goalTargetAmount());
            assertEquals(savings.savedAmount(), r.goalSavedAmount());
            assertEquals(savings.remainingAmount(), r.goalRemainingAmount());
            assertEquals(savings.percentFunded(), r.goalPercentFunded());
            assertEquals(savings.status(), r.goalStatus());
            assertEquals(savings.projectedCompletionDate(), r.projectedCompletionDate());
            assertEquals(savings.projectionStatus(), r.projectionStatus());
        }
        assertMoney("250.00", savings.savedAmount());
    }

    // ---------- Permissions and privacy ----------

    @Test
    void filteredListsKeepEachMembersBankAccountPrivate() {
        // The editor sees only their own bank account, even when filtering by date
        List<SavingsResponse.Contribution> editorView = history("2026-11-01", null, null, EDITOR_ID).contributions();
        assertNull(editorView.get(0).bankAccountId(), "the owner's account is hidden");
        assertEquals(EDITOR_BANK, editorView.get(1).bankAccountId());
        assertFalse(editorView.get(0).canDelete());
        assertTrue(editorView.get(1).canDelete());

        // The owner doesn't see the editor's account either
        List<SavingsResponse.Contribution> ownerView = history("2026-11-01", null, null, OWNER_ID).contributions();
        assertEquals(OWNER_BANK, ownerView.get(0).bankAccountId());
        assertNull(ownerView.get(1).bankAccountId());
    }

    @Test
    void viewersCanFilterButSeeNoBankAccountsAndCanDeleteNothing() {
        ContributionHistoryResponse r = history("2026-09-01", "2026-12-31", null, VIEWER_ID);

        assertEquals(4, r.filteredCount());
        r.contributions().forEach(c -> {
            assertNull(c.bankAccountId());
            assertFalse(c.canDelete());
            assertFalse(c.addedByYou());
        });
    }

    @Test
    void legacyContributionsStillBelongToTheOwner() {
        SavingsResponse.Contribution legacy = history("2026-10-15", "2026-10-15", null, OWNER_ID).contributions().get(0);

        assertTrue(legacy.addedByYou());
        assertTrue(legacy.canDelete());
    }

    @Test
    void strangersGet404EvenWithTheirOwnBankAccountOrBadFilters() {
        // Access is checked first, so a stranger learns nothing from the filters
        assertThrows(TripNotFoundException.class, () -> history(null, null, null, STRANGER_ID));
        assertThrows(TripNotFoundException.class, () -> history(null, null, "504", STRANGER_ID));
        assertThrows(TripNotFoundException.class, () -> history("bad", "dates", "x", STRANGER_ID));
    }

    // ---------- No goal yet ----------

    @Test
    void aTripWithoutAGoalGivesAnEmptyListAndAMessage() {
        when(goals.findByTripId(TRIP_ID)).thenReturn(Optional.empty());

        ContributionHistoryResponse r = history(null, null, null, OWNER_ID);

        assertTrue(r.contributions().isEmpty());
        assertNull(r.goalTargetAmount());
        assertNull(r.projectionStatus());
        assertEquals("This trip has no savings goal yet", r.message());
    }

    @Test
    void aGoalWithoutContributionsSaysSo() {
        when(contributions.findBySavingsGoalIdOrderByTransactionDateDescIdDesc(GOAL_ID)).thenReturn(List.of());

        assertEquals("No contributions yet", history("2026-01-01", null, null, OWNER_ID).message());
    }

    // ---------- The projection on the savings page ----------

    @Test
    void theSavingsPageShowsTheProjectedCompletionDate() {
        SavingsResponse r = service.getSavings(TRIP_ID, OWNER_ID).orElseThrow();

        // Last 90 days (from 27 Aug): 250 saved since 1 Sep = 85 days.
        // 1750 left -> ceil(1750 * 85 / 250) = 595 days -> 13 Jul 2028, after the May 2027 target
        assertEquals(LocalDate.of(2026, 11, 25).plusDays(595), r.projectedCompletionDate());
        assertEquals(SavingsProjection.BEHIND_PACE, r.projectionStatus());
        // 250 * 30.44 / 85 = 89.53 per month
        assertMoney("89.53", r.projectedMonthlyRate());
    }

    @Test
    void yenTripsShowAWholeNumberMonthlyRate() {
        f.trip.setCurrency("JPY");

        assertMoney("90", service.getSavings(TRIP_ID, OWNER_ID).orElseThrow().projectedMonthlyRate());
        assertEquals(0, service.getSavings(TRIP_ID, OWNER_ID).orElseThrow().projectedMonthlyRate().scale());
    }

    // ---------- Helpers ----------

    private ContributionHistoryResponse history(String from, String to, String bankAccountId, Long userId) {
        return service.contributionHistory(TRIP_ID, from, to, bankAccountId, userId);
    }

    private static List<Long> ids(ContributionHistoryResponse r) {
        return r.contributions().stream().map(SavingsResponse.Contribution::id).toList();
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private static SavingsContribution contribution(Long id, Long bankAccountId, String amount, String date, Long createdBy) {
        SavingsContribution c = new SavingsContribution(GOAL_ID, bankAccountId, new BigDecimal(amount),
            LocalDate.parse(date), "Saved", createdBy);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private static BankAccount bankAccount(Long id) {
        BankAccount account = new BankAccount();
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
