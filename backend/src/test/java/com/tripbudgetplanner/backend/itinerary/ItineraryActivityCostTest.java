package com.tripbudgetplanner.backend.itinerary;

import static com.tripbudgetplanner.backend.member.SharedTripFixture.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.tripbudgetplanner.backend.itinerary.dto.ItineraryActivityRequest;
import com.tripbudgetplanner.backend.itinerary.dto.ItineraryActivityResponse;
import com.tripbudgetplanner.backend.member.SharedTripFixture;
import com.tripbudgetplanner.backend.trip.exception.TripAccessDeniedException;

// UC6: optional estimated cost on manual activities, and backward compatibility
class ItineraryActivityCostTest {

    private final SharedTripFixture f = new SharedTripFixture();
    private final ItineraryActivityRepository repository = mock(ItineraryActivityRepository.class);
    private final ItineraryActivityService service = new ItineraryActivityService(repository, f.access);

    ItineraryActivityCostTest() {
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void manualActivityCanHaveACostInTheTripCurrency() {
        ItineraryActivityResponse r = service.createActivity(TRIP_ID, request(new BigDecimal("45.5")), EDITOR_ID);

        assertEquals(0, new BigDecimal("45.50").compareTo(r.estimatedCost()));
        assertEquals("USD", r.costCurrency());
        assertNull(r.sourceType(), "manual activities have no search source");
    }

    @Test
    void activitiesWithoutACostStillWork() {
        ItineraryActivityResponse r = service.createActivity(TRIP_ID, oldStyleRequest(), OWNER_ID);

        assertNull(r.estimatedCost());
        assertNull(r.costCurrency());
    }

    @Test
    void editingWithoutACostKeepsTheSavedCost() {
        ItineraryActivity existing = new ItineraryActivity();
        existing.setTrip(f.trip);
        existing.setEstimatedCost(new BigDecimal("120.00"), "USD");
        existing.setSource("HOTEL", "demo", "demo-hotel-x", "MAIN");
        when(repository.findByIdAndTripId(7L, TRIP_ID)).thenReturn(Optional.of(existing));

        // The current editor page sends only the original 7 fields
        ItineraryActivityResponse r = service.updateActivity(TRIP_ID, 7L, oldStyleRequest(), EDITOR_ID);

        assertEquals(0, new BigDecimal("120.00").compareTo(r.estimatedCost()));
        assertEquals("HOTEL", r.sourceType(), "search source is kept too");
        assertEquals("Museum", r.title());
    }

    @Test
    void yenCostsCantHaveDecimals() {
        f.trip.setCurrency("JPY");

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
            () -> service.createActivity(TRIP_ID, request(new BigDecimal("1500.50")), OWNER_ID));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());

        ItineraryActivityResponse ok = service.createActivity(TRIP_ID, request(new BigDecimal("1500")), OWNER_ID);
        assertEquals("JPY", ok.costCurrency());
        assertEquals(0, ok.estimatedCost().scale());
    }

    @Test
    void viewersCantSetCosts() {
        assertThrows(TripAccessDeniedException.class,
            () -> service.createActivity(TRIP_ID, request(BigDecimal.TEN), VIEWER_ID));
    }

    @Test
    void listShowsCostsAndSourceFields() {
        ItineraryActivity a = new ItineraryActivity();
        a.setTrip(f.trip);
        a.setEstimatedCost(new BigDecimal("300.00"), "USD");
        a.setSource("FLIGHT", "demo", "demo-flight-x", "OUTBOUND");
        a.setBudgetAmountAdded(new BigDecimal("276.00"), "EUR");
        when(repository.findByTripIdOrderByActivityDateAscStartTimeAsc(TRIP_ID)).thenReturn(List.of(a));

        ItineraryActivityResponse r = service.listActivities(TRIP_ID, VIEWER_ID).get(0);

        assertEquals("OUTBOUND", r.sourceSegment());
        assertEquals("EUR", r.budgetAmountCurrency());
        assertEquals(0, new BigDecimal("276.00").compareTo(r.budgetAmountAdded()));
    }

    @Test
    void oldNineFieldResponseConstructorStillWorks() {
        // Used by the AI itinerary generator, which wasn't changed
        ItineraryActivityResponse r = new ItineraryActivityResponse(1L, TRIP_ID, LocalDate.of(2027, 6, 2),
            "AI pick", null, null, "Activity", null, null);

        assertNull(r.estimatedCost());
        assertNull(r.sourceType());
    }

    @Test
    void costOnlyAndCurrencyGoTogether() {
        ItineraryActivity a = new ItineraryActivity();
        a.setEstimatedCost(null, "USD");
        assertNull(a.getCostCurrency());
        a.setBudgetAmountAdded(null, "USD");
        assertNull(a.getBudgetAmountCurrency());
    }

    private static ItineraryActivityRequest request(BigDecimal cost) {
        return new ItineraryActivityRequest(LocalDate.of(2027, 6, 2), "Museum", null, null, "Sightseeing",
            "Ueno", null, cost);
    }

    private static ItineraryActivityRequest oldStyleRequest() {
        return new ItineraryActivityRequest(LocalDate.of(2027, 6, 2), "Museum", null, null, "Sightseeing", "Ueno", null);
    }
}
