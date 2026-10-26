package com.tripbudgetplanner.backend.notification;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

// Handles database operations for notifications
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Gets a user's notifications, newest first
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Finds a notification only if it belongs to the user
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    // Checks whether a reminder already exists for a trip
    boolean existsByTripIdAndType(Long tripId, String type);
}
