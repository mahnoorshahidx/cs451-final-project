package com.tripbudgetplanner.backend.trip.exception;

// Thrown when a user can see a trip but their role doesn't allow the action
// (e.g. a VIEWER trying to edit). GlobalExceptionHandler turns it into a 403.
public class TripAccessDeniedException extends RuntimeException {

    public TripAccessDeniedException(String message) {
        super(message);
    }
}
