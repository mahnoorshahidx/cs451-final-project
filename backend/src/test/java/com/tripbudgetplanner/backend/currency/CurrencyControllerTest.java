package com.tripbudgetplanner.backend.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.tripbudgetplanner.backend.currency.dto.ConversionResponse;

class CurrencyControllerTest {

    // Uses the real service with the fixed demo rates, so no network is needed
    private final CurrencyController controller = new CurrencyController(
        new CurrencyService(new FixedExchangeRateProvider(), new FixedExchangeRateProvider(), "fixed", 60));

    @Test
    void convertsWithQueryParameters() {
        ConversionResponse result = controller.convert("USD", "JPY", "12.50");

        assertEquals(new BigDecimal("1875"), result.convertedAmount());   // 12.50 * 150
        assertEquals("fallback", result.source());
    }

    @Test
    void listsTheSupportedCurrencies() {
        assertEquals(8, controller.list().size());
    }

    @Test
    void missingOrNonNumericAmountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> controller.convert("USD", "EUR", null));
        assertThrows(IllegalArgumentException.class, () -> controller.convert("USD", "EUR", "abc"));
    }

    @Test
    void parseAmountReadsDecimals() {
        assertEquals(new BigDecimal("99.99"), CurrencyController.parseAmount(" 99.99 "));
    }
}
