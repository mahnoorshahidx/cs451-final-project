package com.tripbudgetplanner.backend.itinerary.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

// Activity information returned to the frontend.
// UC6 fields (null for manual and AI activities unless a cost was entered):
//   estimatedCost / costCurrency         - cost for the whole group
//   sourceType / sourceProvider          - set when added from a search result ("demo" for now)
//   sourceSegment                        - MAIN, or OUTBOUND / RETURN for round-trip flights
//   budgetAmountAdded / budgetAmountCurrency - what updateBudget=true added to the budget
public record ItineraryActivityResponse(
    Long id,
    Long tripId,
    LocalDate activityDate,
    String title,
    LocalTime startTime,
    LocalTime endTime,
    String category,
    String location,
    String notes,
    BigDecimal estimatedCost,
    String costCurrency,
    String sourceType,
    String sourceProvider,
    String sourceSegment,
    BigDecimal budgetAmountAdded,
    String budgetAmountCurrency
) {

    // The original 9-field form, still used by the AI itinerary generator. New fields are null.
    public ItineraryActivityResponse(Long id, Long tripId, LocalDate activityDate, String title,
                                     LocalTime startTime, LocalTime endTime, String category,
                                     String location, String notes) {
        this(id, tripId, activityDate, title, startTime, endTime, category, location, notes,
            null, null, null, null, null, null, null);
    }
}
