package com.tripbudgetplanner.backend.notification;

import java.time.LocalDateTime;

import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.user.User;

import jakarta.persistence.*;

// Stores notifications for users
@Entity
@Table(
    name = "notifications",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"trip_id", "type"}
    )
)
public class Notification {

    // Unique notification ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // User receiving the notification
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Trip associated with the reminder
    @ManyToOne
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    // Type of notification
    @Column(nullable = false)
    private String type;

    // Notification message
    @Column(nullable = false)
    private String message;

    // Whether the user has read it
    @Column(nullable = false)
    private boolean isRead = false;

    // When the notification was created
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Required by JPA
    public Notification() {}

    // Getters and setters
    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Trip getTrip() { return trip; }
    public void setTrip(Trip trip) { this.trip = trip; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}

