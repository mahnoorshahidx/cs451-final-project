// Remembers the AI questionnaire answers for each trip in this browser,
// so "Regenerate" can reuse them later without asking again.

const key = (tripId) => `itinerary-preferences:${tripId}`;

export function loadItineraryPreferences(tripId) {
  try {
    const saved = window.localStorage.getItem(key(tripId));
    return saved ? JSON.parse(saved) : null;
  } catch {
    return null;
  }
}

export function saveItineraryPreferences(tripId, preferences) {
  try {
    window.localStorage.setItem(key(tripId), JSON.stringify(preferences));
  } catch {
    // Storage full or blocked: regenerating will just ask for preferences again
  }
}

export function clearItineraryPreferences(tripId) {
  try {
    window.localStorage.removeItem(key(tripId));
  } catch {
    // ignore
  }
}

// The request body the backend expects, from the questionnaire's answers
export function generationRequest(preferences, extra = {}) {
  return {
    dailyBudget: Number(preferences.dailyBudget) || null,
    currency: preferences.currency,
    travelStyle: preferences.travelStyle,
    minimumRating: preferences.minimumRating,
    interests: preferences.interests,
    pace: preferences.pace,
    foodPreferences: preferences.diningPreferences,
    specialRequests: preferences.specialRequests,
    ...extra,
  };
}

// "Luxury · Relaxed · Food, Nature · $120/day per person" for the summary line
export function preferenceSummary(preferences) {
  if (!preferences) return "";
  const style = { budget: "Budget-friendly", balanced: "Mid-range", luxury: "Luxury" }[preferences.travelStyle];
  const pace = { relaxed: "Relaxed pace", balanced: "Balanced pace", packed: "Packed days" }[preferences.pace];
  const parts = [style, pace];
  if (preferences.interests?.length) parts.push(preferences.interests.join(", "));
  if (preferences.dailyBudget) {
    parts.push(`${preferences.dailyBudget} ${preferences.currency || ""}/day per person`.replace(" /", "/"));
  }
  const food = (preferences.diningPreferences || []).filter((f) => f !== "No restrictions");
  if (food.length) parts.push(food.join(", "));
  return parts.filter(Boolean).join(" · ");
}

// Splits selected day keys ("YYYY-MM-DD", sorted) into runs of consecutive days,
// each at most maxDays long, so each run is one AI request.
export function dayChunks(dayKeys, maxDays = 14) {
  const sorted = [...new Set(dayKeys)].sort();
  const chunks = [];
  let current = [];

  const nextDay = (iso) => {
    const [y, m, d] = iso.split("-").map(Number);
    return new Date(Date.UTC(y, m - 1, d + 1)).toISOString().slice(0, 10);
  };

  for (const day of sorted) {
    const last = current[current.length - 1];
    if (current.length && (nextDay(last) !== day || current.length >= maxDays)) {
      chunks.push(current);
      current = [];
    }
    current.push(day);
  }
  if (current.length) chunks.push(current);

  return chunks.map((days) => ({ startDate: days[0], endDate: days[days.length - 1], days }));
}
