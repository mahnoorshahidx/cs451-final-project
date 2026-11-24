package com.tripbudgetplanner.backend.member;

import java.time.Instant;

import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.user.User;

import jakarta.persistence.*;

// A user who joined someone else's trip as an EDITOR or VIEWER (table: trip_members).
// The trip owner is not stored here; the owner is always trips.user_id.
@Entity
@Table(name = "trip_members")
public class TripMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // EDITOR or VIEWER
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TripRole role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    // Who sent the invitation (null if that user no longer exists)
    @Column(name = "invited_by_user_id")
    private Long invitedByUserId;

    // Required by JPA
    protected TripMember() {}

    public TripMember(Trip trip, User user, TripRole role, Instant joinedAt, Long invitedByUserId) {
        this.trip = trip;
        this.user = user;
        this.role = role;
        this.joinedAt = joinedAt;
        this.invitedByUserId = invitedByUserId;
    }

    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public User getUser() { return user; }
    public TripRole getRole() { return role; }
    public void setRole(TripRole role) { this.role = role; }
    public Instant getJoinedAt() { return joinedAt; }
    public Long getInvitedByUserId() { return invitedByUserId; }
}
