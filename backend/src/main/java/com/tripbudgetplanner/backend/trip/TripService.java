package com.tripbudgetplanner.backend.trip;

import com.tripbudgetplanner.backend.trip.dto.CreateTripRequest;
import com.tripbudgetplanner.backend.user.User;
import com.tripbudgetplanner.backend.user.UserRepository;

import org.springframework.stereotype.Service;

// Handles the logic for creating a new trip
@Service
public class TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    // Connects the service to the trip and user repositories
    public TripService(TripRepository tripRepository, UserRepository userRepository) {
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
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
}