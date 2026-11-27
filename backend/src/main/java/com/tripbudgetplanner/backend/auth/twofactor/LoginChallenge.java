package com.tripbudgetplanner.backend.auth.twofactor;

import java.time.Instant;

import jakarta.persistence.*;

// One pending two-factor sign-in (table: login_challenges).
// Created after the password checks out; the token is only issued once the emailed code is verified.
// Only a BCrypt hash of the 6-digit code is stored, never the code itself.
@Entity
@Table(name = "login_challenges")
public class LoginChallenge {

    // Random UUID handed to the browser; it can't be guessed or enumerated
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // Where the code was sent (the user's email at sign-in time)
    @Column(nullable = false)
    private String email;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // When the current code was sent (for the resend cooldown)
    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt;

    // Wrong codes entered for the current code
    @Column(nullable = false)
    private int attempts;

    // Codes sent for this sign-in, the first one included
    @Column(name = "send_count", nullable = false)
    private int sendCount;

    // Set when the code was used, too many wrong codes were entered, or a newer sign-in replaced it
    @Column(name = "closed_at")
    private Instant closedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getLastSentAt() { return lastSentAt; }
    public void setLastSentAt(Instant lastSentAt) { this.lastSentAt = lastSentAt; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public int getSendCount() { return sendCount; }
    public void setSendCount(int sendCount) { this.sendCount = sendCount; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
}
