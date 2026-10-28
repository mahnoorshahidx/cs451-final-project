package com.tripbudgetplanner.backend.recommendation;

import com.tripbudgetplanner.backend.recommendation.dto.BudgetRecommendation;
import com.tripbudgetplanner.backend.recommendation.dto.BudgetRecommendationRequest;
import com.tripbudgetplanner.backend.recommendation.dto.TimingRecommendation;
import com.tripbudgetplanner.backend.recommendation.dto.TimingRecommendationRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Recommendations for the Create Trip wizard. Nothing here is saved;
// the wizard saves the trip and budget the user accepts.
// Requires login like the rest of /api/**.
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationProvider provider;

    public RecommendationController(RecommendationProvider provider) {
        this.provider = provider;
    }

    @PostMapping("/timing")
    public TimingRecommendation timing(@Valid @RequestBody TimingRecommendationRequest request) {
        return provider.recommendTiming(request);
    }

    @PostMapping("/budget")
    public BudgetRecommendation budget(@Valid @RequestBody BudgetRecommendationRequest request) {
        return provider.recommendBudget(request);
    }
}
