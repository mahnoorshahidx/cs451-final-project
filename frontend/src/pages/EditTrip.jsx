import { useEffect, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import "../styles/EditTrip.css";
import {
  deleteTrip,
  getBudget,
  getTrip,
  listTrips,
  recommendBudget,
  saveBudget,
  updateTrip,
} from "../api/api";
import { BUDGET_CATEGORIES, scaleAllocations } from "../lib/budgetSuggestions";

// Edit an existing trip: /trips/:tripId/edit?tab=budget (default) or ?tab=details

const TABS = [
  { key: "budget", label: "Budget" },
  { key: "details", label: "Trip details" },
];

const CATEGORY_INFO = {
  Flights: { icon: "✈", description: "Round-trip tickets for all travelers" },
  Accommodation: { icon: "⌂", description: "Hotels, rentals and other stays" },
  Food: { icon: "◐", description: "Meals, drinks and snacks" },
  "Local transport": { icon: "⇄", description: "Transfers, transit and car rentals" },
  Activities: { icon: "◇", description: "Tours, attractions and experiences" },
};

const CURRENCIES = ["USD", "EUR", "GBP", "MAD"];

// ---------- helpers (dates stay "YYYY-MM-DD" strings) ----------

function toUtc(iso) {
  const [y, m, d] = iso.split("-").map(Number);
  return Date.UTC(y, m - 1, d);
}

function nightsBetween(start, end) {
  if (!start || !end) return 0;
  return Math.round((toUtc(end) - toUtc(start)) / 86400000);
}

function formatDate(iso) {
  if (!iso) return "";
  return new Date(toUtc(iso)).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  });
}

function detailsFromTrip(trip) {
  return {
    name: trip.name ?? "",
    destination: trip.destination ?? "",
    startDate: trip.startDate ?? "",
    endDate: trip.endDate ?? "",
    numberOfTravelers: String(trip.numberOfTravelers ?? 1),
  };
}

function budgetFromSaved(budget, fallbackCurrency) {
  if (!budget) return { totalBudget: "", currency: fallbackCurrency, allocations: {} };
  return {
    totalBudget: String(budget.totalBudget),
    currency: budget.currency,
    allocations: Object.fromEntries(
      Object.entries(budget.allocations || {}).map(([k, v]) => [k, String(v)])
    ),
  };
}

// Compare forms ignoring empty/zero allocation rows, so "" and "0" don't count as changes
function snapshot(value) {
  if (value.allocations) {
    const allocations = Object.fromEntries(
      Object.entries(value.allocations)
        .filter(([, v]) => Number(v) > 0)
        .map(([k, v]) => [k, Number(v)])
        .sort(([a], [b]) => a.localeCompare(b))
    );
    return JSON.stringify({ ...value, totalBudget: Number(value.totalBudget) || 0, allocations });
  }
  return JSON.stringify(value);
}

function EditTrip() {
  const navigate = useNavigate();
  const { tripId } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = searchParams.get("tab") === "details" ? "details" : "budget";

  const [trips, setTrips] = useState([]);
  const [trip, setTrip] = useState(null);

  const [details, setDetails] = useState(null);
  const [savedDetails, setSavedDetails] = useState("");
  const [budget, setBudget] = useState(null);
  const [savedBudget, setSavedBudget] = useState("");
  const [hasBudget, setHasBudget] = useState(false);

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(""); // "budget" | "details" | "suggest" | "delete"

  // ---------- loading ----------

  useEffect(() => {
    listTrips()
      .then(setTrips)
      .catch(() => {}); // the switcher is optional
  }, []);

  useEffect(() => {
    setLoading(true);
    setLoadError("");
    setError("");
    setNotice("");
    Promise.all([getTrip(tripId), getBudget(tripId)])
      .then(([t, b]) => {
        const d = detailsFromTrip(t);
        const bf = budgetFromSaved(b, "USD");
        setTrip(t);
        setDetails(d);
        setSavedDetails(snapshot(d));
        setBudget(bf);
        setSavedBudget(snapshot(bf));
        setHasBudget(Boolean(b));
      })
      .catch((err) => setLoadError(err.message))
      .finally(() => setLoading(false));
  }, [tripId]);

  const detailsDirty = details !== null && snapshot(details) !== savedDetails;
  const budgetDirty = budget !== null && snapshot(budget) !== savedBudget;
  const anyDirty = detailsDirty || budgetDirty;

  // Warn before closing the tab with unsaved changes
  useEffect(() => {
    if (!anyDirty) return;
    const warn = (e) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [anyDirty]);

  // ---------- navigation ----------

  function confirmLeave() {
    return !anyDirty || window.confirm("You have unsaved changes. Leave without saving?");
  }

  function selectTab(key) {
    setError("");
    setNotice("");
    setSearchParams({ tab: key }, { replace: true });
  }

  function switchTrip(id) {
    if (String(id) === String(tripId) || !confirmLeave()) return;
    navigate(`/trips/${id}/edit?tab=${tab}`);
  }

  if (loading) return <p className="et-message">Loading trip...</p>;

  if (loadError || !trip || !details || !budget) {
    return (
      <div className="et-page">
        <p className="et-message">
          {loadError || "Trip not found."} <Link to="/trips">Back to My Trips</Link>
        </p>
      </div>
    );
  }

  // ---------- budget tab ----------

  const total = Number(budget.totalBudget) || 0;
  const travelers = Number(trip.numberOfTravelers) || 1;
  const categories = [
    ...BUDGET_CATEGORIES,
    ...Object.keys(budget.allocations).filter((c) => !BUDGET_CATEGORIES.includes(c)),
  ];
  const allocated = categories.reduce((sum, c) => sum + (Number(budget.allocations[c]) || 0), 0);
  const remaining = total - allocated;
  const percent = total > 0 ? (allocated / total) * 100 : 0;

  const money = (n) =>
    new Intl.NumberFormat("en-US", { style: "currency", currency: budget.currency, maximumFractionDigits: 0 }).format(n);

  function updateBudget(field, value) {
    setBudget((b) => ({ ...b, [field]: value }));
    setError("");
    setNotice("");
  }

  function updateAllocation(category, value) {
    setBudget((b) => ({ ...b, allocations: { ...b.allocations, [category]: value } }));
    setError("");
    setNotice("");
  }

  async function suggestCaps() {
    setBusy("suggest");
    setError("");
    setNotice("");
    try {
      const rec = await recommendBudget({
        destination: trip.destination,
        startDate: trip.startDate || null,
        nights: Math.max(1, nightsBetween(trip.startDate, trip.endDate)),
        travelers,
        tradeOffs: (trip.tradeOffs || []).filter((t) => t !== "none"),
        currency: budget.currency,
      });
      const target = total > 0 ? total : Number(rec.recommendedTotal);
      const caps = scaleAllocations(rec, target);
      setBudget((b) => ({
        ...b,
        totalBudget: String(target),
        allocations: Object.fromEntries(Object.entries(caps).map(([k, v]) => [k, String(v)])),
      }));
      setNotice(
        total > 0
          ? "Suggested caps filled in for your total. Review them, then save."
          : `Suggested a ${money(target)} budget with caps. Review them, then save.`
      );
    } catch (err) {
      setError(`Couldn't get suggestions: ${err.message}`);
    } finally {
      setBusy("");
    }
  }

  async function handleSaveBudget(e) {
    e.preventDefault();
    if (total <= 0) return setError("Enter a total budget greater than 0.");
    if (allocated > total) return setError("Your category caps add up to more than your total budget.");

    setBusy("budget");
    setError("");
    setNotice("");
    try {
      const allocations = Object.fromEntries(
        Object.entries(budget.allocations)
          .filter(([, v]) => Number(v) > 0)
          .map(([k, v]) => [k, Number(v)])
      );
      const saved = await saveBudget(tripId, { totalBudget: total, currency: budget.currency, allocations });
      const bf = budgetFromSaved(saved, budget.currency);
      setBudget(bf);
      setSavedBudget(snapshot(bf));
      setHasBudget(true);
      setNotice("Budget saved.");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy("");
    }
  }

  // ---------- details tab ----------

  function updateDetail(field, value) {
    setDetails((d) => ({ ...d, [field]: value }));
    setError("");
    setNotice("");
  }

  async function handleSaveDetails(e) {
    e.preventDefault();
    const n = Number(details.numberOfTravelers);
    if (!details.name.trim()) return setError("Trip name is required.");
    if (!details.destination.trim()) return setError("Destination is required.");
    if (!details.startDate || !details.endDate) return setError("Start and end dates are required.");
    if (details.endDate < details.startDate) return setError("End date can't be before the start date.");
    if (!Number.isInteger(n) || n < 1) return setError("At least 1 traveler.");

    setBusy("details");
    setError("");
    setNotice("");
    try {
      const updated = await updateTrip(tripId, {
        name: details.name.trim(),
        destination: details.destination.trim(),
        startDate: details.startDate,
        endDate: details.endDate,
        numberOfTravelers: n,
      });
      const d = detailsFromTrip(updated);
      setTrip(updated);
      setDetails(d);
      setSavedDetails(snapshot(d));
      setTrips((list) => list.map((t) => (String(t.id) === String(tripId) ? updated : t)));
      setNotice("Trip details saved.");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy("");
    }
  }

  async function handleDelete() {
    if (!window.confirm(`Delete "${trip.name}"? Its budget will be deleted too. This can't be undone.`)) return;
    setBusy("delete");
    try {
      await deleteTrip(tripId);
      navigate("/trips");
    } catch (err) {
      setError(err.message);
      setBusy("");
    }
  }

  const nights = nightsBetween(trip.startDate, trip.endDate);

  return (
    <div className="et-page">
      <header className="et-topbar">
        <div className="et-brand">
          <span className="et-logo">W</span>
          <span>Wayfare</span>
        </div>
        <Link to="/trips" className="et-back" onClick={(e) => !confirmLeave() && e.preventDefault()}>
          ← My trips
        </Link>
      </header>

      <section className="et-head">
        <div className="et-head-inner">
          <div className="et-head-row">
            <div>
              <p className="et-eyebrow">Edit trip</p>
              <h1>{trip.name}</h1>
              <p className="et-meta">
                {trip.destination} · {formatDate(trip.startDate)} – {formatDate(trip.endDate)}
                {nights > 0 && ` · ${nights} night${nights === 1 ? "" : "s"}`} · {travelers} traveler
                {travelers === 1 ? "" : "s"}
              </p>
            </div>

            {trips.length > 1 && (
              <label className="et-switcher">
                <span>Switch trip</span>
                <select value={tripId} onChange={(e) => switchTrip(e.target.value)}>
                  {trips.map((t) => (
                    <option key={t.id} value={t.id}>{t.name}</option>
                  ))}
                </select>
              </label>
            )}
          </div>

          <nav className="et-tabs" role="tablist" aria-label="Edit sections">
            {TABS.map((t) => {
              const dirty = t.key === "budget" ? budgetDirty : detailsDirty;
              return (
                <button
                  key={t.key}
                  type="button"
                  role="tab"
                  aria-selected={tab === t.key}
                  className={`et-tab ${tab === t.key ? "is-active" : ""}`}
                  onClick={() => selectTab(t.key)}
                >
                  {t.label}
                  {dirty && <span className="et-dot" aria-label="unsaved changes" />}
                </button>
              );
            })}
          </nav>
        </div>
      </section>

      <main className="et-main">
        {tab === "budget" && (
          <form className="et-budget" onSubmit={handleSaveBudget} noValidate>
            <section className="et-panel">
              <div className="et-panel-head">
                <div>
                  <h2>Budget</h2>
                  <p className="et-sub">
                    {hasBudget ? "Adjust your total and category caps." : "This trip has no budget yet. Set one below."}
                  </p>
                </div>
                <button type="button" className="et-ghost" onClick={suggestCaps} disabled={busy !== ""}>
                  {busy === "suggest" ? "Suggesting…" : "✦ Suggest caps"}
                </button>
              </div>

              <div className="et-total-row">
                <div className="et-field">
                  <label htmlFor="totalBudget">Total trip budget</label>
                  <input
                    id="totalBudget"
                    type="number"
                    min="0"
                    inputMode="decimal"
                    placeholder="0"
                    value={budget.totalBudget}
                    onChange={(e) => updateBudget("totalBudget", e.target.value)}
                  />
                </div>
                <div className="et-field">
                  <label htmlFor="currency">Currency</label>
                  <select
                    id="currency"
                    value={budget.currency}
                    onChange={(e) => updateBudget("currency", e.target.value)}
                  >
                    {CURRENCIES.map((c) => (
                      <option key={c} value={c}>{c}</option>
                    ))}
                  </select>
                </div>
              </div>

              <h3>Category caps</h3>
              <ul className="et-categories">
                {categories.map((c) => {
                  const info = CATEGORY_INFO[c] ?? { icon: "•", description: "Custom category" };
                  const amount = Number(budget.allocations[c]) || 0;
                  return (
                    <li key={c} className="et-category">
                      <span className="et-cat-icon" aria-hidden="true">{info.icon}</span>
                      <div className="et-cat-text">
                        <strong>{c}</strong>
                        <span>{info.description}</span>
                      </div>
                      <span className="et-cat-pct">{total > 0 && amount > 0 ? `${Math.round((amount / total) * 100)}%` : ""}</span>
                      <input
                        type="number"
                        min="0"
                        placeholder="0"
                        aria-label={`${c} cap`}
                        value={budget.allocations[c] ?? ""}
                        onChange={(e) => updateAllocation(c, e.target.value)}
                      />
                    </li>
                  );
                })}
              </ul>
            </section>

            <aside className="et-panel et-summary">
              <h2>Breakdown</h2>
              <p className="et-summary-total">{money(total)}</p>
              {travelers > 1 && total > 0 && <p className="et-sub">{money(total / travelers)} per traveler</p>}

              <div className="et-progress" aria-hidden="true">
                <div
                  className={`et-progress-fill ${remaining < 0 ? "is-over" : ""}`}
                  style={{ width: `${Math.min(percent, 100)}%` }}
                />
              </div>
              <p className="et-progress-label">
                {money(allocated)} of {money(total)} planned ({percent.toFixed(0)}%)
              </p>

              <div className={`et-remaining ${remaining < 0 ? "is-over" : ""}`}>
                <span>{remaining < 0 ? "Over budget by" : "Unallocated buffer"}</span>
                <strong>{money(Math.abs(remaining))}</strong>
              </div>

              {error && <p className="et-error" role="alert">{error}</p>}
              {notice && <p className="et-notice" role="status">{notice}</p>}

              <button type="submit" className="et-primary" disabled={busy !== "" || !budgetDirty}>
                {busy === "budget" ? "Saving…" : budgetDirty ? "Save budget" : "Saved"}
              </button>
              {budgetDirty && (
                <button
                  type="button"
                  className="et-link"
                  onClick={() => setBudget(budgetFromSavedSnapshot(savedBudget, budget.currency))}
                >
                  Discard changes
                </button>
              )}
            </aside>
          </form>
        )}

        {tab === "details" && (
          <form className="et-panel et-details" onSubmit={handleSaveDetails} noValidate>
            <h2>Trip details</h2>
            <p className="et-sub">Changing dates or travelers doesn't change your budget. Use “Suggest caps” on the Budget tab to re-estimate.</p>

            <div className="et-grid">
              <div className="et-field et-span-2">
                <label htmlFor="name">Trip name</label>
                <input id="name" type="text" value={details.name} onChange={(e) => updateDetail("name", e.target.value)} />
              </div>
              <div className="et-field et-span-2">
                <label htmlFor="destination">Destination</label>
                <input
                  id="destination"
                  type="text"
                  value={details.destination}
                  onChange={(e) => updateDetail("destination", e.target.value)}
                />
              </div>
              <div className="et-field">
                <label htmlFor="startDate">Start date</label>
                <input
                  id="startDate"
                  type="date"
                  value={details.startDate}
                  onChange={(e) => updateDetail("startDate", e.target.value)}
                />
              </div>
              <div className="et-field">
                <label htmlFor="endDate">End date</label>
                <input
                  id="endDate"
                  type="date"
                  min={details.startDate}
                  value={details.endDate}
                  onChange={(e) => updateDetail("endDate", e.target.value)}
                />
              </div>
              <div className="et-field">
                <label htmlFor="travelers">Travelers</label>
                <input
                  id="travelers"
                  type="number"
                  min="1"
                  step="1"
                  value={details.numberOfTravelers}
                  onChange={(e) => updateDetail("numberOfTravelers", e.target.value)}
                />
              </div>
            </div>

            {error && <p className="et-error" role="alert">{error}</p>}
            {notice && <p className="et-notice" role="status">{notice}</p>}

            <div className="et-actions">
              <button type="button" className="et-danger" onClick={handleDelete} disabled={busy !== ""}>
                {busy === "delete" ? "Deleting…" : "Delete trip"}
              </button>
              <div className="et-actions-right">
                {detailsDirty && (
                  <button type="button" className="et-link" onClick={() => setDetails(JSON.parse(savedDetails))}>
                    Discard changes
                  </button>
                )}
                <button type="submit" className="et-primary" disabled={busy !== "" || !detailsDirty}>
                  {busy === "details" ? "Saving…" : detailsDirty ? "Save details" : "Saved"}
                </button>
              </div>
            </div>
          </form>
        )}
      </main>
    </div>
  );
}

// Rebuild the editable budget form from a saved snapshot string
function budgetFromSavedSnapshot(snap, currency) {
  const s = JSON.parse(snap);
  return {
    totalBudget: s.totalBudget ? String(s.totalBudget) : "",
    currency: s.currency || currency,
    allocations: Object.fromEntries(Object.entries(s.allocations || {}).map(([k, v]) => [k, String(v)])),
  };
}

export default EditTrip;
