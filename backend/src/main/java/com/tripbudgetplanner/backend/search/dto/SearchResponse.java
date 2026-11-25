package com.tripbudgetplanner.backend.search.dto;

import java.util.List;

import com.tripbudgetplanner.backend.currency.dto.RateInfo;

// Search results for one trip. All prices are in "currency" (the trip's currency).
// source is "demo" while the mock provider is used, and notice says so.
// exchangeRate is null when the provider already used the trip's currency.
public record SearchResponse(
    String type,
    Long tripId,
    String destination,
    String currency,
    int travelers,
    String source,
    boolean live,
    String notice,
    RateInfo exchangeRate,
    List<SearchResult> results
) {}
