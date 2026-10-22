
package com.tripbudgetplanner.backend.dashboard;

import com.tripbudgetplanner.backend.bank.BankAccountRepository;
import com.tripbudgetplanner.backend.budget.TripBudgetRepository;
import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.trip.TripRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final TripRepository tripRepository;
    private final TripBudgetRepository budgetRepository;
    private final BankAccountRepository bankAccountRepository;

    public DashboardService(TripRepository tripRepository,
                            TripBudgetRepository budgetRepository,
                            BankAccountRepository bankAccountRepository) {
        this.tripRepository = tripRepository;
        this.budgetRepository = budgetRepository;
        this.bankAccountRepository = bankAccountRepository;
    }

    // Get dashboard statistics for the logged-in user
    public DashboardSummaryResponse getSummary(Long userId) {

        // Get all trips belonging to the user
        List<Trip> trips = tripRepository.findByUserIdOrderByStartDateAsc(userId);

        long totalTrips = trips.size();

        // Count trips that have not ended yet
        long upcomingTrips = trips.stream()
            .filter(trip -> !trip.getEndDate().isBefore(LocalDate.now()))
            .count();

        // Calculate total budgets separately for each currency
        Map<String, BigDecimal> budgetTotals = new HashMap<>();

        for (Trip trip : trips) {
            budgetRepository.findByTripId(trip.getId()).ifPresent(budget -> {
                budgetTotals.merge(
                    budget.getCurrency(),
                    budget.getTotalAmount(),
                    BigDecimal::add
                );
            });
        }

        // Count the user's connected bank accounts
        long connectedBankAccounts =
            bankAccountRepository.findByUserId(userId).size();

        // Return the summary to the frontend
        return new DashboardSummaryResponse(
            totalTrips,
            upcomingTrips,
            budgetTotals,
            connectedBankAccounts
        );
    }
}
