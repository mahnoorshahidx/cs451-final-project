package com.tripbudgetplanner.backend.trip;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbudgetplanner.backend.savings.SavingsContributionRepository;
import com.tripbudgetplanner.backend.savings.SavingsGoalRepository;
import com.tripbudgetplanner.backend.trip.dto.CreateTripRequest;
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

    // Connects the service to the trip, user and savings repositories
    public TripService(TripRepository tripRepository,
                       UserRepository userRepository,
                       SavingsGoalRepository savingsGoalRepository,
                       SavingsContributionRepository savingsContributionRepository) {
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
        this.savingsGoalRepository = savingsGoalRepository;
        this.savingsContributionRepository = savingsContributionRepository;
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

        // Create the trip using the submitted information
        Trip trip = new Trip();
        trip.setName(request.getName());
        trip.setDestination(request.getDestination());
        trip.setStartDate(request.getStartDate());
        trip.setEndDate(request.getEndDate());
        trip.setNumberOfTravelers(request.getNumberOfTravelers());
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

        Trip trip = getTrip(tripId, userId);
        trip.setName(request.getName());
        trip.setDestination(request.getDestination());
        trip.setStartDate(request.getStartDate());
        trip.setEndDate(request.getEndDate());
        trip.setNumberOfTravelers(request.getNumberOfTravelers());
        return tripRepository.save(trip);
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