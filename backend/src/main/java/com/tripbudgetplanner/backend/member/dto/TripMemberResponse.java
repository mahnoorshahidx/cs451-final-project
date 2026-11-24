package com.tripbudgetplanner.backend.member.dto;

import java.time.Instant;

// One person on a trip. email is only filled in for the trip owner's view.
// joinedAt is null for the owner (they created the trip).
public record TripMemberResponse(
    Long userId,
    String name,
    String email,
    String role,
    Instant joinedAt,
    boolean you
) {}
