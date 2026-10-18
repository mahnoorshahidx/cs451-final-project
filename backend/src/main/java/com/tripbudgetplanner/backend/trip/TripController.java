package com.tripbudgetplanner.backend.trip;

import com.tripbudgetplanner.backend.trip.dto.CreateTripRequest;
import com.tripbudgetplanner.backend.trip.dto.TripResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Handles API requests related to trips
@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    // Connects the controller to the trip service
    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    // Creates a new trip for the logged-in user
    @PostMapping
    public ResponseEntity<TripResponse> createTrip(
            @Valid @RequestBody CreateTripRequest request) {

        // TODO: Get the logged-in user's ID once authentication is implemented
        throw new UnsupportedOperationException(
            "Trip creation requires authentication integration"
        );
    }
}