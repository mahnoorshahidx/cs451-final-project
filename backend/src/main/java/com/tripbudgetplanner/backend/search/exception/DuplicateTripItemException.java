package com.tripbudgetplanner.backend.search.exception;

// 409: this search result is already in the trip's itinerary
public class DuplicateTripItemException extends RuntimeException {

    public DuplicateTripItemException(String message) {
        super(message);
    }
}
