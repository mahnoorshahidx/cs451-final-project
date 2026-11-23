package com.tripbudgetplanner.backend.currency;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripbudgetplanner.backend.currency.dto.ConversionResponse;
import com.tripbudgetplanner.backend.currency.dto.CurrencyResponse;

// Currency endpoints. Like the rest of /api/**, they require login.
@RestController
@RequestMapping("/api/currencies")
public class CurrencyController {

    private final CurrencyService currencyService;

    public CurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    // GET /api/currencies -> the 8 supported currencies
    @GetMapping
    public List<CurrencyResponse> list() {
        return currencyService.listCurrencies();
    }

    // GET /api/currencies/convert?from=USD&to=JPY&amount=125.50
    // The parameters are read as text and checked here, so every mistake
    // (missing value, "abc" as the amount, unknown code) gets the same
    // { "message": "..." } 400 response as the rest of the API.
    @GetMapping("/convert")
    public ConversionResponse convert(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String amount) {
        return currencyService.convert(from, to, parseAmount(amount));
    }

    // Turns "125.50" into a BigDecimal, or throws a 400 with a clear message
    static BigDecimal parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            throw new IllegalArgumentException("Amount is required");
        }
        try {
            return new BigDecimal(amount.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Amount must be a number, like 125.50");
        }
    }
}
