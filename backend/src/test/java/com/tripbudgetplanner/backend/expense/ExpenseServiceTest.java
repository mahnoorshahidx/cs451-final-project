package com.tripbudgetplanner.backend.expense;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.tripbudgetplanner.backend.expense.dto.ExpenseRequest;
import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.trip.TripRepository;

class ExpenseServiceTest {

    private ExpenseRepository expenseRepository;
    private TripRepository tripRepository;
    private ExpenseService service;
    private Trip trip;

    private final Long tripId = 1L;
    private final Long userId = 10L;

    // Set up mock repositories before each test.
    @BeforeEach
    void setUp() {
        expenseRepository = mock(ExpenseRepository.class);
        tripRepository = mock(TripRepository.class);

        service = new ExpenseService(
            expenseRepository, tripRepository
        );

        trip = mock(Trip.class);
        when(trip.getId()).thenReturn(tripId);

        when(tripRepository.findByIdAndUserId(tripId, userId))
            .thenReturn(Optional.of(trip));
    }

    // Create sample expense data for testing.
    private ExpenseRequest request() {
        return new ExpenseRequest(
            "Food",
            new BigDecimal("25.50"),
            LocalDate.of(2026, 11, 12),
            "Dinner"
        );
    }

    // Create a sample expense.
    private Expense expense(String category, String amount) {
        Expense expense = new Expense(trip);
        expense.setCategory(category);
        expense.setAmount(new BigDecimal(amount));
        expense.setExpenseDate(LocalDate.of(2026, 11, 12));
        expense.setDescription("Test expense");
        return expense;
    }

    // Test creating a new expense.
    @Test
    void createExpense() {
        when(expenseRepository.save(any(Expense.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(tripId, request(), userId);

        assertEquals("Food", result.category());
        assertEquals(0, new BigDecimal("25.50")
            .compareTo(result.amount()));
        assertEquals(tripId, result.tripId());

        verify(expenseRepository).save(any(Expense.class));
    }

    // Test listing expenses for a trip.
    @Test
    void listExpenses() {
        when(expenseRepository
            .findByTripIdOrderByExpenseDateDescIdDesc(tripId))
            .thenReturn(List.of(
                expense("Food", "25.50"),
                expense("Hotel", "100.00")
            ));

        var results = service.list(tripId, userId);

        assertEquals(2, results.size());
        assertEquals("Food", results.get(0).category());
        assertEquals("Hotel", results.get(1).category());
    }

    // Test updating an existing expense.
    @Test
    void updateExpense() {
        Expense existing = expense("Food", "20.00");

        when(expenseRepository.findByIdAndTripId(5L, tripId))
            .thenReturn(Optional.of(existing));

        when(expenseRepository.save(any(Expense.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.update(
            tripId, 5L, request(), userId
        );

        assertEquals(0, new BigDecimal("25.50")
            .compareTo(result.amount()));
        assertEquals("Dinner", result.description());

        verify(expenseRepository).save(existing);
    }

    // Test deleting an expense.
    @Test
    void deleteExpense() {
        Expense existing = expense("Food", "25.50");

        when(expenseRepository.findByIdAndTripId(5L, tripId))
            .thenReturn(Optional.of(existing));

        service.delete(tripId, 5L, userId);

        verify(expenseRepository).delete(existing);
    }

    // Test total spending and category totals.
    @Test
    void calculateExpenseSummary() {
        when(expenseRepository
            .findByTripIdOrderByExpenseDateDescIdDesc(tripId))
            .thenReturn(List.of(
                expense("Food", "25.50"),
                expense("Food", "14.50"),
                expense("Hotel", "100.00")
            ));

        var result = service.summary(tripId, userId);

        assertEquals(3, result.expenseCount());

        assertEquals(0, new BigDecimal("140.00")
            .compareTo(result.totalSpent()));

        assertEquals(0, new BigDecimal("40.00")
            .compareTo(result.spentByCategory().get("Food")));

        assertEquals(0, new BigDecimal("100.00")
            .compareTo(result.spentByCategory().get("Hotel")));
    }

    // Test that users must be logged in.
    @Test
    void rejectUserWithoutAuthentication() {
        ResponseStatusException error = assertThrows(
            ResponseStatusException.class,
            () -> service.list(tripId, null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED,
            error.getStatusCode());

        verifyNoInteractions(expenseRepository);
    }

    // Test that users cannot access another user's trip.
    @Test
    void rejectOtherUsersTrip() {
        when(tripRepository.findByIdAndUserId(tripId, 99L))
            .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
            () -> service.list(tripId, 99L));

        verifyNoInteractions(expenseRepository);
    }

    // Test that an expense must belong to the selected trip.
    @Test
    void rejectExpenseFromDifferentTrip() {
        when(expenseRepository.findByIdAndTripId(5L, tripId))
            .thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(
            ResponseStatusException.class,
            () -> service.get(tripId, 5L, userId)
        );

        assertEquals(HttpStatus.NOT_FOUND,
            error.getStatusCode());
    }

    // Verify that all expense details are saved correctly.
    @Test
    void verifyCreatedExpenseFields() {
        when(expenseRepository.save(any(Expense.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.create(tripId, request(), userId);

        ArgumentCaptor<Expense> captor =
            ArgumentCaptor.forClass(Expense.class);

        verify(expenseRepository).save(captor.capture());

        Expense saved = captor.getValue();

        assertSame(trip, saved.getTrip());
        assertEquals("Food", saved.getCategory());
        assertEquals(LocalDate.of(2026, 11, 12),
            saved.getExpenseDate());
    }
}
