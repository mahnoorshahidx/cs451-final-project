package com.tripbudgetplanner.backend.member.dto;

import java.time.Instant;

// An invitation as the trip owner sees it.
// type: EMAIL or LINK. email is null for links.
// token and invitePath are only returned right after creating or resending,
// because only a hash of the token is stored. They are null in lists.
public record InvitationResponse(
    Long id,
    String type,
    String email,
    String role,
    String status,
    Instant createdAt,
    Instant lastSentAt,
    Instant expiresAt,
    Instant resendAvailableAt,
    String token,
    String invitePath
) {}
