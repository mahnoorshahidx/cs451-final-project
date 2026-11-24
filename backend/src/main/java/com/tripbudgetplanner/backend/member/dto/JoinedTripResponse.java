package com.tripbudgetplanner.backend.member.dto;

// Returned after accepting an invitation
public record JoinedTripResponse(
    Long tripId,
    String tripName,
    String role
) {}
