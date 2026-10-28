package com.tripbudgetplanner.backend.recommendation.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

// Everything the wizard knows by the time it reaches the budget step
public record BudgetRecommendationRequest(

    @NotBlank(message = "Destination is required")
    @Size(max = 255)
    String destination,

    // Optional: lets the estimate account for peak vs. off-season
    LocalDate startDate,

    @NotNull(message = "Number of nights is required")
    @Min(value = 1, message = "At least 1 night")
    @Max(value = 90, message = "At most 90 nights")
    Integer nights,

    @NotNull(message = "Number of travelers is required")
    @Min(value = 1, message = "At least 1 traveler")
    @Max(value = 50, message = "At most 50 travelers")
    Integer travelers,

    List<String> tradeOffs,

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter code like USD")
    String currency
) {}
