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

import com.tripbudgetplanner.backend.member.TripRole;
import com.tripbudgetplanner.backend.trip.dto.CreateTripRequest;
import com.tripbudgetplanner.backend.trip.dto.TripResponse;

import jakarta.validation.Valid;

// Handles API requests related to trips
@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;
    private final TripAccessService tripAccess;

    public TripController(TripService tripService, TripAccessService tripAccess) {
        this.tripService = tripService;
        this.tripAccess = tripAccess;
    }

    // Creates a new trip for the logged-in user (id comes from the JWT)
    @PostMapping
    public ResponseEntity<TripResponse> createTrip(
            @Valid @RequestBody CreateTripRequest request,
            @AuthenticationPrincipal Long userId) {
        Trip trip = tripService.createTrip(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(trip, TripRole.OWNER));
    }

    // Lists the logged-in user's own trips (dashboard). Shared trips are in /shared.
    @GetMapping
    public List<TripResponse> listTrips(@AuthenticationPrincipal Long userId) {
        return tripService.listTrips(userId).stream()
            .map(trip -> toResponse(trip, TripRole.OWNER))
            .toList();
    }

    // UC9: trips other people shared with the logged-in user, with their role
    @GetMapping("/shared")
    public List<TripResponse> listSharedTrips(@AuthenticationPrincipal Long userId) {
        return tripService.listSharedTrips(userId).stream()
            .map(member -> toResponse(member.getTrip(), member.getRole()))
            .toList();
    }

    // Views one trip (owner, editor or viewer)
    @GetMapping("/{id}")
    public TripResponse getTrip(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        Trip trip = tripService.getTrip(id, userId);
        return toResponse(trip, tripAccess.roleOf(trip, userId));
    }

    // Edits a trip (owner or editor)
    @PutMapping("/{id}")
    public TripResponse updateTrip(@PathVariable Long id,
                                   @Valid @RequestBody CreateTripRequest request,
                                   @AuthenticationPrincipal Long userId) {
        Trip trip = tripService.updateTrip(id, request, userId);
        return toResponse(trip, tripAccess.roleOf(trip, userId));
    }

    // Deletes a trip (owner only)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrip(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        tripService.deleteTrip(id, userId);
        return ResponseEntity.noContent().build();
    }

    private TripResponse toResponse(Trip trip, TripRole role) {
        return new TripResponse(
                trip.getId(), trip.getName(), trip.getDestination(),
                trip.getStartDate(), trip.getEndDate(), trip.getNumberOfTravelers(),
                trip.getCurrency(),
                role == null ? null : role.name(),
                trip.getUser() == null ? null : trip.getUser().getName());
    }
}
