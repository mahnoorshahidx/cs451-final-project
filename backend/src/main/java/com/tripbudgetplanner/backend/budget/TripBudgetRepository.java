package com.tripbudgetplanner.backend.budget;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tripbudgetplanner.backend.trip.Trip;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface TripBudgetRepository extends JpaRepository<TripBudget, Long> {

    Optional<TripBudget> findByTripId(Long tripId);

    // Locks the trip's row (SELECT ... FOR UPDATE) until the transaction ends (UC6).
    // Every budget change and every "add search result" takes this lock first, so two
    // trip members changing the same trip at once wait for each other instead of
    // overwriting each other's changes. Works even when the trip has no budget yet.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :tripId")
    Optional<Trip> lockTrip(@Param("tripId") Long tripId);

    // The trip's currency read straight from the database (fresh, after taking the lock)
    @Query("select t.currency from Trip t where t.id = :tripId")
    Optional<String> findTripCurrency(@Param("tripId") Long tripId);
}
