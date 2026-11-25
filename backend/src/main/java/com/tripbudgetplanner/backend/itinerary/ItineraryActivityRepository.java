
package com.tripbudgetplanner.backend.itinerary;

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

    // UC6: has this search result already been added to the trip?
    boolean existsByTripIdAndSourceProviderAndSourceItemId(Long tripId, String sourceProvider, String sourceItemId);

    // UC6: IDs of the search results already in a trip's itinerary (for "alreadyAdded")
    @Query("select distinct a.sourceItemId from ItineraryActivity a"
        + " where a.trip.id = :tripId and a.sourceProvider = :provider")
    List<String> findSourceItemIds(@Param("tripId") Long tripId, @Param("provider") String provider);

    // UC6: does any activity have a cost? (used by the trip currency protection)
    boolean existsByTripIdAndEstimatedCostIsNotNull(Long tripId);

    // Find a specific activity belonging to a specific trip.
    Optional<ItineraryActivity> findByIdAndTripId(
        Long id,
        Long tripId
    );
}
