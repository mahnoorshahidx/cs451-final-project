package com.tripbudgetplanner.backend.trip;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.tripbudgetplanner.backend.trip.dto.CreateTripRequest;
import com.tripbudgetplanner.backend.user.User;
import com.tripbudgetplanner.backend.user.UserRepository;

public class TripServiceTest {

	@Test
	void createTripSuccessfully() {

		// Create mock repositories so we don't need a real database
		TripRepository tripRepository = mock(TripRepository.class);
		UserRepository userRepository = mock(UserRepository.class);

		TripService tripService = new TripService(tripRepository, userRepository);

		// Create sample trip information
		CreateTripRequest request = new CreateTripRequest();
		request.setName("Summer Vacation");
		request.setDestination("Hawaii");
		request.setStartDate(LocalDate.of(2027, 6, 1));
		request.setEndDate(LocalDate.of(2027, 6, 10));
		request.setNumberOfTravelers(2);

		// Pretend user 1 exists in the database
		User user = new User();
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));

		// Pretend the database saves the trip
		when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Create the trip
		Trip trip = tripService.createTrip(request, 1L);

		// Check that the trip information is correct
		assertEquals("Summer Vacation", trip.getName());
		assertEquals("Hawaii", trip.getDestination());
		assertEquals(LocalDate.of(2027, 6, 1), trip.getStartDate());
		assertEquals(LocalDate.of(2027, 6, 10), trip.getEndDate());
		assertEquals(2, trip.getNumberOfTravelers());
		assertSame(user, trip.getUser());

		// Verify that the trip was saved
		verify(tripRepository).save(any(Trip.class));
	}

	@Test
	void createTripWithInvalidDates() {

		// Create mock repositories
		TripRepository tripRepository = mock(TripRepository.class);
		UserRepository userRepository = mock(UserRepository.class);

		TripService tripService = new TripService(tripRepository, userRepository);

		// Create a trip with a return date before the departure date
		CreateTripRequest request = new CreateTripRequest();
		request.setName("Summer Vacation");
		request.setDestination("Hawaii");
		request.setStartDate(LocalDate.of(2027, 6, 10));
		request.setEndDate(LocalDate.of(2027, 6, 1));
		request.setNumberOfTravelers(2);

		// Check that the invalid dates are rejected
		assertThrows(IllegalArgumentException.class, () -> tripService.createTrip(request, 1L));

		// Make sure the invalid trip was never saved
		verify(tripRepository, never()).save(any(Trip.class));
	}

	@Test
	void createTripWithInvalidUser() {

		// Create mock repositories
		TripRepository tripRepository = mock(TripRepository.class);
		UserRepository userRepository = mock(UserRepository.class);

		TripService tripService = new TripService(tripRepository, userRepository);

		// Create sample trip information
		CreateTripRequest request = new CreateTripRequest();
		request.setName("Summer Vacation");
		request.setDestination("Hawaii");
		request.setStartDate(LocalDate.of(2027, 6, 1));
		request.setEndDate(LocalDate.of(2027, 6, 10));
		request.setNumberOfTravelers(2);

		// Pretend the user does not exist in the database
		when(userRepository.findById(1L)).thenReturn(Optional.empty());

		// Check that the trip is rejected
		assertThrows(IllegalArgumentException.class, () -> tripService.createTrip(request, 1L));

		// Make sure the trip was not saved
		verify(tripRepository, never()).save(any(Trip.class));
	}

}