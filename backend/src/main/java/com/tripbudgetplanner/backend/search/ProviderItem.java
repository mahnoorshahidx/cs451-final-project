package com.tripbudgetplanner.backend.search;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import com.tripbudgetplanner.backend.currency.SupportedCurrency;

// One result as the provider describes it, priced in the provider's own currency.
// destinationKey and travelers say which search produced it, so an item can be
// checked against the trip before it is added.
// Flights: date = departure, endDate = return; startTime/endTime = outbound,
//          returnStartTime/returnEndTime = return. A null end time means it lands the next day.
// Hotels:  date = check-in, endDate = check-out.
public record ProviderItem(
    String itemId,
    TripItemType type,
    String name,
    String location,
    BigDecimal rating,
    LocalDate date,
    LocalDate endDate,
    LocalTime startTime,
    LocalTime endTime,
    LocalTime returnStartTime,
    LocalTime returnEndTime,
    PriceBasis priceBasis,
    BigDecimal unitPrice,
    SupportedCurrency priceCurrency,
    int quantity,
    String quantityDescription,
    String destinationKey,
    int travelers,
    Map<String, String> details
) {}
