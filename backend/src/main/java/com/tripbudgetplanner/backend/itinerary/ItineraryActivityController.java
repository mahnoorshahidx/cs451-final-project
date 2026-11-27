package com.tripbudgetplanner.backend.itinerary;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbudgetplanner.backend.itinerary.dto.ItineraryActivityRequest;
import com.tripbudgetplanner.backend.itinerary.dto.ItineraryActivityResponse;
import com.tripbudgetplanner.backend.itinerary.dto.ItineraryGenerationRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/trips/{tripId}/activities")
public class ItineraryActivityController {

    private final ItineraryActivityService activityService;
    private final ItineraryGenerationService generationService;

    public ItineraryActivityController(
            ItineraryActivityService activityService,
            ItineraryGenerationService generationService) {
        this.activityService = activityService;
        this.generationService = generationService;
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

    // Generate AI-suggested activities from the user's preferences and save them.
    // Optional startDate/endDate plan only those days (replaceExisting then only clears those days).
    // Returns only the new activities. 503 if the AI isn't available.
    @PostMapping("/generate")
    public ResponseEntity<List<ItineraryActivityResponse>> generateActivities(
            @PathVariable Long tripId,
            @Valid @RequestBody ItineraryGenerationRequest request,
            @AuthenticationPrincipal Long userId) {

        List<ItineraryActivityResponse> activities =
                generationService.generate(tripId, request, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(activities);
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

    // Delete the whole itinerary (every activity in the trip). The trip itself is kept.
    @DeleteMapping
    public ResponseEntity<Void> deleteAllActivities(
            @PathVariable Long tripId,
            @AuthenticationPrincipal Long userId) {

        activityService.deleteAllActivities(tripId, userId);

        return ResponseEntity.noContent().build();
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