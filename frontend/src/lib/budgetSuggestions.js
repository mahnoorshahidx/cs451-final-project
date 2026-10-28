// src/lib/budgetSuggestions.js
//
// Turns a total budget into category caps.
// The real recommendations come from the backend (/api/recommendations/budget).
// This file only scales them to the total the user picked, with a local fallback
// for when the recommendation couldn't be loaded.

export const BUDGET_CATEGORIES = ["Flights", "Accommodation", "Food", "Local transport", "Activities"];

const FALLBACK_WEIGHTS = {
  Flights: 0.3,
  Accommodation: 0.3,
  Food: 0.17,
  "Local transport": 0.08,
  Activities: 0.07,
  // remaining ~8% stays unallocated as a buffer
};

// Scale the recommended caps so they keep the same proportions at the user's total.
// The recommendation leaves a buffer, so the scaled caps do too.
export function scaleAllocations(recommendation, total) {
  if (!recommendation || !(Number(recommendation.recommendedTotal) > 0)) return null;
  const ratio = total / Number(recommendation.recommendedTotal);
  return Object.fromEntries(
    Object.entries(recommendation.allocations).map(([category, amount]) => [
      category,
      Math.floor(Number(amount) * ratio),
    ])
  );
}

// Used only if the backend recommendation isn't available
export function fallbackAllocations(total, tradeOffs = []) {
  const weights = { ...FALLBACK_WEIGHTS };
  if (tradeOffs.includes("budgetHotels") || tradeOffs.includes("sharedRooms")) weights.Accommodation -= 0.08;
  if (tradeOffs.includes("lowCostMeals")) weights.Food -= 0.05;
  if (tradeOffs.includes("longLayovers")) weights.Flights -= 0.05;
  if (tradeOffs.includes("skipPaidActivities")) weights.Activities = 0.01;

  return Object.fromEntries(
    Object.entries(weights).map(([category, weight]) => [category, Math.floor(total * Math.max(weight, 0))])
  );
}
