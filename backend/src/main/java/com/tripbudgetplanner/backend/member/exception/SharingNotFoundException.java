package com.tripbudgetplanner.backend.member.exception;

// 404: the member or invitation doesn't exist (or isn't visible to this user)
public class SharingNotFoundException extends RuntimeException {

    public SharingNotFoundException(String message) {
        super(message);
    }
}
