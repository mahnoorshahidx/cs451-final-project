
package com.tripbudgetplanner.backend.itinerary.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Information submitted when creating or editing an activity.
public record ItineraryActivityRequest(

    @NotNull
    LocalDate activityDate,

    @NotBlank
    @Size(max = 255)
    String title,

    LocalTime startTime,

    LocalTime endTime,

    @NotBlank
    @Size(max = 100)
    String category,

    @Size(max = 255)
    String location,

    String notes
) {}
