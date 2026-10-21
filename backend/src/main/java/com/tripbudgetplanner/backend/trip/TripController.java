package com.tripbudgetplanner.backend.trip;

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

import com.tripbudgetplanner.backend.trip.dto.CreateTripRequest;
import com.tripbudgetplanner.backend.trip.dto.TripResponse;

import jakarta.validation.Valid;

// Handles API requests related to trips
@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    // Creates a new trip for the logged-in user (id comes from the JWT)
    @PostMapping
    public ResponseEntity<TripResponse> createTrip(
            @Valid @RequestBody CreateTripRequest request,
            @AuthenticationPrincipal Long userId) {
        Trip trip = tripService.createTrip(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(trip));
    }

    // Lists the logged-in user's trips (dashboard)
    @GetMapping
    public List<TripResponse> listTrips(@AuthenticationPrincipal Long userId) {
        return tripService.listTrips(userId).stream().map(this::toResponse).toList();
    }

    // Views one trip
    @GetMapping("/{id}")
    public TripResponse getTrip(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return toResponse(tripService.getTrip(id, userId));
    }

    // Edits a trip
    @PutMapping("/{id}")
    public TripResponse updateTrip(@PathVariable Long id,
                                   @Valid @RequestBody CreateTripRequest request,
                                   @AuthenticationPrincipal Long userId) {
        return toResponse(tripService.updateTrip(id, request, userId));
    }

    // Deletes a trip
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrip(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        tripService.deleteTrip(id, userId);
        return ResponseEntity.noContent().build();
    }

    private TripResponse toResponse(Trip trip) {
        return new TripResponse(
                trip.getId(), trip.getName(), trip.getDestination(),
                trip.getStartDate(), trip.getEndDate(), trip.getNumberOfTravelers());
    }
}