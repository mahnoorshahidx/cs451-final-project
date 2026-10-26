package com.tripbudgetplanner.backend.user;

// Request for updating trip reminder preferences
public record NotificationPreferenceRequest(
    boolean tripRemindersEnabled
) {}
