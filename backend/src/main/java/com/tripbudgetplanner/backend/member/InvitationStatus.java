package com.tripbudgetplanner.backend.member;

// PENDING can be accepted, declined, revoked or resent.
// The other statuses are final, except that EXPIRED invitations can be resent.
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    REVOKED,
    EXPIRED
}
