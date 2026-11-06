
package com.tripbudgetplanner.backend.itinerary;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.tripbudgetplanner.backend.itinerary.dto.ItineraryActivityRequest;
import com.tripbudgetplanner.backend.itinerary.dto.ItineraryActivityResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/trips/{tripId}/activities")
public class ItineraryActivityController {

    private final ItineraryActivityService activityService;

    public ItineraryActivityController(
            ItineraryActivityService activityService) {
        this.activityService = activityService;
    }

    // Retrieve all activities for a trip.
    @GetMapping
    public List<ItineraryActivityResponse> listActivities(
            @PathVariable Long tripId,
            @AuthenticationPrincipal Long userId) {

        return activityService.listActivities(tripId, userId);
    }

    // Create a new activity.
    @PostMapping
    public ResponseEntity<ItineraryActivityResponse> createActivity(
            @PathVariable Long tripId,
            @Valid @RequestBody ItineraryActivityRequest request,
            @AuthenticationPrincipal Long userId) {

        ItineraryActivityResponse activity =
                activityService.createActivity(
                        tripId, request, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(activity);
    }

    // Update an existing activity.
    @PutMapping("/{activityId}")
    public ItineraryActivityResponse updateActivity(
            @PathVariable Long tripId,
            @PathVariable Long activityId,
            @Valid @RequestBody ItineraryActivityRequest request,
            @AuthenticationPrincipal Long userId) {

        return activityService.updateActivity(
                tripId, activityId, request, userId);
    }

    // Delete an existing activity.
    @DeleteMapping("/{activityId}")
    public ResponseEntity<Void> deleteActivity(
            @PathVariable Long tripId,
            @PathVariable Long activityId,
            @AuthenticationPrincipal Long userId) {

        activityService.deleteActivity(
                tripId, activityId, userId);

        return ResponseEntity.noContent().build();
    }
}
