package com.tripbudgetplanner.backend.member;

import static com.tripbudgetplanner.backend.member.SharedTripFixture.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tripbudgetplanner.backend.budget.TripBudget;
import com.tripbudgetplanner.backend.budget.TripBudgetRepository;
import com.tripbudgetplanner.backend.budget.TripBudgetService;
import com.tripbudgetplanner.backend.budget.dto.BudgetRequest;
import com.tripbudgetplanner.backend.currency.CurrencyService;
import com.tripbudgetplanner.backend.currency.FixedExchangeRateProvider;
import com.tripbudgetplanner.backend.expense.ExpenseRepository;
import com.tripbudgetplanner.backend.expense.ExpenseService;
import com.tripbudgetplanner.backend.expense.dto.ExpenseRequest;
import com.tripbudgetplanner.backend.itinerary.ItineraryActivityRepository;
import com.tripbudgetplanner.backend.itinerary.ItineraryActivityService;
import com.tripbudgetplanner.backend.itinerary.dto.ItineraryActivityRequest;
import com.tripbudgetplanner.backend.savings.SavingsContributionRepository;
import com.tripbudgetplanner.backend.savings.SavingsGoalRepository;
import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.trip.TripService;
import com.tripbudgetplanner.backend.trip.dto.CreateTripRequest;
import com.tripbudgetplanner.backend.trip.exception.TripAccessDeniedException;
import com.tripbudgetplanner.backend.trip.exception.TripCurrencyChangeException;
import com.tripbudgetplanner.backend.trip.exception.TripNotFoundException;
import com.tripbudgetplanner.backend.user.UserRepository;

// UC9: owner / editor / viewer / stranger across the existing trip features
class SharedTripPermissionTest {

    private SharedTripFixture f;
    private TripBudgetRepository budgetRepository;
    private ExpenseRepository expenseRepository;
    private ItineraryActivityRepository activityRepository;
    private TripInvitationRepository invitationRepository;

    private TripService tripService;
    private TripBudgetService budgetService;
    private ExpenseService expenseService;
    private ItineraryActivityService activityService;

    @BeforeEach
    void setUp() {
        f = new SharedTripFixture();
        budgetRepository = mock(TripBudgetRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        activityRepository = mock(ItineraryActivityRepository.class);
        invitationRepository = mock(TripInvitationRepository.class);

        tripService = new TripService(f.tripRepository, mock(UserRepository.class),
            mock(SavingsGoalRepository.class), mock(SavingsContributionRepository.class),
            budgetRepository, expenseRepository, f.access, f.memberRepository, invitationRepository);
        budgetService = new TripBudgetService(budgetRepository, f.access);
        expenseService = new ExpenseService(expenseRepository, f.access, budgetRepository,
            new CurrencyService(new FixedExchangeRateProvider(), new FixedExchangeRateProvider(), "fixed", 60));
        activityService = new ItineraryActivityService(activityRepository, f.access);

        when(f.tripRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(budgetRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(expenseRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(activityRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    // ---------- Trips ----------

    @Test
    void everyMemberCanViewTheTrip() {
        assertSame(f.trip, tripService.getTrip(TRIP_ID, OWNER_ID));
        assertSame(f.trip, tripService.getTrip(TRIP_ID, EDITOR_ID));
        assertSame(f.trip, tripService.getTrip(TRIP_ID, VIEWER_ID));
        assertThrows(TripNotFoundException.class, () -> tripService.getTrip(TRIP_ID, STRANGER_ID));
    }

    @Test
    void editorCanEditTripDetailsButViewerCannot() {
        Trip updated = tripService.updateTrip(TRIP_ID, tripRequest(null), EDITOR_ID);
        assertEquals("Tokyo and Kyoto", updated.getName());

        assertThrows(TripAccessDeniedException.class,
            () -> tripService.updateTrip(TRIP_ID, tripRequest(null), VIEWER_ID));
        assertThrows(TripNotFoundException.class,
            () -> tripService.updateTrip(TRIP_ID, tripRequest(null), STRANGER_ID));
    }

    @Test
    void currencyProtectionStillAppliesToEditors() {
        TripBudget budget = new TripBudget(TRIP_ID);
        budget.setCurrency("USD");
        when(budgetRepository.findByTripId(TRIP_ID)).thenReturn(Optional.of(budget));

        assertThrows(TripCurrencyChangeException.class,
            () -> tripService.updateTrip(TRIP_ID, tripRequest("EUR"), EDITOR_ID));
        assertEquals("USD", f.trip.getCurrency());
    }

    @Test
    void onlyTheOwnerCanDeleteTheTrip() {
        assertThrows(TripAccessDeniedException.class, () -> tripService.deleteTrip(TRIP_ID, EDITOR_ID));
        assertThrows(TripAccessDeniedException.class, () -> tripService.deleteTrip(TRIP_ID, VIEWER_ID));
        assertThrows(TripNotFoundException.class, () -> tripService.deleteTrip(TRIP_ID, STRANGER_ID));
        verify(f.tripRepository, never()).delete(any());

        tripService.deleteTrip(TRIP_ID, OWNER_ID);

        // Members and invitations go with the trip
        verify(f.memberRepository).deleteByTripId(TRIP_ID);
        verify(invitationRepository).deleteByTripId(TRIP_ID);
        verify(f.tripRepository).delete(f.trip);
    }

    @Test
    void sharedTripsListShowsTheUsersRole() {
        when(f.memberRepository.findByUserIdOrderByJoinedAtDesc(EDITOR_ID)).thenReturn(List.of(f.editorMember));

        List<TripMember> shared = tripService.listSharedTrips(EDITOR_ID);

        assertEquals(1, shared.size());
        assertSame(f.trip, shared.get(0).getTrip());
        assertEquals(TripRole.EDITOR, shared.get(0).getRole());
        assertTrue(tripService.listSharedTrips(STRANGER_ID).isEmpty());
    }

    @Test
    void ownTripsListStillShowsOnlyOwnedTrips() {
        when(f.tripRepository.findByUserIdOrderByStartDateAsc(OWNER_ID)).thenReturn(List.of(f.trip));

        assertEquals(List.of(f.trip), tripService.listTrips(OWNER_ID));
        assertTrue(tripService.listTrips(EDITOR_ID).isEmpty(), "shared trips are in /shared, not here");
    }

    // ---------- Budget ----------

    @Test
    void viewerCanSeeTheBudgetButNotSaveIt() {
        assertDoesNotThrow(() -> budgetService.getBudget(TRIP_ID, VIEWER_ID));

        assertThrows(TripAccessDeniedException.class, () -> budgetService.saveBudget(TRIP_ID, budgetRequest(), VIEWER_ID));
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void editorCanSaveTheBudget() {
        TripBudget saved = budgetService.saveBudget(TRIP_ID, budgetRequest(), EDITOR_ID);

        assertEquals(0, new BigDecimal("2000.00").compareTo(saved.getTotalAmount()));
    }

    @Test
    void strangerCannotSeeTheBudget() {
        assertThrows(TripNotFoundException.class, () -> budgetService.getBudget(TRIP_ID, STRANGER_ID));
    }

    // ---------- Expenses and the UC11 comparison ----------

    @Test
    void viewerCanReadExpensesSummaryAndComparison() {
        assertDoesNotThrow(() -> expenseService.list(TRIP_ID, VIEWER_ID));
        assertDoesNotThrow(() -> expenseService.summary(TRIP_ID, VIEWER_ID));
        assertEquals("NO_BUDGET", expenseService.compareToBudget(TRIP_ID, VIEWER_ID).status());
    }

    @Test
    void viewerCannotChangeExpenses() {
        assertThrows(TripAccessDeniedException.class, () -> expenseService.create(TRIP_ID, expenseRequest(), VIEWER_ID));
        assertThrows(TripAccessDeniedException.class, () -> expenseService.update(TRIP_ID, 5L, expenseRequest(), VIEWER_ID));
        assertThrows(TripAccessDeniedException.class, () -> expenseService.delete(TRIP_ID, 5L, VIEWER_ID));
        verify(expenseRepository, never()).save(any());
        verify(expenseRepository, never()).delete(any());
    }

    @Test
    void editorCanAddExpenses() {
        assertEquals(TRIP_ID, expenseService.create(TRIP_ID, expenseRequest(), EDITOR_ID).tripId());
    }

    @Test
    void strangerCannotSeeExpensesOrTheComparison() {
        assertThrows(TripNotFoundException.class, () -> expenseService.list(TRIP_ID, STRANGER_ID));
        assertThrows(TripNotFoundException.class, () -> expenseService.compareToBudget(TRIP_ID, STRANGER_ID));
    }

    // ---------- Itinerary ----------

    @Test
    void viewerCanSeeTheItineraryButNotChangeIt() {
        assertDoesNotThrow(() -> activityService.listActivities(TRIP_ID, VIEWER_ID));

        assertThrows(TripAccessDeniedException.class,
            () -> activityService.createActivity(TRIP_ID, activityRequest(), VIEWER_ID));
        assertThrows(TripAccessDeniedException.class,
            () -> activityService.deleteActivity(TRIP_ID, 7L, VIEWER_ID));
        verify(activityRepository, never()).save(any());
    }

    @Test
    void editorCanAddActivities() {
        assertEquals("Senso-ji", activityService.createActivity(TRIP_ID, activityRequest(), EDITOR_ID).title());
    }

    // ---------- Helpers ----------

    private static CreateTripRequest tripRequest(String currency) {
        CreateTripRequest request = new CreateTripRequest();
        request.setName("Tokyo and Kyoto");
        request.setDestination("Tokyo");
        request.setStartDate(LocalDate.of(2027, 6, 1));
        request.setEndDate(LocalDate.of(2027, 6, 10));
        request.setNumberOfTravelers(3);
        request.setCurrency(currency);
        return request;
    }

    private static BudgetRequest budgetRequest() {
        return new BudgetRequest(new BigDecimal("2000.00"), "USD", Map.of("Food", new BigDecimal("500.00")));
    }

    private static ExpenseRequest expenseRequest() {
        return new ExpenseRequest("Food", new BigDecimal("25.00"), LocalDate.of(2027, 6, 2), "Ramen");
    }

    private static ItineraryActivityRequest activityRequest() {
        return new ItineraryActivityRequest(LocalDate.of(2027, 6, 2), "Senso-ji", null, null, "Sightseeing", "Asakusa", null);
    }

}
