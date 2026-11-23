package com.tripbudgetplanner.backend.trip;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripbudgetplanner.backend.budget.TripBudget;
import com.tripbudgetplanner.backend.budget.TripBudgetRepository;
import com.tripbudgetplanner.backend.currency.exception.UnsupportedCurrencyException;
import com.tripbudgetplanner.backend.expense.ExpenseRepository;
import com.tripbudgetplanner.backend.savings.SavingsGoal;
import com.tripbudgetplanner.backend.savings.SavingsContributionRepository;
import com.tripbudgetplanner.backend.savings.SavingsGoalRepository;
import com.tripbudgetplanner.backend.trip.dto.CreateTripRequest;
import com.tripbudgetplanner.backend.trip.exception.TripCurrencyChangeException;
import com.tripbudgetplanner.backend.user.User;
import com.tripbudgetplanner.backend.user.UserRepository;

public class TripServiceTest {

	@Test
	void createTripSuccessfully() {

		// Create mock repositories so we don't need a real database
		TripRepository tripRepository = mock(TripRepository.class);
		UserRepository userRepository = mock(UserRepository.class);

		TripService tripService = new TripService(tripRepository, userRepository,
        mock(SavingsGoalRepository.class), mock(SavingsContributionRepository.class),
        mock(TripBudgetRepository.class), mock(ExpenseRepository.class));

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

		TripService tripService = new TripService(tripRepository, userRepository,
        mock(SavingsGoalRepository.class), mock(SavingsContributionRepository.class),
        mock(TripBudgetRepository.class), mock(ExpenseRepository.class));

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

		TripService tripService = new TripService(tripRepository, userRepository,
        mock(SavingsGoalRepository.class), mock(SavingsContributionRepository.class),
        mock(TripBudgetRepository.class), mock(ExpenseRepository.class));

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

	// ---------- Trip currency ----------

	// Builds a valid trip request with the given currency
	private static CreateTripRequest requestWithCurrency(String currency) {
		CreateTripRequest request = new CreateTripRequest();
		request.setName("Tokyo Trip");
		request.setDestination("Tokyo");
		request.setStartDate(LocalDate.of(2027, 4, 1));
		request.setEndDate(LocalDate.of(2027, 4, 8));
		request.setNumberOfTravelers(2);
		request.setCurrency(currency);
		return request;
	}

	private static TripService service(TripRepository tripRepository, UserRepository userRepository) {
		return new TripService(tripRepository, userRepository,
			mock(SavingsGoalRepository.class), mock(SavingsContributionRepository.class),
        mock(TripBudgetRepository.class), mock(ExpenseRepository.class));
	}

	@Test
	void createTripSavesTheChosenCurrency() {
		TripRepository tripRepository = mock(TripRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));
		when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Lowercase is accepted and stored as uppercase
		Trip trip = service(tripRepository, userRepository).createTrip(requestWithCurrency("jpy"), 1L);

		assertEquals("JPY", trip.getCurrency());
	}

	@Test
	void createTripUsesTheUsersPreferredCurrencyWhenNoneIsGiven() {
		TripRepository tripRepository = mock(TripRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		User user = new User();
		user.setCurrency("EUR");
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Trip trip = service(tripRepository, userRepository).createTrip(requestWithCurrency(null), 1L);

		assertEquals("EUR", trip.getCurrency());
	}

	@Test
	void createTripFallsBackToUsdWhenThePreferenceIsNotSupported() {
		TripRepository tripRepository = mock(TripRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		User user = new User();
		user.setCurrency("MAD");
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Trip trip = service(tripRepository, userRepository).createTrip(requestWithCurrency(""), 1L);

		assertEquals("USD", trip.getCurrency());
	}

	@Test
	void createTripRejectsAnUnsupportedCurrency() {
		TripRepository tripRepository = mock(TripRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));

		assertThrows(UnsupportedCurrencyException.class,
			() -> service(tripRepository, userRepository).createTrip(requestWithCurrency("XYZ"), 1L));

		verify(tripRepository, never()).save(any(Trip.class));
	}

	@Test
	void oldTripsWithoutACurrencyReadAsUsd() {
		Trip trip = new Trip();
		trip.setCurrency("");

		assertEquals("USD", trip.getCurrency());
	}

	// ---------- Changing a trip's currency (updateTrip) ----------

	// Trip 5 belongs to user 1 and is currently in USD.
	// Each test adds the budget, expenses or savings it needs.
	private static class UpdateSetup {
		final TripRepository tripRepository = mock(TripRepository.class);
		final SavingsGoalRepository savingsGoalRepository = mock(SavingsGoalRepository.class);
		final TripBudgetRepository tripBudgetRepository = mock(TripBudgetRepository.class);
		final ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
		final Trip trip = new Trip();
		final TripService service = new TripService(tripRepository, mock(UserRepository.class),
			savingsGoalRepository, mock(SavingsContributionRepository.class),
			tripBudgetRepository, expenseRepository);

		UpdateSetup() {
			trip.setCurrency("USD");
			when(tripRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(trip));
			when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));
		}

		void withBudgetIn(String currency) {
			TripBudget budget = new TripBudget(5L);
			budget.setCurrency(currency);
			when(tripBudgetRepository.findByTripId(5L)).thenReturn(Optional.of(budget));
		}

		void withExpenses() {
			when(expenseRepository.existsByTripId(5L)).thenReturn(true);
		}

		// Contributions always belong to a savings goal, so a goal stands for both
		void withSavingsGoal() {
			when(savingsGoalRepository.findByTripId(5L)).thenReturn(Optional.of(new SavingsGoal(5L)));
		}

		Trip update(String currency) {
			return service.updateTrip(5L, requestWithCurrency(currency), 1L);
		}
	}

	@Test
	void currencyCanChangeWhenTheTripHasNoMoneyRecorded() {
		UpdateSetup setup = new UpdateSetup();

		assertEquals("KRW", setup.update("KRW").getCurrency());
	}

	@Test
	void missingCurrencyKeepsTheCurrentOneEvenWithMoneyRecorded() {
		UpdateSetup setup = new UpdateSetup();
		setup.withBudgetIn("USD");
		setup.withExpenses();
		setup.withSavingsGoal();

		// The current Edit Trip page doesn't send a currency
		Trip updated = setup.update(null);

		assertEquals("USD", updated.getCurrency());
		assertEquals("Tokyo Trip", updated.getName(), "other details are still saved");
		verify(setup.expenseRepository, never()).existsByTripId(any());
	}

	@Test
	void sendingTheSameCurrencyIsAllowedEvenWithMoneyRecorded() {
		UpdateSetup setup = new UpdateSetup();
		setup.withExpenses();

		// Lowercase still counts as the same currency
		assertEquals("USD", setup.update("usd").getCurrency());
	}

	@Test
	void cannotChangeCurrencyWhenTheTripHasABudget() {
		UpdateSetup setup = new UpdateSetup();
		setup.withBudgetIn("USD");

		TripCurrencyChangeException e = assertThrows(TripCurrencyChangeException.class,
			() -> setup.update("EUR"));

		assertTrue(e.getMessage().contains("a budget in USD"));
		verify(setup.tripRepository, never()).save(any(Trip.class));
		assertEquals("USD", setup.trip.getCurrency());
	}

	@Test
	void cannotChangeCurrencyWhenTheTripHasExpenses() {
		UpdateSetup setup = new UpdateSetup();
		setup.withExpenses();

		TripCurrencyChangeException e = assertThrows(TripCurrencyChangeException.class,
			() -> setup.update("JPY"));

		assertTrue(e.getMessage().contains("expenses"));
		verify(setup.tripRepository, never()).save(any(Trip.class));
	}

	@Test
	void cannotChangeCurrencyWhenTheTripHasSavings() {
		UpdateSetup setup = new UpdateSetup();
		setup.withSavingsGoal();

		TripCurrencyChangeException e = assertThrows(TripCurrencyChangeException.class,
			() -> setup.update("GBP"));

		assertTrue(e.getMessage().contains("a savings goal"));
		verify(setup.tripRepository, never()).save(any(Trip.class));
	}

	@Test
	void canMatchTheBudgetsOwnCurrencyWhenThereAreNoExpensesOrSavings() {
		// Trip was set to USD by the database update, but its budget was saved in EUR
		UpdateSetup setup = new UpdateSetup();
		setup.withBudgetIn("EUR");

		assertEquals("EUR", setup.update("EUR").getCurrency());
	}

	@Test
	void cannotMatchTheBudgetCurrencyOnceASavingsGoalExists() {
		UpdateSetup setup = new UpdateSetup();
		setup.withBudgetIn("EUR");
		setup.withSavingsGoal();

		TripCurrencyChangeException e = assertThrows(TripCurrencyChangeException.class,
			() -> setup.update("EUR"));

		assertTrue(e.getMessage().contains("a savings goal"));
		verify(setup.tripRepository, never()).save(any(Trip.class));
		assertEquals("USD", setup.trip.getCurrency());
	}

	@Test
	void cannotMatchTheBudgetCurrencyOnceExpensesExist() {
		UpdateSetup setup = new UpdateSetup();
		setup.withBudgetIn("EUR");
		setup.withExpenses();

		assertThrows(TripCurrencyChangeException.class, () -> setup.update("EUR"));
		verify(setup.tripRepository, never()).save(any(Trip.class));
	}

	@Test
	void unsupportedBudgetCurrencyLikeMadBlocksTheChangeWithoutErrors() {
		// Budgets can still be saved in MAD from the Edit Trip page
		UpdateSetup setup = new UpdateSetup();
		setup.withBudgetIn("MAD");

		TripCurrencyChangeException e = assertThrows(TripCurrencyChangeException.class,
			() -> setup.update("EUR"));

		assertTrue(e.getMessage().contains("a budget in MAD"));
		assertEquals("USD", setup.trip.getCurrency());
	}

	@Test
	void unsupportedNewCurrencyIsRejectedBeforeAnyChecks() {
		UpdateSetup setup = new UpdateSetup();

		assertThrows(UnsupportedCurrencyException.class, () -> setup.update("MAD"));
		verify(setup.tripRepository, never()).save(any(Trip.class));
	}

}