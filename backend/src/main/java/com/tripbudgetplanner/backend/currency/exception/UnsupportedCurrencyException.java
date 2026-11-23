package com.tripbudgetplanner.backend.currency.exception;

// Thrown when a currency code is missing or not one of the 8 supported currencies.
// It extends IllegalArgumentException, so the existing GlobalExceptionHandler
// already turns it into a 400 response with { "message": "..." }.
public class UnsupportedCurrencyException extends IllegalArgumentException {

    public UnsupportedCurrencyException(String message) {
        super(message);
    }
}
