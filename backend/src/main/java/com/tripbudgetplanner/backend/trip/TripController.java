package com.tripbudgetplanner.backend.trip;

import com.tripbudgetplanner.backend.trip.dto.CreateTripRequest;
import com.tripbudgetplanner.backend.trip.dto.TripResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

        TripResponse response = new TripResponse(
                trip.getId(), trip.getName(), trip.getDestination(),
                trip.getStartDate(), trip.getEndDate(), trip.getNumberOfTravelers());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}