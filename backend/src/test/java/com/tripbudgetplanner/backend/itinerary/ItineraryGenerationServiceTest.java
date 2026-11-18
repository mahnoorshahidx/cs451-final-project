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
}
