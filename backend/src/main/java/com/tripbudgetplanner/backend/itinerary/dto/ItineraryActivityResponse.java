
package com.tripbudgetplanner.backend.itinerary.dto;

import java.time.LocalDate;
import java.time.LocalTime;

// Activity information returned to the frontend.
public record ItineraryActivityResponse(
    Long id,
    Long tripId,
    LocalDate activityDate,
    String title,
    LocalTime startTime,
    LocalTime endTime,
    String category,
    String location,
    String notes
) {}
