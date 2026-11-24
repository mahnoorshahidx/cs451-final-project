package com.tripbudgetplanner.backend.savings;

import static com.tripbudgetplanner.backend.member.SharedTripFixture.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.tripbudgetplanner.backend.bank.BankAccount;
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
import com.tripbudgetplanner.backend.trip.exception.TripNotFoundException;
import com.tripbudgetplanner.backend.user.UserRepository;

// UC9: shared savings goals keep each member's banking details private,
// and editors can only delete contributions they added.
class SavingsSharingPrivacyTest {

    private static final Long GOAL_ID = 100L;
    private static final Long OWNER_BANK = 501L;
    private static final Long EDITOR_BANK = 502L;

    private SharedTripFixture f;
    private SavingsContributionRepository contributions;
    private BankAccountRepository bankAccounts;
    private SavingsService service;

    private SavingsContribution byOwner;    // owner, with the owner's bank account
    private SavingsContribution byEditor;   // editor, with the editor's bank account
    private SavingsContribution legacy;     // saved before sharing existed (no creator)
    private final List<SavingsContribution> added = new ArrayList<>();

    @BeforeEach
    void setUp() {
        f = new SharedTripFixture();
        SavingsGoalRepository goals = mock(SavingsGoalRepository.class);
        contributions = mock(SavingsContributionRepository.class);
        bankAccounts = mock(BankAccountRepository.class);
        TripBudgetRepository budgets = mock(TripBudgetRepository.class);

        TripService tripService = new TripService(f.tripRepository, mock(UserRepository.class), goals, contributions,
            budgets, mock(ExpenseRepository.class), f.access, f.memberRepository, mock(TripInvitationRepository.class));
        service = new SavingsService(goals, contributions, tripService, budgets, bankAccounts, f.access);

        SavingsGoal goal = new SavingsGoal(TRIP_ID);
        ReflectionTestUtils.setField(goal, "id", GOAL_ID);
        goal.setTargetAmount(new BigDecimal("2000.00"));
        goal.setTargetDate(LocalDate.of(2027, 5, 1));
        when(goals.findByTripId(TRIP_ID)).thenReturn(Optional.of(goal));
        when(goals.save(any())).thenAnswer(call -> call.getArgument(0));
        when(contributions.sumByGoalId(GOAL_ID)).thenReturn(new BigDecimal("175.00"));

        byOwner = contribution(1L, OWNER_BANK, "50.00", OWNER_ID);
        byEditor = contribution(2L, EDITOR_BANK, "100.00", EDITOR_ID);
        legacy = contribution(3L, null, "25.00", null);
        when(contributions.findBySavingsGoalIdOrderByTransactionDateDescIdDesc(GOAL_ID))
            .thenReturn(List.of(byOwner, byEditor, legacy));
        when(contributions.save(any())).thenAnswer(call -> {
            added.add(call.getArgument(0));
            return call.getArgument(0);
        });

        // Each person's own connected bank accounts
        BankAccount ownerAccount = bankAccount(OWNER_BANK);
        BankAccount editorAccount = bankAccount(EDITOR_BANK);
        when(bankAccounts.findByUserId(OWNER_ID)).thenReturn(List.of(ownerAccount));
        when(bankAccounts.findByUserId(EDITOR_ID)).thenReturn(List.of(editorAccount));
        when(bankAccounts.findByIdAndUserId(OWNER_BANK, OWNER_ID)).thenReturn(Optional.of(ownerAccount));
        when(bankAccounts.findByIdAndUserId(EDITOR_BANK, EDITOR_ID)).thenReturn(Optional.of(editorAccount));
    }

    // ---------- Who sees which bank account ----------

    @Test
    void ownerSeesOnlyTheirOwnBankAccount() {
        List<SavingsResponse.Contribution> list = contributionsFor(OWNER_ID);

        assertEquals(OWNER_BANK, list.get(0).bankAccountId());
        assertNull(list.get(1).bankAccountId(), "the editor's bank account is private");
        assertTrue(list.get(0).addedByYou());
        assertTrue(list.get(2).addedByYou(), "legacy contributions belong to the owner");
        list.forEach(c -> assertTrue(c.canDelete(), "the owner can delete any contribution"));
    }

    @Test
    void editorSeesOnlyTheirOwnBankAccountAndCanDeleteOnlyTheirOwn() {
        List<SavingsResponse.Contribution> list = contributionsFor(EDITOR_ID);

        assertNull(list.get(0).bankAccountId(), "the owner's bank account is private");
        assertEquals(EDITOR_BANK, list.get(1).bankAccountId());
        assertFalse(list.get(0).canDelete());
        assertTrue(list.get(1).canDelete());
        assertFalse(list.get(2).canDelete(), "legacy rows belong to the owner");
        assertTrue(list.get(1).addedByYou());
    }

    @Test
    void viewerSeesNoBankAccountsAndCanDeleteNothing() {
        List<SavingsResponse.Contribution> list = contributionsFor(VIEWER_ID);

        list.forEach(c -> {
            assertNull(c.bankAccountId());
            assertFalse(c.canDelete());
            assertFalse(c.addedByYou());
        });
        // Totals are still shared, so everyone sees the same progress
        assertEquals(0, new BigDecimal("175.00").compareTo(service.getSavings(TRIP_ID, VIEWER_ID).orElseThrow().contributedAmount()));
    }

    @Test
    void strangerCannotSeeTheSavingsGoal() {
        assertThrows(TripNotFoundException.class, () -> service.getSavings(TRIP_ID, STRANGER_ID));
    }

    // ---------- Adding contributions ----------

    @Test
    void editorAddsAContributionWithTheirOwnBankAccount() {
        service.addContribution(TRIP_ID, contributionRequest(EDITOR_BANK), EDITOR_ID);

        assertEquals(1, added.size());
        assertEquals(EDITOR_ID, added.get(0).getCreatedByUserId());
        assertEquals(EDITOR_BANK, added.get(0).getBankAccountId());
    }

    @Test
    void memberCannotUseSomeoneElsesBankAccount() {
        assertThrows(IllegalArgumentException.class,
            () -> service.addContribution(TRIP_ID, contributionRequest(OWNER_BANK), EDITOR_ID));
        assertThrows(IllegalArgumentException.class,
            () -> service.addContribution(TRIP_ID, contributionRequest(EDITOR_BANK), OWNER_ID));
        assertTrue(added.isEmpty());
    }

    @Test
    void viewerCannotAddContributionsOrChangeTheGoal() {
        assertThrows(TripAccessDeniedException.class,
            () -> service.addContribution(TRIP_ID, contributionRequest(null), VIEWER_ID));
        assertThrows(TripAccessDeniedException.class,
            () -> service.saveGoal(TRIP_ID, goalRequest(), VIEWER_ID));
        assertTrue(added.isEmpty());
    }

    @Test
    void editorCanUpdateTheGoal() {
        SavingsResponse result = service.saveGoal(TRIP_ID, goalRequest(), EDITOR_ID);

        assertEquals(0, new BigDecimal("2500.00").compareTo(result.targetAmount()));
    }

    // ---------- Deleting contributions ----------

    @Test
    void editorDeletesTheirOwnContribution() {
        stubFind(byEditor);

        service.deleteContribution(TRIP_ID, byEditor.getId(), EDITOR_ID);

        verify(contributions).delete(byEditor);
    }

    @Test
    void editorCannotDeleteTheOwnersOrLegacyContributions() {
        stubFind(byOwner);
        stubFind(legacy);

        assertThrows(TripAccessDeniedException.class,
            () -> service.deleteContribution(TRIP_ID, byOwner.getId(), EDITOR_ID));
        assertThrows(TripAccessDeniedException.class,
            () -> service.deleteContribution(TRIP_ID, legacy.getId(), EDITOR_ID));
        verify(contributions, never()).delete(any());
    }

    @Test
    void ownerCanDeleteAnEditorsContribution() {
        stubFind(byEditor);

        service.deleteContribution(TRIP_ID, byEditor.getId(), OWNER_ID);

        verify(contributions).delete(byEditor);
    }

    @Test
    void viewerCannotDeleteContributions() {
        stubFind(byOwner);

        assertThrows(TripAccessDeniedException.class,
            () -> service.deleteContribution(TRIP_ID, byOwner.getId(), VIEWER_ID));
        verify(contributions, never()).delete(any());
    }

    // ---------- Personal financial pages stay personal ----------

    @Test
    void budgetOverviewOnlyListsTripsYouOwn() {
        when(f.tripRepository.findByUserIdOrderByStartDateAsc(EDITOR_ID)).thenReturn(List.of());

        assertTrue(service.overview(EDITOR_ID).isEmpty(), "a shared trip is not part of the editor's own overview");
    }

    // ---------- Helpers ----------

    private List<SavingsResponse.Contribution> contributionsFor(Long userId) {
        return service.getSavings(TRIP_ID, userId).orElseThrow().contributions();
    }

    private void stubFind(SavingsContribution contribution) {
        when(contributions.findByIdAndSavingsGoalId(contribution.getId(), GOAL_ID)).thenReturn(Optional.of(contribution));
    }

    private static SavingsContribution contribution(Long id, Long bankAccountId, String amount, Long createdBy) {
        SavingsContribution c = new SavingsContribution(GOAL_ID, bankAccountId, new BigDecimal(amount),
            LocalDate.of(2026, 11, 1), "Saved", createdBy);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private static BankAccount bankAccount(Long id) {
        BankAccount account = new BankAccount();
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private static ContributionRequest contributionRequest(Long bankAccountId) {
        return new ContributionRequest(new BigDecimal("40.00"), LocalDate.of(2026, 11, 20), "Weekly savings", bankAccountId);
    }

    private static SavingsGoalRequest goalRequest() {
        return new SavingsGoalRequest(new BigDecimal("2500.00"), BigDecimal.ZERO, LocalDate.of(2027, 5, 1), new BigDecimal("300.00"));
    }
}
