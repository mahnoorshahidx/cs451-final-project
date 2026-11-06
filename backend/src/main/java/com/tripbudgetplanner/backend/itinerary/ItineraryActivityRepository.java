
package com.tripbudgetplanner.backend.itinerary;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

// Handles database operations for itinerary activities.
public interface ItineraryActivityRepository
        extends JpaRepository<ItineraryActivity, Long> {

    // Find all activities for a trip, ordered by date and time.
    List<ItineraryActivity>
        findByTripIdOrderByActivityDateAscStartTimeAsc(
            Long tripId
        );

    // Find a specific activity belonging to a specific trip.
    Optional<ItineraryActivity> findByIdAndTripId(
        Long id,
        Long tripId
    );
}
