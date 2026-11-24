package com.tripbudgetplanner.backend.member;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripInvitationRepository extends JpaRepository<TripInvitation, Long> {

    Optional<TripInvitation> findByTokenHash(String tokenHash);

    Optional<TripInvitation> findByIdAndTripId(Long id, Long tripId);

    List<TripInvitation> findByTripIdAndStatusOrderByCreatedAtDesc(Long tripId, InvitationStatus status);

    List<TripInvitation> findByInvitedEmailAndStatusOrderByCreatedAtDesc(String invitedEmail, InvitationStatus status);

    boolean existsByTripIdAndInvitedEmailAndStatus(Long tripId, String invitedEmail, InvitationStatus status);

    // Marks old PENDING invitations for this trip and email as EXPIRED,
    // so they don't block a new invitation (the database allows one PENDING per email per trip)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TripInvitation i set i.status = :expired"
        + " where i.trip.id = :tripId and i.invitedEmail = :email and i.status = :pending and i.expiresAt <= :now")
    int expireOldForEmail(@Param("tripId") Long tripId, @Param("email") String email, @Param("now") Instant now,
                          @Param("pending") InvitationStatus pending, @Param("expired") InvitationStatus expired);

    // Marks a user's old PENDING invitations (all trips) as EXPIRED
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TripInvitation i set i.status = :expired"
        + " where i.invitedEmail = :email and i.status = :pending and i.expiresAt <= :now")
    int expireOldForRecipient(@Param("email") String email, @Param("now") Instant now,
                              @Param("pending") InvitationStatus pending, @Param("expired") InvitationStatus expired);

    // Marks a trip's old PENDING invitations as EXPIRED
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TripInvitation i set i.status = :expired"
        + " where i.trip.id = :tripId and i.status = :pending and i.expiresAt <= :now")
    int expireOldForTrip(@Param("tripId") Long tripId, @Param("now") Instant now,
                         @Param("pending") InvitationStatus pending, @Param("expired") InvitationStatus expired);

    // Accepts the invitation only if it is still PENDING and not expired.
    // This is one atomic UPDATE: if two users accept at the same moment, the database lets
    // only one of them change the row, and the other gets 0 (so it can't be used twice).
    @Modifying(flushAutomatically = true)
    @Query("update TripInvitation i set i.status = :accepted, i.acceptedByUserId = :userId, i.respondedAt = :now"
        + " where i.id = :id and i.status = :pending and i.expiresAt > :now")
    int markAcceptedIfPending(@Param("id") Long id, @Param("userId") Long userId, @Param("now") Instant now,
                              @Param("pending") InvitationStatus pending, @Param("accepted") InvitationStatus accepted);

    // Used when a trip is deleted
    void deleteByTripId(Long tripId);
}
