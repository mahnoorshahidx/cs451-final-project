package com.tripbudgetplanner.backend.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TripBudgetRepository extends JpaRepository<TripBudget, Long> {

    Optional<TripBudget> findByTripId(Long tripId);
}
