package com.tripbudgetplanner.backend.savings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {

    Optional<SavingsGoal> findByTripId(Long tripId);

    List<SavingsGoal> findByTripIdIn(Collection<Long> tripIds);
}
