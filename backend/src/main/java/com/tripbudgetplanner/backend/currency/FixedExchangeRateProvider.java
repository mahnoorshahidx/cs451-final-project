package com.tripbudgetplanner.backend.currency;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

// FIXED DEMO RATES, NOT LIVE DATA.
// Used when app.fx.provider=fixed, or when the live rates API can't be reached.
// They are rough values for development only. Every conversion that uses them is
// returned with source "fallback" and a notice, so they are never shown as real rates.
// EUR and GBP match the demo rates already used by RuleBasedRecommendationProvider.
@Component
public class FixedExchangeRateProvider implements ExchangeRateProvider {

    // 1 USD = x units of each currency
    private static final Map<SupportedCurrency, BigDecimal> DEMO_USD_RATES = new EnumMap<>(Map.of(
        SupportedCurrency.USD, new BigDecimal("1.00"),
        SupportedCurrency.EUR, new BigDecimal("0.92"),
        SupportedCurrency.GBP, new BigDecimal("0.79"),
        SupportedCurrency.JPY, new BigDecimal("150.00"),
        SupportedCurrency.CAD, new BigDecimal("1.37"),
        SupportedCurrency.AUD, new BigDecimal("1.52"),
        SupportedCurrency.INR, new BigDecimal("83.00"),
        SupportedCurrency.KRW, new BigDecimal("1350.00")
    ));

    @Override
    public ExchangeRates fetchRates() {
        // No publish date: these rates are not from any real day
        return new ExchangeRates(DEMO_USD_RATES, null, ExchangeRates.FALLBACK);
    }
}
