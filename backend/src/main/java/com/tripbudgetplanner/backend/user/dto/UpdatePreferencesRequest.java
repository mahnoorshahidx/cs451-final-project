package com.tripbudgetplanner.backend.user.dto;

// Receives the user's selected preferences
public record UpdatePreferencesRequest(
    String currency,
    String dateFormat,
    String appearance
) {}