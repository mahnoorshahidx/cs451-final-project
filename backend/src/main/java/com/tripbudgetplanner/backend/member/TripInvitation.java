package com.tripbudgetplanner.backend.member;

import java.time.Instant;

import com.tripbudgetplanner.backend.trip.Trip;

import jakarta.persistence.*;

// An invitation to join a trip (table: trip_invitations).
// invitedEmail set  -> email invitation: only the user with that email can accept it.
// invitedEmail null -> shareable link: any signed-in user with the link can accept it, once.
// Only a SHA-256 hash of the token is stored, never the token itself.
@Entity
@Table(name = "trip_invitations")
public class TripInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64, columnDefinition = "CHAR(64)")
    private String tokenHash;

    // Stored trimmed and lowercase, like user emails
    @Column(name = "invited_email")
    private String invitedEmail;

    // EDITOR or VIEWER
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TripRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "invited_by_user_id", nullable = false)
    private Long invitedByUserId;

    @Column(name = "accepted_by_user_id")
    private Long acceptedByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // When the invitation was last created or resent (for the resend cooldown)
    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // When it was accepted, declined or revoked
    @Column(name = "responded_at")
    private Instant respondedAt;

    // Required by JPA
    protected TripInvitation() {}

    public TripInvitation(Trip trip, String invitedEmail, TripRole role, Long invitedByUserId) {
        this.trip = trip;
        this.invitedEmail = invitedEmail;
        this.role = role;
        this.invitedByUserId = invitedByUserId;
    }

    // True for email invitations, false for shareable links
    public boolean isEmailInvite() {
        return invitedEmail != null;
    }

    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getInvitedEmail() { return invitedEmail; }
    public TripRole getRole() { return role; }
    public InvitationStatus getStatus() { return status; }
    public void setStatus(InvitationStatus status) { this.status = status; }
    public Long getInvitedByUserId() { return invitedByUserId; }
    public Long getAcceptedByUserId() { return acceptedByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastSentAt() { return lastSentAt; }
    public void setLastSentAt(Instant lastSentAt) { this.lastSentAt = lastSentAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }
}
