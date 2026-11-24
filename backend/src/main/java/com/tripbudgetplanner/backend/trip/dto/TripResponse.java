package com.tripbudgetplanner.backend.trip.dto;

import java.time.LocalDate;

// Information returned to the frontend after a trip is created.
// role: the signed-in user's role on this trip (OWNER, EDITOR or VIEWER).
// ownerName: the name of the user who created the trip.
public record TripResponse(
    Long id,
    String name,
    String destination,
    LocalDate startDate,
    LocalDate endDate,
    int numberOfTravelers,
    String currency,
    String role,
    String ownerName
) {}