package com.tripbudgetplanner.backend.trip;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

// Handles database operations for trips
public interface TripRepository extends JpaRepository<Trip, Long> {

    // Dashboard list, soonest trip first
    List<Trip> findByUserIdOrderByStartDateAsc(Long userId);

    // Only finds the trip if it belongs to this user (the ownership check)
    Optional<Trip> findByIdAndUserId(Long id, Long userId);
}
