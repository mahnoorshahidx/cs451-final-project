package com.tripbudgetplanner.backend.itinerary.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Answers from the AI itinerary questionnaire. The destination and dates come from the saved trip.
public record ItineraryGenerationRequest(

    // Page 1: daily budget for food and activities, per person, in the trip's currency (optional)
    @DecimalMin(value = "1", message = "Daily budget must be at least 1")
    @DecimalMax(value = "1000000", message = "Daily budget is too large")
    BigDecimal dailyBudget,

    // Ignored for costs: AI costs are always in the trip's currency. Kept for older clients.
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter code like USD")
    String currency,

    @Pattern(regexp = "budget|balanced|luxury", message = "Travel style must be 'budget', 'balanced' or 'luxury'")
    String travelStyle,

    // Page 2: "any", or a number from 0.5 to 5 like "4.0"
    @Pattern(regexp = "any|[0-4](\\.[05])?|5(\\.0)?", message = "Minimum rating must be 'any' or 0.5 to 5")
    String minimumRating,

    // Page 3: e.g. ["Sightseeing", "Nature"]
    @Size(max = 20)
    List<@Size(max = 50) String> interests,

    // Page 4: how busy the days should be
    @NotBlank(message = "Pace is required")
    @Pattern(regexp = "relaxed|balanced|packed", message = "Pace must be 'relaxed', 'balanced' or 'packed'")
    String pace,

    // e.g. ["Halal", "Local cuisine"]. Empty or null means no restrictions.
    @Size(max = 20)
    List<@Size(max = 50) String> foodPreferences,

    @Size(max = 1000, message = "Special requests can be at most 1000 characters")
    String specialRequests,

    // true: delete existing activities first (only inside startDate..endDate when a range is given).
    // false or null: add to them.
    Boolean replaceExisting,

    // Optional: only plan these days (for regenerating part of an itinerary). Both must be within
    // the trip. Null means from the trip's first day. At most MAX_DAYS days are planned per request.
    LocalDate startDate,
    LocalDate endDate,

    // Optional: places already planned on other days, so the AI doesn't repeat them
    @Size(max = 300)
    List<@Size(max = 255) String> avoidTitles
) {

    // The original 9-field form (no date range, nothing to avoid)
    public ItineraryGenerationRequest(BigDecimal dailyBudget, String currency, String travelStyle,
                                      String minimumRating, List<String> interests, String pace,
                                      List<String> foodPreferences, String specialRequests,
                                      Boolean replaceExisting) {
        this(dailyBudget, currency, travelStyle, minimumRating, interests, pace, foodPreferences,
            specialRequests, replaceExisting, null, null, null);
    }
}
