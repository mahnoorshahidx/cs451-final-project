package com.tripbudgetplanner.backend.savings;

import com.tripbudgetplanner.backend.savings.dto.BudgetOverviewItem;
import com.tripbudgetplanner.backend.savings.dto.ContributionRequest;
import com.tripbudgetplanner.backend.savings.dto.SavingsGoalRequest;
import com.tripbudgetplanner.backend.savings.dto.SavingsResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Savings goals and contributions for the Budget page
@RestController
public class SavingsController {

    private final SavingsService savingsService;

    public SavingsController(SavingsService savingsService) {
        this.savingsService = savingsService;
    }

    // Every trip with its budget and savings progress
    @GetMapping("/api/budget-overview")
    public List<BudgetOverviewItem> overview(@AuthenticationPrincipal Long userId) {
        return savingsService.overview(userId);
    }

    // 200 with the goal, or 404 if the trip has no goal yet
    @GetMapping("/api/trips/{tripId}/savings")
    public ResponseEntity<SavingsResponse> get(@PathVariable Long tripId,
                                               @AuthenticationPrincipal Long userId) {
        return savingsService.getSavings(tripId, userId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // Create or update the goal
    @PutMapping("/api/trips/{tripId}/savings")
    public SavingsResponse saveGoal(@PathVariable Long tripId,
                                    @Valid @RequestBody SavingsGoalRequest request,
                                    @AuthenticationPrincipal Long userId) {
        return savingsService.saveGoal(tripId, request, userId);
    }

    @PostMapping("/api/trips/{tripId}/savings/contributions")
    public SavingsResponse addContribution(@PathVariable Long tripId,
                                           @Valid @RequestBody ContributionRequest request,
                                           @AuthenticationPrincipal Long userId) {
        return savingsService.addContribution(tripId, request, userId);
    }

    @DeleteMapping("/api/trips/{tripId}/savings/contributions/{contributionId}")
    public SavingsResponse deleteContribution(@PathVariable Long tripId,
                                              @PathVariable Long contributionId,
                                              @AuthenticationPrincipal Long userId) {
        return savingsService.deleteContribution(tripId, contributionId, userId);
    }
}
