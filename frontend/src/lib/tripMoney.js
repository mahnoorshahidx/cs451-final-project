// Shared helpers for the Budget and Savings pages. Dates stay "YYYY-MM-DD" strings.
import { todayIso } from "./formatDate";
import { BUDGET_CATEGORIES } from "./budgetSuggestions";

export function toUtc(iso) {
  const [y, m, d] = iso.split("-").map(Number);
  return Date.UTC(y, m - 1, d);
}

export function daysBetween(start, end) {
  if (!start || !end) return 0;
  return Math.round((toUtc(end) - toUtc(start)) / 86400000);
}

export function addDays(iso, days) {
  return new Date(toUtc(iso) + days * 86400000).toISOString().slice(0, 10);
}

// "Dec 8" or "Dec 8, 2026"
export function shortDate(iso, withYear = false) {
  if (!iso) return "";
  return new Date(toUtc(iso)).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    ...(withYear && { year: "numeric" }),
    timeZone: "UTC",
  });
}

// Whole-dollar money in the trip's currency: makeMoney("USD")(1234) -> "$1,234"
export function makeMoney(currency) {
  const fmt = new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: currency || "USD",
    maximumFractionDigits: 0,
  });
  return (n) => fmt.format(Number(n) || 0);
}

// Countdown text and its colour (marigold soon, coral within 30 days, sage later, grey past)
export function countdown(trip, today = todayIso()) {
  if (trip.endDate < today) return { label: "Completed", tone: "past" };
  if (trip.startDate <= today) return { label: "Happening now", tone: "now" };
  const days = daysBetween(today, trip.startDate);
  if (days === 1) return { label: "Tomorrow", tone: "now" };
  return { label: `In ${days} days`, tone: days <= 30 ? "soon" : "later" };
}

// Upcoming (soonest first) and past (latest first) trips from the budget overview
export function splitTrips(overview, today = todayIso()) {
  const list = overview || [];
  return {
    upcoming: list.filter((t) => t.endDate >= today).sort((a, b) => a.startDate.localeCompare(b.startDate)),
    past: list.filter((t) => t.endDate < today).sort((a, b) => b.startDate.localeCompare(a.startDate)),
  };
}

// Category caps in display order, plus whatever is left as buffer
export function envelopes(budget) {
  const total = Number(budget?.totalBudget) || 0;
  const allocations = budget?.allocations || {};
  const names = [
    ...BUDGET_CATEGORIES.filter((c) => Number(allocations[c]) > 0),
    ...Object.keys(allocations).filter((c) => !BUDGET_CATEGORIES.includes(c) && Number(allocations[c]) > 0),
  ];
  const items = names.map((name) => ({ name, amount: Number(allocations[name]) }));
  const planned = items.reduce((sum, item) => sum + item.amount, 0);
  const buffer = Math.max(0, total - planned);
  return { total, items, buffer };
}

// A monthly amount that covers the budget by the day before the trip.
// null when there's no budget, or the trip is under two weeks away.
export function suggestedPlan(trip, today = todayIso()) {
  const budget = Number(trip.budgetTotal);
  if (!(budget > 0)) return null;
  const targetDate = addDays(trip.startDate, -1);
  const days = daysBetween(today, targetDate);
  if (days < 14) return null;
  const months = Math.max(1, Math.round(days / 30.44));
  return { target: budget, targetDate, months, monthly: Math.ceil(budget / months) };
}

// Totals from dated entries: the running total, the pace (saved per day since the first entry,
// once there's a week of history), when the goal is reached at that pace, and where it ends up.
export function savingsForecast(entries, goal, endDate, today) {
  const sorted = [...entries].sort((a, b) => a.date.localeCompare(b.date));
  let running = 0;
  const points = sorted.map((e) => ({ date: e.date, value: (running += Number(e.amount) || 0) }));
  const saved = running;
  const first = points[0];

  // Pace = saved per day since the first entry (needs at least a week of history)
  const span = first ? daysBetween(first.date, today) : 0;
  const rate = first && span >= 7 && saved > 0 ? saved / span : null;
  let reachDate = null;
  if (rate && goal > saved) {
    reachDate = addDays(today, Math.ceil((goal - saved) / rate));
  }
  const atEnd = rate && endDate > today ? saved + rate * daysBetween(today, endDate) : null;
  return { points, saved, rate, reachDate, atEnd };
}

