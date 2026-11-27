
package com.tripbudgetplanner.backend.itinerary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Handles database operations for itinerary activities.
public interface ItineraryActivityRepository
        extends JpaRepository<ItineraryActivity, Long> {

    // Find all activities for a trip, ordered by date and time.
    List<ItineraryActivity>
        findByTripIdOrderByActivityDateAscStartTimeAsc(
            Long tripId
        );

    // Activities on the days from..to (inclusive), used when regenerating part of an itinerary
    List<ItineraryActivity> findByTripIdAndActivityDateBetween(
            Long tripId, LocalDate from, LocalDate to);

    // UC6: has this search result already been added to the trip?
    boolean existsByTripIdAndSourceProviderAndSourceItemId(Long tripId, String sourceProvider, String sourceItemId);

    // UC6: IDs of the search results already in a trip's itinerary (for "alreadyAdded")
    @Query("select distinct a.sourceItemId from ItineraryActivity a"
        + " where a.trip.id = :tripId and a.sourceProvider = :provider")
    List<String> findSourceItemIds(@Param("tripId") Long tripId, @Param("provider") String provider);

    // UC6: does any activity have a cost? (used by the trip currency protection)
    boolean existsByTripIdAndEstimatedCostIsNotNull(Long tripId);

    // Deletes all of a trip's activities, including search results (used when the trip is deleted, UC12)
    void deleteByTripId(Long tripId);

    // Find a specific activity belonging to a specific trip.
    Optional<ItineraryActivity> findByIdAndTripId(
        Long id,
        Long tripId
    );
}
