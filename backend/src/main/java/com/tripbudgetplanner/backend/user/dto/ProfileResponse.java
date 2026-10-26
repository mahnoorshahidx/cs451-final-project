package com.tripbudgetplanner.backend.user.dto;

// User profile information sent to the frontend
public record ProfileResponse(
    Long id,
    String name,
    String email,
    boolean tripRemindersEnabled
) {}
