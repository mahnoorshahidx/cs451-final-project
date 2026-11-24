package com.tripbudgetplanner.backend.member.exception;

// 429: the invitation was sent less than 10 minutes ago, so it can't be resent yet
public class InvitationCooldownException extends RuntimeException {

    private final long retryAfterSeconds;

    public InvitationCooldownException(long retryAfterSeconds) {
        super("This invitation was sent recently. You can resend it in "
            + Math.max(1, (retryAfterSeconds + 59) / 60) + " minute(s).");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
