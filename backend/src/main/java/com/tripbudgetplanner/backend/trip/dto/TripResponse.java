package com.tripbudgetplanner.backend.trip.dto;

import java.time.LocalDate;

// Information returned to the frontend after a trip is created
public record TripResponse(
    Long id,
    String name,
    String destination,
    LocalDate startDate,
    LocalDate endDate,
    int numberOfTravelers
) {}