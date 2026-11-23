package com.tripbudgetplanner.backend.currency;

// Something that can supply exchange rates.
// Two implementations: FrankfurterExchangeRateProvider (live) and FixedExchangeRateProvider (demo fallback).
// To use a different rates API later, add another class that implements this interface.
public interface ExchangeRateProvider {

    // Returns USD-based rates for all supported currencies.
    // Throws ExchangeRateException if the rates can't be loaded.
    ExchangeRates fetchRates();
}
