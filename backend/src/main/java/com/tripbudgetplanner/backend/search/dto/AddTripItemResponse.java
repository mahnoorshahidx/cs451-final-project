package com.tripbudgetplanner.backend.search.dto;

import java.util.List;

import com.tripbudgetplanner.backend.budget.dto.BudgetChange;
import com.tripbudgetplanner.backend.itinerary.dto.ItineraryActivityResponse;

// Result of adding a search result.
// activities: the new itinerary entries (two for a round-trip flight: outbound, then return).
// item: the result as it was priced when added.
// budgetChange: what was added to the budget, or null when updateBudget was false.
public record AddTripItemResponse(
    List<ItineraryActivityResponse> activities,
    SearchResult item,
    BudgetChange budgetChange,
    String notice
) {}
