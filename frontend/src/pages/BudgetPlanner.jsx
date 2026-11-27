import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowRight, Plus, SlidersHorizontal } from "lucide-react";
import Sidebar from "../components/Sidebar";
import BudgetDonut from "../components/BudgetDonut";
import { getBudget, getBudgetOverview } from "../api/api";
import useDestinationPhoto from "../lib/useDestinationPhoto";
import { categoryMeta } from "../lib/budgetCategories";
import { countdown, daysBetween, envelopes, makeMoney, shortDate, splitTrips } from "../lib/tripMoney";
import "../styles/Dashboard.css";
import "../styles/BudgetPages.css";

// Budget: /budget-planner (next trip) or /budget-planner/:tripId. One trip at a time:
// what it costs in total, per person and per night, and how the money is split.

function TripHero({ trip }) {
  const [photo, clearPhoto] = useDestinationPhoto(trip.destination);
  const place = trip.destination?.split(",")[0]?.trim() || trip.name;
  const nights = Math.max(0, daysBetween(trip.startDate, trip.endDate));
  const when = countdown(trip);

  return (
    <div className="bp-hero">
      {photo?.imageUrl && <img src={photo.imageUrl} alt="" onError={clearPhoto} />}
      <div className="bp-hero-shade" aria-hidden="true" />
      <div className="bp-hero-text">
        <span className="tb-wide bp-hero-place">{place}</span>
        <div className="bp-chips">
          <span className="bp-chip">{shortDate(trip.startDate)} – {shortDate(trip.endDate, true)}</span>
          <span className="bp-chip">
            {trip.numberOfTravelers} traveler{trip.numberOfTravelers === 1 ? "" : "s"} · {nights} night{nights === 1 ? "" : "s"}
          </span>
          <span className={`bp-chip is-${when.tone}`}>{when.label}</span>
        </div>
      </div>
    </div>
  );
}

function Totals({ trip, budget }) {
  const money = makeMoney(budget?.currency || trip.currency);
  const editLink = `/trips/${trip.tripId}/edit?tab=budget`;

  if (!budget) {
    return (
      <div className="bp-totals is-empty">
        <span className="tb-kicker">Total budget</span>
        <p className="bp-totals-empty">This trip doesn't have a budget yet. We'll suggest one from the destination and dates.</p>
        <Link to={editLink} className="tb-btn tb-btn-accent">Set a budget</Link>
      </div>
    );
  }

  const total = Number(budget.totalBudget) || 0;
  const travelers = Math.max(1, Number(trip.numberOfTravelers) || 1);
  const nights = Math.max(1, daysBetween(trip.startDate, trip.endDate));
  const hasGoal = trip.savingsTarget != null;

  return (
    <div className="bp-totals">
      <div className="bp-stat">
        <span className="tb-kicker">Total budget</span>
        <span className="tb-num bp-stat-big">{money(total)}</span>
      </div>
      <div className="bp-stat">
        <span className="tb-kicker">Per person</span>
        <span className="tb-num bp-stat-big">{money(total / travelers)}</span>
      </div>
      <div className="bp-stat">
        <span className="tb-kicker">Per night</span>
        <span className="tb-num bp-stat-mid">{money(total / nights)}</span>
      </div>
      <div className="bp-stat bp-stat-action">
        {hasGoal && (
          <span className="bp-saved-line">
            {money(trip.savedAmount)} saved · {trip.percentFunded}%
          </span>
        )}
        <Link to={`/savings-planner?trip=${trip.tripId}`} className="tb-btn tb-btn-accent">
          {hasGoal ? "View savings" : "Save for this trip"}
          <ArrowRight size={16} aria-hidden="true" />
        </Link>
      </div>
    </div>
  );
}

function Envelopes({ trip, budget }) {
  const editLink = `/trips/${trip.tripId}/edit?tab=budget`;
  const money = makeMoney(budget?.currency || trip.currency);

  if (!budget) return null;

  const { total, items, buffer } = envelopes(budget);
  const rows = [...items, ...(buffer > 0 ? [{ name: "Buffer", amount: buffer, buffer: true }] : [])];
  const pct = (n) => (total > 0 ? Math.round((n / total) * 100) : 0);

  return (
    <section className="bp-envelopes tb-card" aria-labelledby="bp-env-title">
      <div className="bp-env-chart">
        <BudgetDonut
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
        </BudgetDonut>
      </div>

      <div className="bp-env-list">
        <div className="bp-env-head">
          <h2 id="bp-env-title" className="tb-wide">Budget envelopes</h2>
          <Link to={editLink} className="bp-text-link">
            <SlidersHorizontal size={15} aria-hidden="true" /> Adjust caps
          </Link>
        </div>

        {items.length === 0 ? (
          <p className="bp-muted">
            Your {money(total)} budget isn't split into categories yet. <Link to={editLink}>Set category caps</Link>
          </p>
        ) : (
          <ul>
            {rows.map((r) => (
              <li key={r.name} className={`bp-env-row${r.buffer ? " is-buffer" : ""}`} data-category={r.name}>
                <span className="bp-swatch" aria-hidden="true" />
                <span className="bp-env-name">
                  {r.name}
                  <small>{r.buffer ? `${pct(r.amount)}% not assigned` : categoryMeta(r.name).description}</small>
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
        )}
      </div>
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

          {overview && overview.length > 0 && (
            <div className="bp-tabs" role="tablist" aria-label="Trips">
              {upcoming.map((t) => (
                <button
                  key={t.tripId}
                  type="button"
                  role="tab"
                  aria-selected={t.tripId === selectedId}
                  className={`bp-tab${t.tripId === selectedId ? " is-active" : ""}`}
                  onClick={() => navigate(`/budget-planner/${t.tripId}`)}
                >
                  {t.destination?.split(",")[0] || t.name} · {shortDate(t.startDate)}
                </button>
              ))}
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
          )}
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

        {overview && overview.length > 0 && !selected && (
          <p className="bp-muted">
            We couldn't find that trip. <Link to="/budget-planner">Show my next trip</Link>
          </p>
        )}

        {selected && (
          <>
            <div className="bp-top">
              <TripHero trip={selected} />
              {budget === undefined && !budgetError ? (
                <div className="bp-totals is-loading" role="status">Loading budget...</div>
              ) : (
                <Totals trip={selected} budget={budget} />
              )}
            </div>
            {budgetError && <p role="alert" className="error-text">{budgetError}</p>}
            {budget && <Envelopes trip={selected} budget={budget} />}
          </>
        )}
      </main>
    </div>
  );
}
