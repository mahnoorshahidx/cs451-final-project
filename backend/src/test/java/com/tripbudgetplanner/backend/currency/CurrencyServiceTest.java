package com.tripbudgetplanner.backend.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tripbudgetplanner.backend.currency.dto.ConversionResponse;
import com.tripbudgetplanner.backend.currency.exception.ExchangeRateException;
import com.tripbudgetplanner.backend.currency.exception.UnsupportedCurrencyException;

class CurrencyServiceTest {

    private FakeProvider live;
    private FixedExchangeRateProvider fixed;
    private MutableClock clock;
    private CurrencyService service;

    @BeforeEach
    void setUp() {
        live = new FakeProvider(liveRates());
        fixed = new FixedExchangeRateProvider();
        clock = new MutableClock(Instant.parse("2026-11-23T12:00:00Z"));
        service = new CurrencyService(live, fixed, "live", 60, clock);
    }

    // ---------- Conversions ----------

    @Test
    void convertsUsdToEurWithLiveRates() {
        ConversionResponse result = service.convert("USD", "EUR", new BigDecimal("100.00"));

        assertEquals("USD", result.from());
        assertEquals("EUR", result.to());
        assertEquals(new BigDecimal("95.00"), result.convertedAmount());
        assertEquals(new BigDecimal("0.950000"), result.rate());
        assertEquals("live", result.source());
        assertEquals(LocalDate.of(2026, 11, 20), result.rateDate());
        assertNull(result.notice());
    }

    @Test
    void convertsBetweenTwoNonUsdCurrencies() {
        // 100 EUR -> USD = 100 / 0.95, then -> JPY = * 150  = 15789.47... -> 15789 (no decimals for yen)
        ConversionResponse result = service.convert("EUR", "JPY", new BigDecimal("100"));

        assertEquals(new BigDecimal("15789"), result.convertedAmount());
        assertEquals(0, result.convertedAmount().scale());
    }

    @Test
    void roundsKrwToWholeWonAndUsdToCents() {
        assertEquals(new BigDecimal("13500"),
            service.convert("USD", "KRW", new BigDecimal("10.00")).convertedAmount());

        // 1000 KRW / 1350 = 0.7407... -> 0.74 USD
        assertEquals(new BigDecimal("0.74"),
            service.convert("KRW", "USD", new BigDecimal("1000")).convertedAmount());
    }

    @Test
    void acceptsLowercaseAndSpacesInCodes() {
        ConversionResponse result = service.convert(" usd ", "gbp", new BigDecimal("10"));

        assertEquals("USD", result.from());
        assertEquals("GBP", result.to());
        assertEquals(new BigDecimal("7.80"), result.convertedAmount());
    }

    @Test
    void sameCurrencyNeedsNoRatesAndKeepsTheAmount() {
        ConversionResponse result = service.convert("CAD", "CAD", new BigDecimal("42.5"));

        assertEquals(new BigDecimal("42.50"), result.convertedAmount());
        assertEquals(0, BigDecimal.ONE.compareTo(result.rate()));
        assertEquals(0, live.calls, "no API call is needed for the same currency");
    }

    @Test
    void zeroConvertsToZero() {
        assertEquals(new BigDecimal("0.00"),
            service.convert("USD", "EUR", BigDecimal.ZERO).convertedAmount());
    }

    // ---------- Validation ----------

    @Test
    void rejectsUnsupportedCurrency() {
        UnsupportedCurrencyException e = assertThrows(UnsupportedCurrencyException.class,
            () -> service.convert("USD", "MXN", new BigDecimal("10")));

        assertTrue(e.getMessage().contains("MXN"));
        assertTrue(e.getMessage().contains("KRW"), "message lists the supported codes");
    }

    @Test
    void rejectsMissingCurrency() {
        assertThrows(UnsupportedCurrencyException.class,
            () -> service.convert(null, "USD", new BigDecimal("10")));
        assertThrows(UnsupportedCurrencyException.class,
            () -> service.convert("USD", "  ", new BigDecimal("10")));
    }

    @Test
    void rejectsBadAmounts() {
        assertThrows(IllegalArgumentException.class,
            () -> service.convert("USD", "EUR", null));
        assertThrows(IllegalArgumentException.class,
            () -> service.convert("USD", "EUR", new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,
            () -> service.convert("USD", "EUR", new BigDecimal("1.005")));
        assertThrows(IllegalArgumentException.class,
            () -> service.convert("USD", "EUR", new BigDecimal("10000000000")));
    }

    @Test
    void trailingZerosDoNotCountAsExtraDecimals() {
        // "5.500" is really 5.5, so it's allowed
        assertEquals(new BigDecimal("5.23"),
            service.convert("USD", "EUR", new BigDecimal("5.500")).convertedAmount());
    }

    // ---------- Fallback and cache ----------

    @Test
    void usesFixedDemoRatesWhenLiveApiFails() {
        live.failing = true;

        ConversionResponse result = service.convert("USD", "EUR", new BigDecimal("100"));

        assertEquals("fallback", result.source());
        assertEquals(CurrencyService.FALLBACK_NOTICE, result.notice());
        assertEquals(new BigDecimal("92.00"), result.convertedAmount());   // demo rate 0.92
        assertNull(result.rateDate());
    }

    @Test
    void fixedModeNeverCallsTheLiveApi() {
        CurrencyService fixedOnly = new CurrencyService(live, fixed, "fixed", 60, clock);

        ConversionResponse result = fixedOnly.convert("USD", "INR", new BigDecimal("1"));

        assertEquals("fallback", result.source());
        assertEquals(new BigDecimal("83.00"), result.convertedAmount());
        assertEquals(0, live.calls);
    }

    @Test
    void cachesLiveRatesUntilTheyExpire() {
        service.convert("USD", "EUR", new BigDecimal("1"));
        service.convert("USD", "GBP", new BigDecimal("1"));
        assertEquals(1, live.calls, "second conversion uses the cached rates");

        clock.advance(Duration.ofMinutes(61));
        service.convert("USD", "EUR", new BigDecimal("1"));
        assertEquals(2, live.calls, "rates are loaded again after the cache expires");
    }

    @Test
    void keepsOlderLiveRatesIfTheApiFailsLater() {
        service.convert("USD", "EUR", new BigDecimal("1"));   // loads live rates

        live.failing = true;
        clock.advance(Duration.ofMinutes(61));
        ConversionResponse result = service.convert("USD", "EUR", new BigDecimal("100"));

        assertEquals("live", result.source(), "older live rates beat demo rates");
        assertEquals(new BigDecimal("95.00"), result.convertedAmount());
    }

    @Test
    void waitsBeforeRetryingTheLiveApiAfterAFailure() {
        live.failing = true;
        service.convert("USD", "EUR", new BigDecimal("1"));
        service.convert("USD", "EUR", new BigDecimal("1"));
        assertEquals(1, live.calls, "doesn't call the failing API on every request");

        live.failing = false;
        clock.advance(CurrencyService.RETRY_AFTER_FAILURE.plusSeconds(1));
        assertEquals("live", service.convert("USD", "EUR", new BigDecimal("1")).source());
        assertEquals(2, live.calls);
    }

    // ---------- Supported currencies ----------

    @Test
    void listsAllEightCurrencies() {
        assertEquals(8, service.listCurrencies().size());
        assertEquals(0, service.listCurrencies().stream()
            .filter(c -> c.code().equals("JPY")).findFirst().orElseThrow().decimalPlaces());
    }

    // ---------- Test helpers ----------

    // Live-style rates: 1 USD = 0.95 EUR, 0.78 GBP, 150 JPY, ...
    private static ExchangeRates liveRates() {
        Map<SupportedCurrency, BigDecimal> rates = new EnumMap<>(SupportedCurrency.class);
        rates.put(SupportedCurrency.USD, new BigDecimal("1"));
        rates.put(SupportedCurrency.EUR, new BigDecimal("0.95"));
        rates.put(SupportedCurrency.GBP, new BigDecimal("0.78"));
        rates.put(SupportedCurrency.JPY, new BigDecimal("150"));
        rates.put(SupportedCurrency.CAD, new BigDecimal("1.40"));
        rates.put(SupportedCurrency.AUD, new BigDecimal("1.55"));
        rates.put(SupportedCurrency.INR, new BigDecimal("84.50"));
        rates.put(SupportedCurrency.KRW, new BigDecimal("1350"));
        return new ExchangeRates(rates, LocalDate.of(2026, 11, 20), ExchangeRates.LIVE);
    }

    // A pretend live provider that counts calls and can be told to fail
    private static class FakeProvider implements ExchangeRateProvider {
        private final ExchangeRates rates;
        int calls = 0;
        boolean failing = false;

        FakeProvider(ExchangeRates rates) {
            this.rates = rates;
        }

        @Override
        public ExchangeRates fetchRates() {
            calls++;
            if (failing) {
                throw new ExchangeRateException("API is down");
            }
            return rates;
        }
    }

    // A clock the test can move forward to check the cache
    private static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
