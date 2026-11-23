package com.tripbudgetplanner.backend.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.tripbudgetplanner.backend.currency.dto.ConversionResponse;
import com.tripbudgetplanner.backend.currency.dto.CurrencyResponse;

// Converts money between the 8 supported currencies.
//
// - app.fx.provider=live (default): uses Frankfurter, caches the rates for app.fx.cache-minutes,
//   and switches to the fixed demo rates if Frankfurter can't be reached.
// - app.fx.provider=fixed: always uses the fixed demo rates (handy when working offline).
//
// All math uses BigDecimal. Results are rounded to the target currency's
// decimal places (2 for most, 0 for JPY and KRW).
@Service
public class CurrencyService {

    private static final Logger log = LoggerFactory.getLogger(CurrencyService.class);

    // Largest amount we accept, same limit as the budget and expense forms (10 digits, 2 decimals)
    static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    // After a failed live request, wait this long before trying the live API again
    static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(5);

    // Decimals shown for the exchange rate itself
    private static final int RATE_SCALE = 6;

    static final String FALLBACK_NOTICE =
        "Live exchange rates are unavailable. This uses fixed demo rates, so it is only an estimate.";

    private final ExchangeRateProvider liveProvider;
    private final ExchangeRateProvider fallbackProvider;
    private final boolean useLiveRates;
    private final Duration cacheDuration;
    private final Clock clock;

    // The last rates we loaded and when they expire
    private ExchangeRates cachedRates;
    private Instant cachedUntil = Instant.MIN;

    // Used by Spring
    @Autowired
    public CurrencyService(
        @Qualifier("frankfurterExchangeRateProvider") ExchangeRateProvider liveProvider,
        @Qualifier("fixedExchangeRateProvider") ExchangeRateProvider fallbackProvider,
        @Value("${app.fx.provider:live}") String providerMode,
        @Value("${app.fx.cache-minutes:60}") long cacheMinutes
    ) {
        this(liveProvider, fallbackProvider, providerMode, cacheMinutes, Clock.systemUTC());
    }

    // Used by unit tests, which pass a fake clock to test the cache
    CurrencyService(ExchangeRateProvider liveProvider, ExchangeRateProvider fallbackProvider,
                    String providerMode, long cacheMinutes, Clock clock) {
        this.liveProvider = liveProvider;
        this.fallbackProvider = fallbackProvider;
        this.useLiveRates = !"fixed".equalsIgnoreCase(providerMode == null ? "" : providerMode.trim());
        this.cacheDuration = Duration.ofMinutes(Math.max(1, cacheMinutes));
        this.clock = clock;

        if (!useLiveRates) {
            log.info("Currency conversion uses fixed demo exchange rates (app.fx.provider=fixed).");
        }
    }

    // ---------- Supported currencies ----------

    public List<CurrencyResponse> listCurrencies() {
        return Arrays.stream(SupportedCurrency.values())
            .map(c -> new CurrencyResponse(c.name(), c.getDisplayName(), c.getSymbol(), c.getDecimalPlaces()))
            .toList();
    }

    // ---------- Conversion ----------

    // Converts using currency codes like "usd" or "JPY". Validates the codes and the amount.
    public ConversionResponse convert(String fromCode, String toCode, BigDecimal amount) {
        SupportedCurrency from = SupportedCurrency.fromCode(fromCode);
        SupportedCurrency to = SupportedCurrency.fromCode(toCode);
        return convert(amount, from, to);
    }

    // Converts an amount. Other services (e.g. expense vs. budget) can call this directly.
    public ConversionResponse convert(BigDecimal amount, SupportedCurrency from, SupportedCurrency to) {
        // Same currency: no rates needed, so don't load them
        return convert(amount, from, to, from == to ? null : currentRates());
    }

    // Converts an amount using rates the caller already loaded with currentRates().
    // Use this to convert several amounts with the same rates (one snapshot),
    // e.g. a budget's total and each of its categories.
    public ConversionResponse convert(BigDecimal amount, SupportedCurrency from, SupportedCurrency to,
                                      ExchangeRates rates) {
        validateAmount(amount);

        // Same currency: nothing to look up, the rate is exactly 1
        if (from == to) {
            BigDecimal same = amount.setScale(to.getDecimalPlaces(), RoundingMode.HALF_UP);
            return new ConversionResponse(from.name(), to.name(), amount,
                BigDecimal.ONE.setScale(RATE_SCALE), same, null, "none", null);
        }
        if (rates == null) {
            throw new IllegalArgumentException("Exchange rates are required to convert between currencies");
        }

        BigDecimal fromRate = rates.rateFor(from);   // 1 USD = fromRate "from"
        BigDecimal toRate = rates.rateFor(to);       // 1 USD = toRate "to"

        // amount in "to" = amount / fromRate * toRate.
        // Multiply first, then divide once, so we only round one time.
        BigDecimal converted = amount.multiply(toRate)
            .divide(fromRate, to.getDecimalPlaces(), RoundingMode.HALF_UP);
        BigDecimal rate = toRate.divide(fromRate, RATE_SCALE, RoundingMode.HALF_UP);

        return new ConversionResponse(
            from.name(),
            to.name(),
            amount,
            rate,
            converted,
            rates.rateDate(),
            rates.source(),
            rates.isLive() ? null : FALLBACK_NOTICE);
    }

    // Amount must be given, not negative, at most 2 decimals and not too large
    private static void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Amount can have at most 2 decimal places");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Amount is too large");
        }
    }

    // ---------- Rates and cache ----------

    // Returns the rates to use right now (live, cached, or the fixed demo rates).
    // Public so a service can load them once and pass them to convert(..., rates).
    // synchronized so two requests at the same moment don't both call the API.
    public synchronized ExchangeRates currentRates() {
        if (!useLiveRates) {
            return fallbackProvider.fetchRates();
        }

        Instant now = clock.instant();
        if (cachedRates != null && now.isBefore(cachedUntil)) {
            return cachedRates;
        }

        try {
            cachedRates = liveProvider.fetchRates();
            cachedUntil = now.plus(cacheDuration);
        } catch (RuntimeException e) {
            log.warn("Live exchange rates unavailable ({}). Using {} rates.", e.getMessage(),
                cachedRates != null && cachedRates.isLive() ? "the last live" : "fixed demo");

            // Older live rates are still better than demo rates, so keep them if we have them
            if (cachedRates == null || !cachedRates.isLive()) {
                cachedRates = fallbackProvider.fetchRates();
            }
            // Don't call the live API on every request while it's down
            cachedUntil = now.plus(RETRY_AFTER_FAILURE);
        }
        return cachedRates;
    }
}
