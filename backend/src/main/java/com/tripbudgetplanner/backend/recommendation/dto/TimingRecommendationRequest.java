package com.tripbudgetplanner.backend.recommendation.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

// "Recommend the best time" / "Find the cheapest time" on the timing step
public record TimingRecommendationRequest(

    @NotBlank(message = "Destination is required")
    @Size(max = 255)
    String destination,

    @NotBlank(message = "Mode is required")
    @Pattern(regexp = "best|cheapest", message = "Mode must be 'best' or 'cheapest'")
    String mode,

    @NotNull(message = "Window start is required")
    LocalDate windowStart,

    @NotNull(message = "Window end is required")
    LocalDate windowEnd,

    @NotNull(message = "Number of nights is required")
    @Min(value = 1, message = "At least 1 night")
    @Max(value = 60, message = "At most 60 nights")
    Integer nights
) {}
