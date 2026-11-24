package com.tripbudgetplanner.backend.member.exception;

// 410: the invitation can no longer be used (expired, revoked, declined or already accepted)
public class InvitationGoneException extends RuntimeException {

    public InvitationGoneException(String message) {
        super(message);
    }
}
