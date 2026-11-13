package com.tripbudgetplanner.backend.expense;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import com.tripbudgetplanner.backend.expense.dto.*;

@RestController
@RequestMapping("/api/trips/{tripId}/expenses")
public class ExpenseController {

    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    // Get all expenses for a trip.
    @GetMapping
    public List<ExpenseResponse> list(@PathVariable Long tripId,
            @AuthenticationPrincipal Long userId) {
        return service.list(tripId, userId);
    }

    // Get the total spending and category breakdown.
    @GetMapping("/summary")
    public ExpenseSummaryResponse summary(@PathVariable Long tripId,
            @AuthenticationPrincipal Long userId) {
        return service.summary(tripId, userId);
    }

    // Get a specific expense.
    @GetMapping("/{expenseId}")
    public ExpenseResponse get(@PathVariable Long tripId,
            @PathVariable Long expenseId,
            @AuthenticationPrincipal Long userId) {
        return service.get(tripId, expenseId, userId);
    }

    // Add a new expense to a trip.
    @PostMapping
    public ResponseEntity<ExpenseResponse> create(@PathVariable Long tripId,
            @Valid @RequestBody ExpenseRequest request,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.create(tripId, request, userId));
    }

    // Update an existing expense.
    @PutMapping("/{expenseId}")
    public ExpenseResponse update(@PathVariable Long tripId,
            @PathVariable Long expenseId,
            @Valid @RequestBody ExpenseRequest request,
            @AuthenticationPrincipal Long userId) {
        return service.update(tripId, expenseId, request, userId);
    }

    // Delete an expense from a trip.
    @DeleteMapping("/{expenseId}")
    public ResponseEntity<Void> delete(@PathVariable Long tripId,
            @PathVariable Long expenseId,
            @AuthenticationPrincipal Long userId) {
        service.delete(tripId, expenseId, userId);
        return ResponseEntity.noContent().build();
    }
}
