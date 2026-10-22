
package com.tripbudgetplanner.backend.budget;

import com.tripbudgetplanner.backend.budget.dto.BudgetRequest;
import com.tripbudgetplanner.backend.trip.TripService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripBudgetServiceTest {

    @Mock
    private TripBudgetRepository budgetRepository;

    @Mock
    private TripService tripService;

    @InjectMocks
    private TripBudgetService budgetService;

    private Long tripId;
    private Long userId;

    @BeforeEach
    void setUp() {
        tripId = 1L;
        userId = 1L;
    }

    // Test creating a new trip budget
    @Test
    void shouldCreateBudget() {
        BudgetRequest request = new BudgetRequest(
            new BigDecimal("3000.00"),
            "USD",
            Map.of("Flights", new BigDecimal("1000.00"))
        );

        when(budgetRepository.findByTripId(tripId))
            .thenReturn(Optional.empty());

        when(budgetRepository.save(any(TripBudget.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        TripBudget result = budgetService.saveBudget(tripId, request, userId);

        assertEquals(new BigDecimal("3000.00"), result.getTotalAmount());
        assertEquals("USD", result.getCurrency());
        assertEquals(new BigDecimal("1000.00"),
            result.getAllocations().get("Flights"));

        verify(budgetRepository).save(any(TripBudget.class));
    }

    // Test updating an existing budget
    @Test
    void shouldUpdateBudget() {
        TripBudget existing = new TripBudget(tripId);
        existing.getAllocations().put("Flights", new BigDecimal("500.00"));

        BudgetRequest request = new BudgetRequest(
            new BigDecimal("4000.00"),
            "USD",
            Map.of("Hotels", new BigDecimal("1500.00"))
        );

        when(budgetRepository.findByTripId(tripId))
            .thenReturn(Optional.of(existing));

        when(budgetRepository.save(any(TripBudget.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        TripBudget result = budgetService.saveBudget(tripId, request, userId);

        assertEquals(new BigDecimal("4000.00"), result.getTotalAmount());
        assertFalse(result.getAllocations().containsKey("Flights"));
        assertEquals(new BigDecimal("1500.00"),
            result.getAllocations().get("Hotels"));
    }

    // Test rejecting allocations that exceed the total budget
    @Test
    void shouldRejectExcessAllocations() {
        BudgetRequest request = new BudgetRequest(
            new BigDecimal("1000.00"),
            "USD",
            Map.of("Flights", new BigDecimal("1500.00"))
        );

        assertThrows(IllegalArgumentException.class,
            () -> budgetService.saveBudget(tripId, request, userId));

        verify(budgetRepository, never()).save(any(TripBudget.class));
    }

    // Test retrieving an existing trip budget
    @Test
    void shouldGetBudget() {
        TripBudget existing = new TripBudget(tripId);

        when(budgetRepository.findByTripId(tripId))
            .thenReturn(Optional.of(existing));

        Optional<TripBudget> result = budgetService.getBudget(tripId, userId);

        assertTrue(result.isPresent());
        assertEquals(tripId, result.get().getTripId());
    }
}