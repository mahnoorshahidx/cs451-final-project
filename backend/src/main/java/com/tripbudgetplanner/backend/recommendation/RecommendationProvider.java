package com.tripbudgetplanner.backend.recommendation;

import com.tripbudgetplanner.backend.recommendation.dto.BudgetRecommendation;
import com.tripbudgetplanner.backend.recommendation.dto.BudgetRecommendationRequest;
import com.tripbudgetplanner.backend.recommendation.dto.TimingRecommendation;
import com.tripbudgetplanner.backend.recommendation.dto.TimingRecommendationRequest;

// The one seam the AI plugs into.
// Today RuleBasedRecommendationProvider implements this with fixed rules.
// Later, add an AiRecommendationProvider that implements the same interface;
// the controller and frontend don't change.
public interface RecommendationProvider {

    TimingRecommendation recommendTiming(TimingRecommendationRequest request);

    BudgetRecommendation recommendBudget(BudgetRecommendationRequest request);
}
