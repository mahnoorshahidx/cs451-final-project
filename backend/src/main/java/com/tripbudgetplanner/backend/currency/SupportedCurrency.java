package com.tripbudgetplanner.backend.currency;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.tripbudgetplanner.backend.currency.exception.UnsupportedCurrencyException;

// The 8 currencies the app supports for trips and conversions.
// decimalPlaces is how many digits after the decimal point the currency uses
// (Japanese yen and Korean won have no cents).
public enum SupportedCurrency {

    USD("US Dollar", "$", 2),
    EUR("Euro", "€", 2),
    GBP("British Pound", "£", 2),
    JPY("Japanese Yen", "¥", 0),
    CAD("Canadian Dollar", "CA$", 2),
    AUD("Australian Dollar", "A$", 2),
    INR("Indian Rupee", "₹", 2),
    KRW("South Korean Won", "₩", 0);

    private final String displayName;
    private final String symbol;
    private final int decimalPlaces;

    SupportedCurrency(String displayName, String symbol, int decimalPlaces) {
        this.displayName = displayName;
        this.symbol = symbol;
        this.decimalPlaces = decimalPlaces;
    }

    public String getDisplayName() { return displayName; }
    public String getSymbol() { return symbol; }
    public int getDecimalPlaces() { return decimalPlaces; }

    // Turns a code like "usd" or " EUR " into a SupportedCurrency.
    // Throws UnsupportedCurrencyException (400) for blank or unknown codes.
    public static SupportedCurrency fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new UnsupportedCurrencyException("Currency is required. Supported currencies: " + codesAsText());
        }
        String clean = code.trim().toUpperCase(Locale.ROOT);
        for (SupportedCurrency currency : values()) {
            if (currency.name().equals(clean)) {
                return currency;
            }
        }
        throw new UnsupportedCurrencyException(
            "Unsupported currency '" + code.trim() + "'. Supported currencies: " + codesAsText());
    }

    // True if the code is one of the 8 supported currencies
    public static boolean isSupported(String code) {
        if (code == null) {
            return false;
        }
        String clean = code.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).anyMatch(c -> c.name().equals(clean));
    }

    // ["USD", "EUR", ...]
    public static List<String> codes() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }

    // "USD, EUR, ..." for error messages
    private static String codesAsText() {
        return String.join(", ", codes());
    }
}
