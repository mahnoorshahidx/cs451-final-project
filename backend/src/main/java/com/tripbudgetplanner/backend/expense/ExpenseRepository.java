package com.tripbudgetplanner.backend.expense;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    // Get all expenses for a trip, newest first.
    List<Expense> findByTripIdOrderByExpenseDateDescIdDesc(Long tripId);

    // Find a specific expense belonging to a trip.
    Optional<Expense> findByIdAndTripId(Long id, Long tripId);
}
