package com.tripbudgetplanner.backend.search.exception;

// 400: the search result belongs to a different destination, dates or group size than this trip
// (for example the trip was edited after searching, or the ID was changed by hand)
public class SearchItemMismatchException extends RuntimeException {

    public SearchItemMismatchException(String message) {
        super(message);
    }
}
