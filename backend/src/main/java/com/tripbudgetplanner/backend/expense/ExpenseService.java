package com.tripbudgetplanner.backend.expense;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tripbudgetplanner.backend.trip.Trip;
import com.tripbudgetplanner.backend.trip.TripRepository;
import com.tripbudgetplanner.backend.trip.exception.TripNotFoundException;
import com.tripbudgetplanner.backend.expense.dto.*;

@Service
public class ExpenseService {

    private final ExpenseRepository expenses;
    private final TripRepository trips;

    public ExpenseService(ExpenseRepository expenses, TripRepository trips) {
        this.expenses = expenses;
        this.trips = trips;
    }

    // Check that the user owns the trip.
    private Trip ownedTrip(Long tripId, Long userId) {
        if (userId == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");

        return trips.findByIdAndUserId(tripId, userId)
            .orElseThrow(() -> new TripNotFoundException());
    }

    // Find an expense that belongs to the trip.
    private Expense ownedExpense(Long tripId, Long expenseId) {
        return expenses.findByIdAndTripId(expenseId, tripId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
    }

    // Convert an expense into an API response.
    private ExpenseResponse response(Expense expense) {
        return new ExpenseResponse(expense.getId(), expense.getTrip().getId(),
            expense.getCategory(), expense.getAmount(), expense.getExpenseDate(), expense.getDescription());
    }

    // Apply the expense details from the request.
    private void apply(Expense expense, ExpenseRequest request) {
        expense.setCategory(request.category().trim());
        expense.setAmount(request.amount());
        expense.setExpenseDate(request.expenseDate());
        expense.setDescription(request.description() == null || request.description().isBlank()
            ? null : request.description().trim());
    }

    // Get all expenses for a trip.
    @Transactional(readOnly = true)
    public List<ExpenseResponse> list(Long tripId, Long userId) {
        ownedTrip(tripId, userId);

        return expenses.findByTripIdOrderByExpenseDateDescIdDesc(tripId)
            .stream().map(this::response).toList();
    }

    // Get one expense by ID.
    @Transactional(readOnly = true)
    public ExpenseResponse get(Long tripId, Long expenseId, Long userId) {
        ownedTrip(tripId, userId);
        return response(ownedExpense(tripId, expenseId));
    }

    // Create and save a new expense.
    @Transactional
    public ExpenseResponse create(Long tripId, ExpenseRequest request, Long userId) {
        Trip trip = ownedTrip(tripId, userId);
        Expense expense = new Expense(trip);

        apply(expense, request);
        return response(expenses.save(expense));
    }

    // Update an existing expense.
    @Transactional
    public ExpenseResponse update(Long tripId, Long expenseId, ExpenseRequest request, Long userId) {
        ownedTrip(tripId, userId);
        Expense expense = ownedExpense(tripId, expenseId);

        apply(expense, request);
        return response(expenses.save(expense));
    }

    // Delete an expense from the trip.
    @Transactional
    public void delete(Long tripId, Long expenseId, Long userId) {
        ownedTrip(tripId, userId);
        expenses.delete(ownedExpense(tripId, expenseId));
    }

    // Calculate total spending and spending by category.
    @Transactional(readOnly = true)
    public ExpenseSummaryResponse summary(Long tripId, Long userId) {
        ownedTrip(tripId, userId);

        List<Expense> items = expenses.findByTripIdOrderByExpenseDateDescIdDesc(tripId);
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Expense item : items) {
            total = total.add(item.getAmount());
            totals.merge(item.getCategory(), item.getAmount(), BigDecimal::add);
        }

        return new ExpenseSummaryResponse(tripId, total, items.size(), totals);
    }
}
