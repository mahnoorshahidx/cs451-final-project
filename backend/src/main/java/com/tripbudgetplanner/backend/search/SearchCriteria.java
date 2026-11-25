package com.tripbudgetplanner.backend.search;

import java.math.BigDecimal;
import java.time.LocalDate;

// What a provider searches for. Already validated by TravelSearchService.
// startDate/endDate: flight depart/return, hotel check-in/check-out,
// or the day for restaurants and activities (endDate null).
// Price filters are NOT here: they are applied in the trip's currency after conversion.
public record SearchCriteria(
    TripItemType type,
    String destination,
    LocalDate startDate,
    LocalDate endDate,
    int travelers,
    String origin,        // flights only, 3-letter airport code
    String cuisine,       // restaurants only
    Integer priceLevel,   // restaurants only, 1-4
    String keyword,       // activities only
    BigDecimal minRating
) {}
