package com.tripbudgetplanner.backend.member.dto;

import java.time.Instant;
import java.time.LocalDate;

// A pending email invitation as the invited (signed-in) user sees it.
public record ReceivedInvitationResponse(
    Long id,
    Long tripId,
    String tripName,
    String destination,
    LocalDate startDate,
    LocalDate endDate,
    String role,
    String invitedBy,
    Instant expiresAt
) {}
