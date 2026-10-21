package com.tripbudgetplanner.backend.trip.exception;

public class TripNotFoundException extends RuntimeException {
    public TripNotFoundException() {
        super("Trip not found");
    }
}