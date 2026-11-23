package com.tripbudgetplanner.backend.currency.dto;

// One supported currency, e.g. { "code": "JPY", "name": "Japanese Yen", "symbol": "¥", "decimalPlaces": 0 }
public record CurrencyResponse(
    String code,
    String name,
    String symbol,
    int decimalPlaces
) {}
