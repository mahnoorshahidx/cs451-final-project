package com.tripbudgetplanner.backend.trip;

import static com.tripbudgetplanner.backend.member.SharedTripFixture.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.tripbudgetplanner.backend.budget.TripBudget;
import com.tripbudgetplanner.backend.budget.TripBudgetRepository;
import com.tripbudgetplanner.backend.expense.ExpenseRepository;
import com.tripbudgetplanner.backend.itinerary.ItineraryActivityRepository;
import com.tripbudgetplanner.backend.member.SharedTripFixture;
import com.tripbudgetplanner.backend.member.TripInvitationRepository;
import com.tripbudgetplanner.backend.savings.SavingsContributionRepository;
import com.tripbudgetplanner.backend.savings.SavingsGoalRepository;
import com.tripbudgetplanner.backend.trip.dto.CreateTripRequest;
import com.tripbudgetplanner.backend.trip.exception.TripCurrencyChangeException;
import com.tripbudgetplanner.backend.user.UserRepository;

// UC6: itinerary cost estimates are in the trip's currency, so they block currency changes too
class TripCurrencyItineraryGuardTest {

    private final SharedTripFixture f = new SharedTripFixture();
    private final TripBudgetRepository budgets = mock(TripBudgetRepository.class);
    private final ItineraryActivityRepository activities = mock(ItineraryActivityRepository.class);
    private final TripService service = new TripService(f.tripRepository, mock(UserRepository.class),
        mock(SavingsGoalRepository.class), mock(SavingsContributionRepository.class), budgets,
        mock(ExpenseRepository.class), f.access, f.memberRepository, mock(TripInvitationRepository.class), activities);

    TripCurrencyItineraryGuardTest() {
        when(f.tripRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void itineraryCostsBlockACurrencyChange() {
        when(activities.existsByTripIdAndEstimatedCostIsNotNull(TRIP_ID)).thenReturn(true);

        TripCurrencyChangeException e = assertThrows(TripCurrencyChangeException.class,
            () -> service.updateTrip(TRIP_ID, request("EUR"), EDITOR_ID));

        assertTrue(e.getMessage().contains("itinerary cost estimates"));
        assertEquals("USD", f.trip.getCurrency());
        verify(f.tripRepository, never()).save(any());
    }

    @Test
    void theyAlsoBlockMatchingTheBudgetsCurrency() {
        TripBudget budget = new TripBudget(TRIP_ID);
        budget.setCurrency("EUR");
        when(budgets.findByTripId(TRIP_ID)).thenReturn(Optional.of(budget));
        when(activities.existsByTripIdAndEstimatedCostIsNotNull(TRIP_ID)).thenReturn(true);

        assertThrows(TripCurrencyChangeException.class, () -> service.updateTrip(TRIP_ID, request("EUR"), OWNER_ID));
    }

    @Test
    void activitiesWithoutCostsDontBlock() {
        assertEquals("JPY", service.updateTrip(TRIP_ID, request("JPY"), OWNER_ID).getCurrency());
    }

    @Test
    void aCurrencyChangeLocksTheTripFirst() {
        service.updateTrip(TRIP_ID, request("JPY"), OWNER_ID);

        verify(budgets).lockTrip(TRIP_ID);
    }

    @Test
    void sameCurrencyNeedsNoCheckOrLock() {
        when(activities.existsByTripIdAndEstimatedCostIsNotNull(TRIP_ID)).thenReturn(true);

        assertEquals("USD", service.updateTrip(TRIP_ID, request(null), OWNER_ID).getCurrency());
        verify(budgets, never()).lockTrip(any());
    }

    private static CreateTripRequest request(String currency) {
        CreateTripRequest r = new CreateTripRequest();
        r.setName("Tokyo Trip");
        r.setDestination("Tokyo");
        r.setStartDate(LocalDate.of(2027, 6, 1));
        r.setEndDate(LocalDate.of(2027, 6, 10));
        r.setNumberOfTravelers(3);
        r.setCurrency(currency);
        return r;
    }
}
