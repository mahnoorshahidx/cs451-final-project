package com.tripbudgetplanner.backend.notification;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbudgetplanner.backend.notification.dto.NotificationResponse;

// Handles notification API requests
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // Gets notifications for the logged-in user
    @GetMapping
    public List<NotificationResponse> getNotifications(
            @AuthenticationPrincipal Long userId) {

        return notificationService.getUserNotifications(userId);
    }

    // Marks a notification as read
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {

        notificationService.markAsRead(id, userId);

        return ResponseEntity.noContent().build();
    }
}
