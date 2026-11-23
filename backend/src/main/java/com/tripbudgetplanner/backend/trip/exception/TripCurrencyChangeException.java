package com.tripbudgetplanner.backend.trip.exception;

// Thrown when someone tries to change a trip's currency after money has already been
// recorded for it (budget, expenses or savings). Changing only the label would make
// those amounts wrong, because they are not converted.
// GlobalExceptionHandler turns this into a 409 Conflict with { "message": "..." }.
public class TripCurrencyChangeException extends RuntimeException {

    public TripCurrencyChangeException(String message) {
        super(message);
    }
}
