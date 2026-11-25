package com.tripbudgetplanner.backend.search.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Add a search result to the trip's itinerary.
// Only itemId is required. The price, name and dates come from the provider, never from here.
// updateBudget: also add the estimated total to the budget (default false).
// startTime: optional new time, for restaurants and activities only.
// notes: optional, added to the itinerary entry.
public record AddTripItemRequest(

    @NotBlank(message = "itemId is required")
    @Size(max = 120, message = "itemId is too long")
    String itemId,

    Boolean updateBudget,

    LocalTime startTime,

    @Size(max = 2000, message = "Notes can be at most 2000 characters")
    String notes
) {}
