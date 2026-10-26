package com.tripbudgetplanner.backend.notification;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

@Component
public class NotificationScheduler {

    private final NotificationService notificationService;

    public NotificationScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // Generate reminders when the backend starts
    @PostConstruct
    public void generateRemindersOnStartup() {
        notificationService.generateTripReminders();
    }

    // Generate reminders every day at 9 AM
    @Scheduled(cron = "0 0 9 * * *", zone = "America/Chicago")
    public void generateDailyReminders() {
        notificationService.generateTripReminders();
    }
}
