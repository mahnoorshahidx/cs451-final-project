package com.tripbudgetplanner.backend.currency.exception;

// Thrown when exchange rates can't be loaded or are invalid
// (network error, bad HTTP status, missing currency in the reply, ...).
// CurrencyService catches it and switches to the fixed demo rates,
// so users never see this error directly.
public class ExchangeRateException extends RuntimeException {

    public ExchangeRateException(String message) {
        super(message);
    }

    public ExchangeRateException(String message, Throwable cause) {
        super(message, cause);
    }
}
