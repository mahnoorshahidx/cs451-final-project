import { useState } from "react";
import { Link } from "react-router-dom";
import { BUDGET_CATEGORIES, fallbackAllocations } from "../lib/budgetSuggestions";

const COLORS = {
  Flights: "#213253",
  Accommodation: "#638FB3",
  Food: "#E9AB42",
  "Local transport": "#6C8569",
  Activities: "#DB6E5F",
};

const money = (value) =>
  new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 0,
  }).format(value);

// Keeps a typed value inside a sensible range ("" stays "" while typing)
const clampInput = (raw, min, max) => {
  if (raw === "") return "";
  const n = Math.floor(Number(raw));
  if (Number.isNaN(n)) return min;
  return Math.min(max, Math.max(min, n));
};

// "Try it" card on the landing page: splits a budget the same way the app does
// when it has no recommendation from the backend. Nothing is saved.
export default function LandingBudgetPreview() {
  const [total, setTotal] = useState(3200);
  const [travelers, setTravelers] = useState(2);
  const [months, setMonths] = useState(3);

  const budget = Number(total) || 0;
  const people = Number(travelers) || 1;
  const allocations = fallbackAllocations(budget);
  const allocated = Object.values(allocations).reduce((sum, value) => sum + value, 0);
  const buffer = Math.max(0, budget - allocated);

  const rows = [
    ...BUDGET_CATEGORIES.map((name) => ({ name, amount: allocations[name] ?? 0, color: COLORS[name] })),
    { name: "Buffer", amount: buffer, color: "var(--tb-border-strong)" },
  ];

  return (
    <section className="tb-card landing-try" aria-labelledby="landing-try-title">
      <div className="landing-try-head">
        <span className="tb-kicker">Try it</span>
        <h2 id="landing-try-title">
          <span className="tb-wide">Split a</span>{" "}
          <span className="tb-serif">trip budget</span>
        </h2>
      </div>

      <div className="landing-try-inputs">
        <label className="tb-field">
          Total budget
          <span className="landing-try-money">
            <span aria-hidden="true">$</span>
            <input
              className="tb-input"
              type="number"
              inputMode="numeric"
              min="0"
              step="100"
              value={total}
              onChange={(e) => setTotal(clampInput(e.target.value, 0, 1000000))}
            />
          </span>
        </label>

        <label className="tb-field">
          Travelers
          <input
            className="tb-input"
            type="number"
            inputMode="numeric"
            min="1"
            max="20"
            value={travelers}
            onChange={(e) => setTravelers(clampInput(e.target.value, 1, 20))}
          />
        </label>

        <label className="tb-field">
          Trip is in
          <select className="tb-input" value={months} onChange={(e) => setMonths(Number(e.target.value))}>
            {[1, 2, 3, 4, 6, 9, 12].map((m) => (
              <option key={m} value={m}>
                {m} {m === 1 ? "month" : "months"}
              </option>
            ))}
          </select>
        </label>
      </div>

      {/* One stacked bar, then the amounts */}
      <div className="landing-try-bar" aria-hidden="true">
        {rows.map((row) =>
          budget > 0 && row.amount > 0 ? (
            <span key={row.name} style={{ flexGrow: row.amount, background: row.color }} />
          ) : null
        )}
      </div>

      <ul className="landing-try-rows">
        {rows.map((row) => (
          <li key={row.name}>
            <span className="landing-try-dot" style={{ background: row.color }} aria-hidden="true" />
            <span>{row.name}</span>
            <span className="tb-num">{money(row.amount)}</span>
          </li>
        ))}
      </ul>

      <div className="landing-try-result">
        <div>
          <span className="tb-kicker">Save each month</span>
          <span className="tb-num landing-try-big">{money(Math.ceil(budget / months))}</span>
        </div>
        <div>
          <span className="tb-kicker">Per person</span>
          <span className="tb-num landing-try-big">{money(Math.ceil(budget / people))}</span>
        </div>
      </div>

      <Link to="/signup" className="tb-btn tb-btn-primary landing-try-cta">
        Save this as a trip
      </Link>
    </section>
  );
}
