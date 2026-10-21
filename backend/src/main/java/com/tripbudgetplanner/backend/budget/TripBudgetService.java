package com.tripbudgetplanner.backend.budget;

import com.tripbudgetplanner.backend.budget.dto.BudgetRequest;
import com.tripbudgetplanner.backend.trip.TripService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class TripBudgetService {

    private final TripBudgetRepository budgetRepository;
    private final TripService tripService;

    public TripBudgetService(TripBudgetRepository budgetRepository, TripService tripService) {
        this.budgetRepository = budgetRepository;
        this.tripService = tripService;
    }

    // Returns the trip's budget, or empty if none is saved yet.
    // getTrip throws TripNotFoundException (404) if the trip isn't this user's.
    @Transactional(readOnly = true)
    public Optional<TripBudget> getBudget(Long tripId, Long userId) {
        tripService.getTrip(tripId, userId);
        return budgetRepository.findByTripId(tripId);
    }

    // Creates the budget, or replaces it if it already exists
    @Transactional
    public TripBudget saveBudget(Long tripId, BudgetRequest request, Long userId) {
        tripService.getTrip(tripId, userId);

        BigDecimal allocated = request.allocations().values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.compareTo(request.totalBudget()) > 0) {
            throw new IllegalArgumentException("Your allocations exceed your total budget");
        }

        TripBudget budget = budgetRepository.findByTripId(tripId)
            .orElseGet(() -> new TripBudget(tripId));

        budget.setTotalAmount(request.totalBudget());
        budget.setCurrency(request.currency());
        budget.getAllocations().clear();
        request.allocations().forEach((category, amount) -> {
            if (amount.signum() > 0) budget.getAllocations().put(category.trim(), amount);
        });

        return budgetRepository.save(budget);
    }

    public static BigDecimal totalAllocated(TripBudget budget) {
        return budget.getAllocations().values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
