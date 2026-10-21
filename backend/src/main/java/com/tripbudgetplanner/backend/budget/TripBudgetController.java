package com.tripbudgetplanner.backend.budget;

import com.tripbudgetplanner.backend.budget.dto.BudgetRequest;
import com.tripbudgetplanner.backend.budget.dto.BudgetResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;

// Trip budget (UC4), nested under the trip it belongs to
@RestController
@RequestMapping("/api/trips/{tripId}/budget")
public class TripBudgetController {

    private final TripBudgetService budgetService;

    public TripBudgetController(TripBudgetService budgetService) {
        this.budgetService = budgetService;
    }

    // 200 with the budget, or 404 if the trip has no budget yet
    @GetMapping
    public ResponseEntity<BudgetResponse> get(@PathVariable Long tripId,
                                              @AuthenticationPrincipal Long userId) {
        return budgetService.getBudget(tripId, userId)
            .map(b -> ResponseEntity.ok(toResponse(b)))
            .orElse(ResponseEntity.notFound().build());
    }

    // Create or replace the budget
    @PutMapping
    public BudgetResponse save(@PathVariable Long tripId,
                               @Valid @RequestBody BudgetRequest request,
                               @AuthenticationPrincipal Long userId) {
        return toResponse(budgetService.saveBudget(tripId, request, userId));
    }

    private BudgetResponse toResponse(TripBudget b) {
        return new BudgetResponse(
            b.getTripId(),
            b.getTotalAmount(),
            b.getCurrency(),
            TripBudgetService.totalAllocated(b),
            new LinkedHashMap<>(b.getAllocations()));
    }
}
