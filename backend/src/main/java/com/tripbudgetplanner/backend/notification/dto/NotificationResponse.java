package com.tripbudgetplanner.backend.notification.dto;

import java.time.LocalDateTime;

// Information sent to the frontend for each notification
public record NotificationResponse(
    Long id,
    String type,
    String message,
    boolean isRead,
    LocalDateTime createdAt
) {}
