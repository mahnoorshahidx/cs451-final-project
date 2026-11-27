package com.tripbudgetplanner.backend.itinerary;

import com.tripbudgetplanner.backend.trip.Trip;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tripbudgetplanner.backend.itinerary.dto.ItineraryGenerationRequest;
import java.math.BigDecimal;

// Checks that AI replies are turned into valid activities, and bad items are skipped.
class ItineraryGenerationServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Trip trip = mock(Trip.class);
    private final LocalDate first = LocalDate.of(2027, 1, 10);
    private final LocalDate last = LocalDate.of(2027, 1, 14);

    private List<ItineraryActivity> read(String json) {
        JsonNode node = mapper.readTree(json);
        return ItineraryGenerationService.readActivities(node, trip, first, last);
    }

    @Test
    void readsValidActivity() {
        List<ItineraryActivity> result = read("""
            {"activities": [{"date": "2027-01-11", "startTime": "09:00", "endTime": "11:30",
              "title": "Haleakala sunrise", "category": "Sightseeing", "location": "Haleakala National Park",
              "notes": "Book the sunrise permit ahead. About $1 per person plus park entry."}]}
            """);

        assertEquals(1, result.size());
        ItineraryActivity a = result.get(0);
        assertEquals(LocalDate.of(2027, 1, 11), a.getActivityDate());
        assertEquals(LocalTime.of(9, 0), a.getStartTime());
        assertEquals(LocalTime.of(11, 30), a.getEndTime());
        assertEquals("Sightseeing", a.getCategory());
        assertSame(trip, a.getTrip());
        assertTrue(a.getNotes().endsWith("(AI suggestion)"));
    }

    @Test
    void skipsDatesOutsideTripAndMissingTitles() {
        List<ItineraryActivity> result = read("""
            {"activities": [
              {"date": "2027-01-20", "title": "After the trip"},
              {"date": "2027-01-12", "title": ""},
              {"date": "not a date", "title": "Bad date"},
              {"date": "2027-01-12", "title": "Beach day"}
            ]}
            """);

        assertEquals(1, result.size());
        assertEquals("Beach day", result.get(0).getTitle());
    }

    @Test
    void fixesBadTimesAndUnknownCategory() {
        List<ItineraryActivity> result = read("""
            {"activities": [
              {"date": "2027-01-12", "startTime": "14:00", "endTime": "13:00", "title": "Snorkeling", "category": "Water fun"},
              {"date": "2027-01-12", "endTime": "18:00", "title": "Dinner", "category": "Food", "location": null}
            ]}
            """);

        assertEquals(LocalTime.of(14, 0), result.get(0).getStartTime());
        assertNull(result.get(0).getEndTime());          // end before start: dropped
        assertEquals("Activity", result.get(0).getCategory());

        assertNull(result.get(1).getStartTime());
        assertNull(result.get(1).getEndTime());          // end without start: dropped
        assertNull(result.get(1).getLocation());         // JSON null: no location, not "null"
    }

    @Test
    void emptyWhenNoActivities() {
        assertTrue(read("{\"activities\": []}").isEmpty());
        assertTrue(read("{\"something\": 1}").isEmpty());
    }

    // ---------- Preferences are applied to the reply ----------

    @Test
    void keepsAtMostThePaceLimitPerDayEarliestFirst() {
        JsonNode node = mapper.readTree("""
            {"activities": [
              {"date": "2027-01-11", "startTime": "18:00", "title": "Dinner"},
              {"date": "2027-01-11", "startTime": "09:00", "title": "Breakfast"},
              {"date": "2027-01-11", "startTime": "12:00", "title": "Lunch"},
              {"date": "2027-01-11", "startTime": "14:00", "title": "Museum"},
              {"date": "2027-01-12", "startTime": "10:00", "title": "Beach"}
            ]}
            """);

        List<ItineraryActivity> result =
            ItineraryGenerationService.readActivities(node, trip, first, last, 3, 2, "USD");

        assertEquals(4, result.size());
        assertEquals(List.of("Breakfast", "Lunch", "Museum", "Beach"),
            result.stream().map(ItineraryActivity::getTitle).toList());
    }

    @Test
    void costPerPersonBecomesGroupCostInTripCurrency() {
        JsonNode node = mapper.readTree("""
            {"activities": [
              {"date": "2027-01-11", "title": "Museum", "costPerPerson": 12.5},
              {"date": "2027-01-11", "title": "Market", "costPerPerson": "8"},
              {"date": "2027-01-11", "title": "Park", "costPerPerson": -3},
              {"date": "2027-01-11", "title": "Walk"}
            ]}
            """);

        List<ItineraryActivity> result =
            ItineraryGenerationService.readActivities(node, trip, first, last, null, 3, "USD");

        assertEquals(new BigDecimal("37.50"), result.get(0).getEstimatedCost());
        assertEquals("USD", result.get(0).getCostCurrency());
        assertEquals(new BigDecimal("24.00"), result.get(1).getEstimatedCost());
        assertNull(result.get(2).getEstimatedCost());   // negative: ignored
        assertNull(result.get(3).getEstimatedCost());   // missing: no cost
    }

    @Test
    void promptIncludesEveryPreference() {
        when(trip.getDestination()).thenReturn("Lisbon, Portugal");
        when(trip.getCurrency()).thenReturn("EUR");
        when(trip.getNumberOfTravelers()).thenReturn(2);

        ItineraryGenerationRequest request = new ItineraryGenerationRequest(
            new BigDecimal("120"), "USD", "luxury", "4.5", List.of("Food", "Nature"), "relaxed",
            List.of("Halal"), "No museums", false, first, last, List.of("Belem Tower"));

        String prompt = ItineraryGenerationService.userPrompt(trip, request, first, last);

        assertTrue(prompt.contains("Lisbon, Portugal"));
        assertTrue(prompt.contains("Travelers: 2"));
        assertTrue(prompt.contains("120 EUR"));          // budget in the trip's currency
        assertTrue(prompt.contains("Travel style: luxury"));
        assertTrue(prompt.contains("4.5 out of 5"));
        assertTrue(prompt.contains("Food, Nature"));
        assertTrue(prompt.contains("2 to 3 activities per day"));
        assertTrue(prompt.contains("Halal"));
        assertTrue(prompt.contains("No museums"));
        assertTrue(prompt.contains("Belem Tower"));
        assertTrue(prompt.contains("Plan these dates: 2027-01-10 to 2027-01-14"));
    }
}
