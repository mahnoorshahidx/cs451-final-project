package com.tripbudgetplanner.backend.itinerary.options;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryOptionRepository extends JpaRepository<ItineraryOption, Long> {

    List<ItineraryOption> findByTripIdOrderByIdAsc(Long tripId);

    Optional<ItineraryOption> findByIdAndTripId(Long id, Long tripId);
}
