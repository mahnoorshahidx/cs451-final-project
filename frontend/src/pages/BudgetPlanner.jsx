import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  ArrowRight, CalendarDays, Coffee, Crown, PiggyBank, Plus, ShieldCheck, SlidersHorizontal, Users,
} from "lucide-react";
import Sidebar from "../components/Sidebar";
import BudgetDonut from "../components/BudgetDonut";
import { getBudget, getBudgetOverview } from "../api/api";
import { categoryMeta } from "../lib/budgetCategories";
import { todayIso } from "../lib/formatDate";
import { countdown, daysBetween, envelopes, makeMoney, shortDate, splitTrips } from "../lib/tripMoney";
import "../styles/Dashboard.css";
import "../styles/BudgetPages.css";

// Budget: /budget-planner (next trip) or /budget-planner/:tripId.
// One boxed board with the trips as tabs along its top. Inside: the trip's key numbers in a
// strip, the budget envelopes as the main view, then a few facts worked out from the budget.

// The trip's numbers across the top of the board
function StatStrip({ trip, budget }) {
  const money = makeMoney(budget.currency || trip.currency);
  const total = Number(budget.totalBudget) || 0;
  const travelers = Math.max(1, Number(trip.numberOfTravelers) || 1);
  const nights = Math.max(1, daysBetween(trip.startDate, trip.endDate));
  const hasGoal = trip.savingsTarget != null;

  return (
    <dl className="bp-stats">
      <div className="bp-stat is-main">
        <dt>Total budget</dt>
        <dd className="tb-num">{money(total)}</dd>
      </div>
      <div className="bp-stat">
        <dt>Per person</dt>
        <dd className="tb-num">{money(total / travelers)}</dd>
      </div>
      <div className="bp-stat">
        <dt>Per night</dt>
        <dd className="tb-num">{money(total / nights)}</dd>
      </div>
      <div className="bp-stat">
        <dt>Travelers</dt>
        <dd className="tb-num">{travelers}</dd>
      </div>
      <div className="bp-stat">
        <dt>Saved</dt>
        <dd>
          {hasGoal ? (
            <>
              <span className="tb-num">{money(trip.savedAmount)}</span>
              <span className="bp-stat-bar" aria-hidden="true">
                <span style={{ width: `${Math.min(100, trip.percentFunded ?? 0)}%` }} />
              </span>
            </>
          ) : (
            <span className="bp-stat-none">No goal yet</span>
          )}
        </dd>
      </div>
    </dl>
  );
}

function Envelopes({ trip, budget }) {
  const editLink = `/trips/${trip.tripId}/edit?tab=budget`;
  const money = makeMoney(budget.currency || trip.currency);
  const { total, items, buffer } = envelopes(budget);
  const rows = [...items, ...(buffer > 0 ? [{ name: "Buffer", amount: buffer, buffer: true }] : [])];
  const pct = (n) => (total > 0 ? Math.round((n / total) * 100) : 0);

  if (items.length === 0) {
    return (
      <div className="bp-env-empty">
        <p>Your {money(total)} budget isn't split into envelopes yet.</p>
        <Link to={editLink} className="tb-btn tb-btn-primary">
          <SlidersHorizontal size={16} aria-hidden="true" /> Set category caps
        </Link>
      </div>
    );
  }

  return (
    <div className="bp-envelopes">
      <div className="bp-env-chart">
        <BudgetDonut
          size={300}
          stroke={34}
          label={`Budget split for ${trip.name}: ${rows.map((r) => `${r.name} ${pct(r.amount)}%`).join(", ")}`}
          segments={rows.map((r) => ({
            key: r.name,
            category: r.name,
            value: r.amount,
            title: `${r.name}: ${money(r.amount)}`,
          }))}
        >
          <span className="tb-kicker">Planned</span>
          <span className="tb-num bp-donut-total">{money(total)}</span>
          <span className="bp-donut-sub">{rows.length} envelopes</span>
        </BudgetDonut>
      </div>

      <ul className="bp-env-list">
        {rows.map((r) => (
          <li key={r.name} className={`bp-env-row${r.buffer ? " is-buffer" : ""}`} data-category={r.name}>
            <span className="bp-swatch" aria-hidden="true" />
            <span className="bp-env-name">
              {r.name}
              <small>{r.buffer ? "Not assigned yet: room for surprises" : categoryMeta(r.name).description}</small>
            </span>
            <span className="bp-env-bar" aria-hidden="true">
              <span style={{ width: `${pct(r.amount)}%` }} />
            </span>
            <span className="bp-env-amount">
              <strong className="tb-num">{money(r.amount)}</strong>
              <small>{pct(r.amount)}%</small>
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

// Facts worked out from the budget and the trip; only the ones the data supports are shown
function TripFacts({ trip, budget }) {
  const money = makeMoney(budget.currency || trip.currency);
  const today = todayIso();
  const { total, items, buffer } = envelopes(budget);
  const travelers = Math.max(1, Number(trip.numberOfTravelers) || 1);
  const nights = Math.max(0, daysBetween(trip.startDate, trip.endDate));
  const days = nights + 1;
  const facts = [];

  if (items.length > 0) {
    const biggest = items.reduce((a, b) => (b.amount > a.amount ? b : a));
    facts.push({
      Icon: Crown,
      value: `${Math.round((biggest.amount / total) * 100)}%`,
      title: `goes to ${biggest.name.toLowerCase()}`,
      text: `${biggest.name} is the biggest envelope at ${money(biggest.amount)}.`,
    });
  }

  facts.push({
    Icon: CalendarDays,
    value: money(total / travelers / days),
    title: "per person, per day",
    text: `Spread over ${days} day${days === 1 ? "" : "s"} and ${travelers} traveler${travelers === 1 ? "" : "s"}.`,
  });

  const everyday = items.filter((i) => ["Food", "Activities", "Local transport"].includes(i.name));
  if (everyday.length > 0) {
    const amount = everyday.reduce((s, i) => s + i.amount, 0);
    facts.push({
      Icon: Coffee,
      value: money(amount / travelers / days),
      title: "spending money a day",
      text: `Each person's daily share of ${everyday.map((i) => i.name.toLowerCase()).join(", ")}.`,
    });
  }

  if (items.length > 0) {
    facts.push({
      Icon: ShieldCheck,
      value: buffer > 0 ? money(buffer) : "$0",
      title: buffer > 0 ? "cushion for surprises" : "no cushion",
      text: buffer > 0
        ? `${Math.round((buffer / total) * 100)}% of the budget isn't assigned to an envelope.`
        : "Every dollar is in an envelope. Consider leaving some room.",
    });
  }

  // Saving: what's left, or what a week of saving would need to look like
  const weeksLeft = Math.max(0, Math.floor(daysBetween(today, trip.startDate) / 7));
  if (trip.savingsTarget != null) {
    const left = Math.max(0, Number(trip.savingsTarget) - Number(trip.savedAmount || 0));
    facts.push({
      Icon: PiggyBank,
      value: left > 0 ? money(left) : "Funded",
      title: left > 0 ? "still to save" : "fully saved",
      text: left > 0 && weeksLeft > 0
        ? `About ${money(left / weeksLeft)} a week until you leave.`
        : left > 0 ? "The trip is almost here." : "You've saved the whole goal.",
      link: { to: `/savings-planner?trip=${trip.tripId}`, label: "View savings" },
    });
  } else {
    // Short trips count in days, longer ones in weeks
    const daysLeft = Math.max(0, daysBetween(today, trip.startDate));
    const byWeek = weeksLeft >= 3;
    const periods = byWeek ? weeksLeft : daysLeft;
    if (periods > 0) {
      const unit = byWeek ? "week" : "day";
      facts.push({
        Icon: PiggyBank,
        value: money(total / periods),
        title: `a ${unit} covers it`,
        text: `Saving that for the ${periods} ${unit}${periods === 1 ? "" : "s"} before you leave pays for the whole trip.`,
        link: { to: `/savings-planner?trip=${trip.tripId}`, label: "Set a savings goal" },
      });
    }
  }

  facts.push({
    Icon: Users,
    value: `${days}d · ${nights}n`,
    title: "trip length",
    text: `${shortDate(trip.startDate)} to ${shortDate(trip.endDate, true)} with ${travelers} traveler${travelers === 1 ? "" : "s"}.`,
  });

  return (
    <section className="bp-facts" aria-labelledby="bp-facts-title">
      <h2 id="bp-facts-title" className="tb-wide">Good to know</h2>
      <ul>
        {facts.map(({ Icon, value, title, text, link }) => (
          <li key={title} className="bp-fact">
            <span className="bp-fact-icon" aria-hidden="true"><Icon size={18} /></span>
            <span className="tb-num bp-fact-value">{value}</span>
            <strong>{title}</strong>
            <p>{text}</p>
            {link && (
              <Link to={link.to} className="bp-text-link">
                {link.label} <ArrowRight size={14} aria-hidden="true" />
              </Link>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}

export default function BudgetPlanner() {
  const { tripId } = useParams();
  const navigate = useNavigate();

  const [overview, setOverview] = useState(null);
  const [error, setError] = useState("");
  const [budgets, setBudgets] = useState({}); // tripId -> budget | null
  const [budgetError, setBudgetError] = useState("");

  useEffect(() => {
    getBudgetOverview()
      .then(setOverview)
      .catch((err) => setError(err.message || "Could not load your budgets."));
  }, []);

  const { upcoming, past } = splitTrips(overview);
  const selected = overview
    ? tripId
      ? overview.find((t) => String(t.tripId) === String(tripId))
      : upcoming[0] ?? past[0]
    : null;
  const selectedId = selected?.tripId;
  const budget = selectedId === undefined ? undefined : budgets[selectedId];

  // Load the selected trip's budget once; later visits to the tab reuse it
  useEffect(() => {
    if (selectedId === undefined || budgets[selectedId] !== undefined) return;
    let cancelled = false;
    getBudget(selectedId)
      .then((b) => !cancelled && setBudgets((all) => ({ ...all, [selectedId]: b })))
      .catch((err) => !cancelled && setBudgetError(err.message || "Could not load this budget."));
    return () => {
      cancelled = true;
    };
  }, [selectedId, budgets]);

  const isPastSelected = selected && past.some((t) => t.tripId === selected.tripId);
  const when = selected ? countdown(selected) : null;
  const editLink = selected ? `/trips/${selected.tripId}/edit?tab=budget` : "";

  return (
    <div className="dashboard-page bp-page">
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      <main className="dashboard-main bp-main">
        <header className="bp-header">
          <div className="bp-title">
            <span className="tb-kicker">Budget</span>
            <h1 className="tb-wide">What each trip really costs</h1>
          </div>
        </header>

        {error && <p role="alert" className="error-text">{error}</p>}
        {!overview && !error && <p role="status" className="bp-muted">Loading your budgets...</p>}

        {overview && overview.length === 0 && (
          <section className="bp-empty tb-card">
            <h2 className="tb-wide">No trips yet</h2>
            <p className="bp-muted">Create a trip and we'll recommend a budget for it.</p>
            <Link to="/create-trip" className="tb-btn tb-btn-primary">
              <Plus size={18} aria-hidden="true" /> Plan a trip
            </Link>
          </section>
        )}

        {overview && overview.length > 0 && (
          <section className="bp-board">
            {/* Trips as tabs along the top of the board */}
            <div className="bp-tabbar">
              <div className="bp-tabs" role="tablist" aria-label="Trips">
                {upcoming.map((t) => {
                  const active = t.tripId === selectedId;
                  return (
                    <button
                      key={t.tripId}
                      type="button"
                      role="tab"
                      aria-selected={active}
                      aria-controls="bp-panel"
                      className={`bp-tab${active ? " is-active" : ""}`}
                      onClick={() => navigate(`/budget-planner/${t.tripId}`)}
                    >
                      <strong>{t.destination?.split(",")[0] || t.name}</strong>
                      <span>{shortDate(t.startDate)}</span>
                    </button>
                  );
                })}
              </div>
              {past.length > 0 && (
                <label className={`bp-past-select${isPastSelected ? " is-active" : ""}`}>
                  <span className="bp-sr-only">Past trips</span>
                  <select
                    value={isPastSelected ? String(selectedId) : ""}
                    onChange={(e) => e.target.value && navigate(`/budget-planner/${e.target.value}`)}
                  >
                    <option value="">Past trips</option>
                    {past.map((t) => (
                      <option key={t.tripId} value={t.tripId}>
                        {t.name} · {shortDate(t.startDate, true)}
                      </option>
                    ))}
                  </select>
                </label>
              )}
            </div>

            <div id="bp-panel" role="tabpanel" className="bp-board-body">
              {!selected ? (
                <p className="bp-muted">
                  We couldn't find that trip. <Link to="/budget-planner">Show my next trip</Link>
                </p>
              ) : (
                <>
                  <div className="bp-trip-head">
                    <div>
                      <h2 className="tb-wide">{selected.name}</h2>
                      <p className="bp-trip-meta">
                        {selected.destination} · {shortDate(selected.startDate)} – {shortDate(selected.endDate, true)}
                        <span className={`bp-chip is-${when.tone}`}>{when.label}</span>
                      </p>
                    </div>
                    {budget && (
                      <div className="bp-trip-actions">
                        <Link to={editLink} className="tb-btn tb-btn-outline">
                          <SlidersHorizontal size={16} aria-hidden="true" /> Adjust caps
                        </Link>
                        <Link to={`/savings-planner?trip=${selected.tripId}`} className="tb-btn tb-btn-accent">
                          {selected.savingsTarget != null ? "View savings" : "Save for this trip"}
                          <ArrowRight size={16} aria-hidden="true" />
                        </Link>
                      </div>
                    )}
                  </div>

                  {budgetError && <p role="alert" className="error-text">{budgetError}</p>}
                  {budget === undefined && !budgetError && <p role="status" className="bp-muted">Loading budget...</p>}

                  {budget === null && (
                    <div className="bp-env-empty">
                      <p>This trip doesn't have a budget yet. We'll suggest one from the destination and dates.</p>
                      <Link to={editLink} className="tb-btn tb-btn-accent">Set a budget</Link>
                    </div>
                  )}

                  {budget && (
                    <>
                      <StatStrip trip={selected} budget={budget} />
                      <Envelopes trip={selected} budget={budget} />
                    </>
                  )}
                </>
              )}
            </div>
          </section>
        )}

        {selected && budget && <TripFacts trip={selected} budget={budget} />}
      </main>
    </div>
  );
}
