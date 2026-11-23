package com.tripbudgetplanner.backend.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tripbudgetplanner.backend.currency.exception.UnsupportedCurrencyException;

class SupportedCurrencyTest {

    @Test
    void supportsExactlyTheEightRequiredCurrencies() {
        assertEquals(List.of("USD", "EUR", "GBP", "JPY", "CAD", "AUD", "INR", "KRW"), SupportedCurrency.codes());
    }

    @Test
    void yenAndWonHaveNoDecimals() {
        assertEquals(0, SupportedCurrency.JPY.getDecimalPlaces());
        assertEquals(0, SupportedCurrency.KRW.getDecimalPlaces());
        assertEquals(2, SupportedCurrency.USD.getDecimalPlaces());
    }

    @Test
    void fromCodeIgnoresCaseAndSpaces() {
        assertEquals(SupportedCurrency.AUD, SupportedCurrency.fromCode(" aud "));
    }

    @Test
    void fromCodeRejectsUnknownAndBlankCodes() {
        assertThrows(UnsupportedCurrencyException.class, () -> SupportedCurrency.fromCode("MAD"));
        assertThrows(UnsupportedCurrencyException.class, () -> SupportedCurrency.fromCode(""));
        assertThrows(UnsupportedCurrencyException.class, () -> SupportedCurrency.fromCode(null));
    }

    @Test
    void isSupportedChecksTheCode() {
        assertTrue(SupportedCurrency.isSupported("inr"));
        assertFalse(SupportedCurrency.isSupported("XYZ"));
        assertFalse(SupportedCurrency.isSupported(null));
    }

    @Test
    void unsupportedCurrencyIsA400Error() {
        // GlobalExceptionHandler maps IllegalArgumentException to 400
        assertTrue(IllegalArgumentException.class.isAssignableFrom(UnsupportedCurrencyException.class));
    }

    @Test
    void fixedDemoRatesCoverEveryCurrency() {
        ExchangeRates rates = new FixedExchangeRateProvider().fetchRates();

        for (SupportedCurrency currency : SupportedCurrency.values()) {
            assertTrue(rates.rateFor(currency).signum() > 0, currency + " has a positive rate");
        }
        assertEquals(0, BigDecimal.ONE.compareTo(rates.rateFor(SupportedCurrency.USD)));
        assertEquals("fallback", rates.source());
        assertFalse(rates.isLive());
    }
}
