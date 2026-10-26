package com.tripbudgetplanner.backend.notification;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbudgetplanner.backend.notification.dto.NotificationResponse;
import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.trip.TripRepository;

// Handles trip reminders and notifications
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TripRepository tripRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            TripRepository tripRepository) {
        this.notificationRepository = notificationRepository;
        this.tripRepository = tripRepository;
    }

    // Creates reminders for trips starting in 7 days
    @Transactional
    public void generateTripReminders() {

        LocalDate reminderDate = LocalDate.now().plusDays(7);

        // Find upcoming trips
        List<Trip> upcomingTrips = tripRepository.findByStartDate(reminderDate);

        for (Trip trip : upcomingTrips) {

            // Skip users who disabled trip reminders
            if (!trip.getUser().isTripRemindersEnabled()) {
                continue;
            }

            String type = "TRIP_REMINDER_7_DAYS";

            // Prevent duplicate reminders
            if (notificationRepository.existsByTripIdAndType(trip.getId(), type)) {
                continue;
            }

            // Create and save notification
            Notification notification = new Notification();
            notification.setUser(trip.getUser());
            notification.setTrip(trip);
            notification.setType(type);
            notification.setMessage(
                "Your trip to " + trip.getDestination() + " starts in 7 days!"
            );

            notificationRepository.save(notification);
        }
    }

    // Gets the user's notifications
    @Transactional(readOnly = true)
    public List<NotificationResponse> getUserNotifications(Long userId) {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Marks a notification as read
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {

        Notification notification = notificationRepository
                .findByIdAndUserId(notificationId, userId)
                .orElseThrow(() ->
                    new IllegalArgumentException("Notification not found")
                );

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    // Converts a notification into a frontend response
    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getType(),
            notification.getMessage(),
            notification.isRead(),
            notification.getCreatedAt()
        );
    }
}
