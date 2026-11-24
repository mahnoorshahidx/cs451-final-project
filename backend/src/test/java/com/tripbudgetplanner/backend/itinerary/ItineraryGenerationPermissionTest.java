package com.tripbudgetplanner.backend.itinerary;

import static com.tripbudgetplanner.backend.member.SharedTripFixture.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.tripbudgetplanner.backend.ai.AnthropicClient;
import com.tripbudgetplanner.backend.itinerary.dto.ItineraryGenerationRequest;
import com.tripbudgetplanner.backend.member.SharedTripFixture;
import com.tripbudgetplanner.backend.trip.exception.TripAccessDeniedException;
import com.tripbudgetplanner.backend.trip.exception.TripNotFoundException;

import tools.jackson.databind.ObjectMapper;

// UC9: only the owner or an editor can generate AI activities.
// The permission check happens before the AI is called.
class ItineraryGenerationPermissionTest {

    private final SharedTripFixture f = new SharedTripFixture();
    private final ItineraryActivityRepository activities = mock(ItineraryActivityRepository.class);

    // No API key, so the AI is off: an allowed user gets 503, a blocked user never gets that far
    private final ItineraryGenerationService service = new ItineraryGenerationService(
        new AnthropicClient(new ObjectMapper(), "", "test-model", "https://example.invalid", 5, 512),
        f.access, activities);

    private final ItineraryGenerationRequest request = new ItineraryGenerationRequest(
        null, "USD", "balanced", "any", List.of("Food"), "balanced", List.of(), "", false);

    @Test
    void viewerCannotGenerate() {
        assertThrows(TripAccessDeniedException.class, () -> service.generate(TRIP_ID, request, VIEWER_ID));
        verifyNoInteractions(activities);
    }

    @Test
    void strangerGets404() {
        assertThrows(TripNotFoundException.class, () -> service.generate(TRIP_ID, request, STRANGER_ID));
    }

    @Test
    void editorPassesThePermissionCheck() {
        // Gets past the check and stops at "AI not available" (503)
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
            () -> service.generate(TRIP_ID, request, EDITOR_ID));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
    }
}
