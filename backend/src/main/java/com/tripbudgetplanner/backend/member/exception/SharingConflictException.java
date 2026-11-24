package com.tripbudgetplanner.backend.member.exception;

// 409: the request clashes with what already exists
// (duplicate member or invitation, inviting the owner, changing the owner's role, ...)
public class SharingConflictException extends RuntimeException {

    public SharingConflictException(String message) {
        super(message);
    }
}
