package com.tripbudgetplanner.backend.member;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TripMemberRepository extends JpaRepository<TripMember, Long> {

    // The user's membership on one trip, if any
    Optional<TripMember> findByTripIdAndUserId(Long tripId, Long userId);

    boolean existsByTripIdAndUserId(Long tripId, Long userId);

    // Everyone who joined a trip, oldest first
    List<TripMember> findByTripIdOrderByJoinedAtAsc(Long tripId);

    // Trips shared with a user, newest first
    List<TripMember> findByUserIdOrderByJoinedAtDesc(Long userId);

    // Used when a trip is deleted
    void deleteByTripId(Long tripId);
}
