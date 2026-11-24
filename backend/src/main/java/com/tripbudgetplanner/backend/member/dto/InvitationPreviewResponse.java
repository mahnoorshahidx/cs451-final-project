package com.tripbudgetplanner.backend.member.dto;

import java.time.Instant;

// Public preview of an invitation link (no login needed).
// Kept minimal on purpose: no dates, money, emails, IDs or other personal details.
// invitedBy is the inviter's first name only.
// emailInvite tells the page that the user must sign in with the invited email.
public record InvitationPreviewResponse(
    String tripName,
    String destination,
    String invitedBy,
    String role,
    Instant expiresAt,
    boolean emailInvite
) {}
