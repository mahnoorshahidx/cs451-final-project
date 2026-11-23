package com.tripbudgetplanner.backend.currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import com.tripbudgetplanner.backend.currency.exception.ExchangeRateException;

// A set of exchange rates, all based on USD: usdRates.get(EUR) = how many euros 1 US dollar buys.
// rateDate is the day the rates were published (null for the fixed demo rates).
// source is "live" (Frankfurter) or "fallback" (fixed demo rates).
public record ExchangeRates(Map<SupportedCurrency, BigDecimal> usdRates, LocalDate rateDate, String source) {

    public static final String LIVE = "live";
    public static final String FALLBACK = "fallback";

    // Checks the rates when they are created, so a bad API reply is caught right away
    public ExchangeRates {
        if (usdRates == null) {
            throw new ExchangeRateException("No exchange rates were provided");
        }
        for (SupportedCurrency currency : SupportedCurrency.values()) {
            BigDecimal rate = usdRates.get(currency);
            if (rate == null || rate.signum() <= 0) {
                throw new ExchangeRateException("Missing or invalid exchange rate for " + currency);
            }
        }
        // Keep our own copy so nobody can change the rates later
        usdRates = Map.copyOf(new EnumMap<>(usdRates));
    }

    // How many units of this currency 1 USD buys
    public BigDecimal rateFor(SupportedCurrency currency) {
        return usdRates.get(currency);
    }

    public boolean isLive() {
        return LIVE.equals(source);
    }
}
