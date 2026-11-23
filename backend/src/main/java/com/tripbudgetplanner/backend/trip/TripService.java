package com.tripbudgetplanner.backend.trip;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbudgetplanner.backend.budget.TripBudget;
import com.tripbudgetplanner.backend.budget.TripBudgetRepository;
import com.tripbudgetplanner.backend.currency.SupportedCurrency;
import com.tripbudgetplanner.backend.expense.ExpenseRepository;
import com.tripbudgetplanner.backend.savings.SavingsContributionRepository;
import com.tripbudgetplanner.backend.savings.SavingsGoalRepository;
import com.tripbudgetplanner.backend.trip.dto.CreateTripRequest;
import com.tripbudgetplanner.backend.trip.exception.TripCurrencyChangeException;
import com.tripbudgetplanner.backend.trip.exception.TripNotFoundException;
import com.tripbudgetplanner.backend.user.User;
import com.tripbudgetplanner.backend.user.UserRepository;

// Handles the logic for creating, viewing, editing and deleting trips
@Service
public class TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final SavingsGoalRepository savingsGoalRepository;
    private final SavingsContributionRepository savingsContributionRepository;
    private final TripBudgetRepository tripBudgetRepository;
    private final ExpenseRepository expenseRepository;

    // Connects the service to the trip, user, savings, budget and expense repositories.
    // Budget and expense repositories are used to check for money already recorded
    // before a trip's currency can change.
    public TripService(TripRepository tripRepository,
                       UserRepository userRepository,
                       SavingsGoalRepository savingsGoalRepository,
                       SavingsContributionRepository savingsContributionRepository,
                       TripBudgetRepository tripBudgetRepository,
                       ExpenseRepository expenseRepository) {
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
        this.savingsGoalRepository = savingsGoalRepository;
        this.savingsContributionRepository = savingsContributionRepository;
        this.tripBudgetRepository = tripBudgetRepository;
        this.expenseRepository = expenseRepository;
    }

    // Validates the trip information and saves a new trip
    public Trip createTrip(CreateTripRequest request, Long userId) {

        // Make sure the return date is not before the departure date
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException(
                "Return date cannot be before departure date"
            );
        }

        // Find the user creating the trip
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Use the chosen currency, or fall back to the user's preferred currency.
        // Throws a 400 if the code isn't one of the 8 supported currencies.
        String currency = hasText(request.getCurrency())
            ? SupportedCurrency.fromCode(request.getCurrency()).name()
            : defaultCurrency(user);

        // Create the trip using the submitted information
        Trip trip = new Trip();
        trip.setName(request.getName());
        trip.setDestination(request.getDestination());
        trip.setStartDate(request.getStartDate());
        trip.setEndDate(request.getEndDate());
        trip.setNumberOfTravelers(request.getNumberOfTravelers());
        trip.setCurrency(currency);
        trip.setUser(user);

        // Save the trip to the database
        return tripRepository.save(trip);
    }

    // Lists the logged-in user's trips for the dashboard
    public List<Trip> listTrips(Long userId) {
        return tripRepository.findByUserIdOrderByStartDateAsc(userId);
    }

    // Finds one trip, but only if it belongs to this user
    public Trip getTrip(Long tripId, Long userId) {
        return tripRepository.findByIdAndUserId(tripId, userId)
            .orElseThrow(TripNotFoundException::new);
    }

    // Updates the trip's details (UC10)
    public Trip updateTrip(Long tripId, CreateTripRequest request, Long userId) {

        // Make sure the return date is not before the departure date
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException(
                "Return date cannot be before departure date"
            );
        }

        // Check the currency code before changing anything (400 if unsupported)
        String newCurrency = hasText(request.getCurrency())
            ? SupportedCurrency.fromCode(request.getCurrency()).name()
            : null;

        Trip trip = getTrip(tripId, userId);

        // Only a real change needs the money check. An empty currency keeps the
        // current one (the Edit Trip page doesn't send it), and sending the same
        // currency again changes nothing.
        boolean currencyChanges = newCurrency != null && !newCurrency.equals(trip.getCurrency());
        if (currencyChanges) {
            checkCurrencyCanChange(tripId, trip, newCurrency);   // 409 if amounts would become wrong
        }

        trip.setName(request.getName());
        trip.setDestination(request.getDestination());
        trip.setStartDate(request.getStartDate());
        trip.setEndDate(request.getEndDate());
        trip.setNumberOfTravelers(request.getNumberOfTravelers());
        if (currencyChanges) {
            trip.setCurrency(newCurrency);
        }
        return tripRepository.save(trip);
    }

    // A trip's currency can only change while no money has been recorded for it,
    // because we don't convert existing amounts.
    //
    // One safe exception: the trip has a budget but no expenses and no savings goal
    // (so no contributions either), and the new currency is the budget's own currency.
    // The budget is already in that currency, so nothing becomes wrong. This lets trips
    // that were set to USD by the database update match a budget saved in EUR, GBP, etc.
    //
    // A budget saved in a currency we don't support (like MAD) can never match,
    // so those trips keep their current currency. Nothing breaks: we only compare
    // the budget's code, we never try to convert it.
    private void checkCurrencyCanChange(Long tripId, Trip trip, String newCurrency) {
        Optional<TripBudget> budget = tripBudgetRepository.findByTripId(tripId);
        boolean hasExpenses = expenseRepository.existsByTripId(tripId);
        // Contributions can only exist under a savings goal (transactions.savings_goal_id
        // is required), so checking for the goal also covers its contributions.
        boolean hasSavings = savingsGoalRepository.findByTripId(tripId).isPresent();

        boolean matchesBudgetCurrency = budget
            .map(b -> newCurrency.equalsIgnoreCase(b.getCurrency()))
            .orElse(false);
        if (budget.isPresent() && !hasExpenses && !hasSavings && matchesBudgetCurrency) {
            return;   // safe: lines the trip up with its own budget
        }

        // Build a short list of what's already recorded, for the error message
        List<String> recorded = new ArrayList<>();
        budget.ifPresent(b -> recorded.add("a budget in " + b.getCurrency()));
        if (hasExpenses) {
            recorded.add("expenses");
        }
        if (hasSavings) {
            recorded.add("a savings goal");
        }

        if (!recorded.isEmpty()) {
            throw new TripCurrencyChangeException(
                "This trip's currency can't be changed from " + trip.getCurrency() + " to " + newCurrency
                + " because it already has " + String.join(", ", recorded)
                + ". Those amounts aren't converted, so they would be wrong.");
        }
    }

    // The user's preferred currency from Settings if it's supported, otherwise USD
    private static String defaultCurrency(User user) {
        return SupportedCurrency.isSupported(user.getCurrency())
            ? SupportedCurrency.fromCode(user.getCurrency()).name()
            : SupportedCurrency.USD.name();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    // Deletes the trip (UC12)
    @Transactional
    public void deleteTrip(Long tripId, Long userId) {
        Trip trip = getTrip(tripId, userId);

        // The database doesn't cascade savings rows, so remove them first
        savingsGoalRepository.findByTripId(tripId).ifPresent(goal -> {
            savingsContributionRepository.deleteBySavingsGoalId(goal.getId());
            savingsGoalRepository.delete(goal);
        });

        tripRepository.delete(trip);
    }
}
