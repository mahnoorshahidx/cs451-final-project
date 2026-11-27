package com.tripbudgetplanner.backend.budget;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tripbudgetplanner.backend.trip.TripAccessService;

// UC6: deleting a search item can take back what it added to the budget
class TripBudgetTakeBackTest {

    private static final Long TRIP_ID = 4L;

    private TripBudgetRepository repository;
    private TripBudgetService service;
    private TripBudget budget;

    @BeforeEach
    void setUp() {
        repository = mock(TripBudgetRepository.class);
        service = new TripBudgetService(repository, mock(TripAccessService.class));

        budget = new TripBudget(TRIP_ID);
        budget.setCurrency("USD");
        budget.setTotalAmount(new BigDecimal("3000"));
        budget.getAllocations().put("Flights", new BigDecimal("2000"));
        budget.getAllocations().put("Food", new BigDecimal("500"));
        when(repository.findByTripId(TRIP_ID)).thenReturn(Optional.of(budget));
    }

    @Test
    void takesTheAmountOffTheCategoryAndTheTotal() {
        BigDecimal removed = service.takeBackFromCategory(TRIP_ID, "Flights", new BigDecimal("1200"), "USD");

        assertEquals(0, new BigDecimal("1200").compareTo(removed));
        assertEquals(0, new BigDecimal("800").compareTo(budget.getAllocations().get("Flights")));
        assertEquals(0, new BigDecimal("1800").compareTo(budget.getTotalAmount()));
        verify(repository).save(budget);
    }

    @Test
    void neverGoesBelowZero() {
        BigDecimal removed = service.takeBackFromCategory(TRIP_ID, "Flights", new BigDecimal("5000"), "USD");

        assertEquals(0, new BigDecimal("2000").compareTo(removed));
        assertEquals(0, BigDecimal.ZERO.compareTo(budget.getAllocations().get("Flights")));
        // The total never drops below what is still allocated
        assertTrue(budget.getTotalAmount().compareTo(new BigDecimal("500")) >= 0);
    }

    @Test
    void ignoresAnotherCurrencyOrAMissingCategory() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
            service.takeBackFromCategory(TRIP_ID, "Flights", new BigDecimal("100"), "EUR")));
        assertEquals(0, BigDecimal.ZERO.compareTo(
            service.takeBackFromCategory(TRIP_ID, "Accommodation", new BigDecimal("100"), "USD")));
        verify(repository, never()).save(any());
    }

    @Test
    void noBudgetMeansNothingToTakeBack() {
        when(repository.findByTripId(TRIP_ID)).thenReturn(Optional.empty());

        assertEquals(0, BigDecimal.ZERO.compareTo(
            service.takeBackFromCategory(TRIP_ID, "Flights", new BigDecimal("100"), "USD")));
    }
}
