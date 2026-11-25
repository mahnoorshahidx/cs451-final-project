package com.tripbudgetplanner.backend.search.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

// One search result, priced in the trip's currency.
// unitPrice x quantity = estimatedTotal (the whole group). priceBasis says what one unitPrice pays for.
// originalUnitPrice / originalTotal / originalCurrency are the provider's own prices.
// A null endTime or returnEndTime means the flight lands the next day.
public record SearchResult(
    String itemId,
    String type,
    String name,
    String location,
    BigDecimal rating,
    LocalDate date,
    LocalDate endDate,
    LocalTime startTime,
    LocalTime endTime,
    LocalTime returnStartTime,
    LocalTime returnEndTime,
    String priceBasis,
    String priceBasisLabel,
    BigDecimal unitPrice,
    int quantity,
    String quantityDescription,
    BigDecimal estimatedTotal,
    String currency,
    BigDecimal originalUnitPrice,
    BigDecimal originalTotal,
    String originalCurrency,
    String budgetCategory,
    boolean alreadyAdded,
    String source,
    Map<String, String> details
) {

    // The same result, marked as added to the itinerary
    public SearchResult withAlreadyAdded() {
        return new SearchResult(itemId, type, name, location, rating, date, endDate, startTime, endTime,
            returnStartTime, returnEndTime, priceBasis, priceBasisLabel, unitPrice, quantity, quantityDescription,
            estimatedTotal, currency, originalUnitPrice, originalTotal, originalCurrency, budgetCategory,
            true, source, details);
    }
}
