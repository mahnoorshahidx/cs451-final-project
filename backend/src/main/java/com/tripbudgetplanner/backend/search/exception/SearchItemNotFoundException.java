package com.tripbudgetplanner.backend.search.exception;

// 404: the search result ID is unknown, malformed or no longer available
public class SearchItemNotFoundException extends RuntimeException {

    public SearchItemNotFoundException(String message) {
        super(message);
    }
}
