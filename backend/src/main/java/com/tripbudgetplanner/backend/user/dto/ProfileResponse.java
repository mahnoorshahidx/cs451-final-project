
package com.tripbudgetplanner.backend.user.dto;

// User profile and saved preferences
public record ProfileResponse(
    Long id,
    String name,
    String email,
    boolean tripRemindersEnabled,
    String currency,
    String dateFormat,
    String appearance
) {}