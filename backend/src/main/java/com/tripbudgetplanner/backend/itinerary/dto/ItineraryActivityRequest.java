package com.tripbudgetplanner.backend.itinerary.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Information submitted when creating or editing an activity.
// estimatedCost is optional (UC6): the cost for the whole group in the trip's currency.
// When editing, leaving it out keeps the saved cost, so older pages don't erase it.
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

    String notes,

    @DecimalMin(value = "0", message = "Estimated cost cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Estimated cost can have at most 2 decimal places")
    BigDecimal estimatedCost
) {

    // The original form without a cost (kept so existing code and tests still work)
    public ItineraryActivityRequest(LocalDate activityDate, String title, LocalTime startTime, LocalTime endTime,
                                    String category, String location, String notes) {
        this(activityDate, title, startTime, endTime, category, location, notes, null);
    }
}
