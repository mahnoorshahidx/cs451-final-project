package com.tripbudgetplanner.backend.trip;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbudgetplanner.backend.budget.TripBudget;
import com.tripbudgetplanner.backend.budget.TripBudgetRepository;
import com.tripbudgetplanner.backend.currency.SupportedCurrency;
import com.tripbudgetplanner.backend.expense.ExpenseRepository;
import com.tripbudgetplanner.backend.itinerary.ItineraryActivityRepository;
import com.tripbudgetplanner.backend.member.TripInvitationRepository;
import com.tripbudgetplanner.backend.member.TripMember;
import com.tripbudgetplanner.backend.member.TripMemberRepository;
import com.tripbudgetplanner.backend.savings.SavingsContributionRepository;
import com.tripbudgetplanner.backend.savings.SavingsGoalRepository;
import com.tripbudgetplanner.backend.trip.dto.CreateTripRequest;
import com.tripbudgetplanner.backend.trip.exception.TripCurrencyChangeException;
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
    private final TripAccessService tripAccess;
    private final TripMemberRepository memberRepository;
    private final TripInvitationRepository invitationRepository;
    private final ItineraryActivityRepository activityRepository;

    // Connects the service to the trip, user, savings, budget and expense repositories.
    // Budget and expense repositories are used to check for money already recorded
    // before a trip's currency can change.
    // TripAccessService decides who can view, edit or delete a trip (UC9).
    // ItineraryActivityRepository is used to protect itinerary cost estimates (UC6).
    @Autowired
    public TripService(TripRepository tripRepository,
                       UserRepository userRepository,
                       SavingsGoalRepository savingsGoalRepository,
                       SavingsContributionRepository savingsContributionRepository,
                       TripBudgetRepository tripBudgetRepository,
                       ExpenseRepository expenseRepository,
                       TripAccessService tripAccess,
                       TripMemberRepository memberRepository,
                       TripInvitationRepository invitationRepository,
                       ItineraryActivityRepository activityRepository) {
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
        this.savingsGoalRepository = savingsGoalRepository;
        this.savingsContributionRepository = savingsContributionRepository;
        this.tripBudgetRepository = tripBudgetRepository;
        this.expenseRepository = expenseRepository;
        this.tripAccess = tripAccess;
        this.memberRepository = memberRepository;
        this.invitationRepository = invitationRepository;
        this.activityRepository = activityRepository;
    }

    // The constructor from before UC6, kept for existing tests (no itinerary cost check)
    public TripService(TripRepository tripRepository,
                       UserRepository userRepository,
                       SavingsGoalRepository savingsGoalRepository,
                       SavingsContributionRepository savingsContributionRepository,
                       TripBudgetRepository tripBudgetRepository,
                       ExpenseRepository expenseRepository,
                       TripAccessService tripAccess,
                       TripMemberRepository memberRepository,
                       TripInvitationRepository invitationRepository) {
        this(tripRepository, userRepository, savingsGoalRepository, savingsContributionRepository,
            tripBudgetRepository, expenseRepository, tripAccess, memberRepository, invitationRepository, null);
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

    // Trips other people shared with this user (UC9), newest first
    public List<TripMember> listSharedTrips(Long userId) {
        return memberRepository.findByUserIdOrderByJoinedAtDesc(userId);
    }

    // Finds one trip the user can view: their own, or one shared with them.
    // 404 if they have no access.
    public Trip getTrip(Long tripId, Long userId) {
        return tripAccess.viewableTrip(tripId, userId);
    }

    // Updates the trip's details (UC10)
    @Transactional
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

        // Owner or editor (403 for viewers)
        Trip trip = tripAccess.editableTrip(tripId, userId);

        // Only a real change needs the money check. An empty currency keeps the
        // current one (the Edit Trip page doesn't send it), and sending the same
        // currency again changes nothing.
        boolean currencyChanges = newCurrency != null && !newCurrency.equals(trip.getCurrency());
        if (currencyChanges) {
            // Wait for any budget or itinerary change in progress on this trip (UC6),
            // so nothing is added in the old currency while we check
            tripBudgetRepository.lockTrip(tripId);
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
    // One safe exception: the trip has a budget but no expenses, no savings goal
    // (so no contributions either) and no itinerary costs, and the new currency is the budget's own.
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
        // UC6: itinerary cost estimates are stored in the trip's currency
        boolean hasItineraryCosts = activityRepository != null
            && activityRepository.existsByTripIdAndEstimatedCostIsNotNull(tripId);

        boolean matchesBudgetCurrency = budget
            .map(b -> newCurrency.equalsIgnoreCase(b.getCurrency()))
            .orElse(false);
        if (budget.isPresent() && !hasExpenses && !hasSavings && !hasItineraryCosts && matchesBudgetCurrency) {
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
        if (hasItineraryCosts) {
            recorded.add("itinerary cost estimates");
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

    // Deletes the trip (UC12). Only the owner can delete (403 for editors and viewers).
    @Transactional
    public void deleteTrip(Long tripId, Long userId) {
        Trip trip = tripAccess.ownedTrip(tripId, userId);

        // Remove members and invitations first. The database also cascades these,
        // but this works even if the tables were created without the cascade.
        memberRepository.deleteByTripId(tripId);
        invitationRepository.deleteByTripId(tripId);

        // The database doesn't cascade savings rows, so remove them first
        savingsGoalRepository.findByTripId(tripId).ifPresent(goal -> {
            savingsContributionRepository.deleteBySavingsGoalId(goal.getId());
            savingsGoalRepository.delete(goal);
        });

        tripRepository.delete(trip);
    }
}
