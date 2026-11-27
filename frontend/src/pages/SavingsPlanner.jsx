import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { History, X } from "lucide-react";
import Sidebar from "../components/Sidebar";
import ConfirmDialog from "../components/ConfirmDialog";
import SavingsBankCard from "../components/SavingsBankCard";
import {
  addContribution,
  deleteContribution,
  getBudgetOverview,
  getSavings,
  saveSavingsGoal,
} from "../api/api";
import useDestinationPhoto from "../lib/useDestinationPhoto";
import { todayIso } from "../lib/formatDate";
import { addDays, countdown, makeMoney, shortDate, splitTrips, suggestedPlan } from "../lib/tripMoney";
import "../styles/Dashboard.css";
import "../styles/BudgetPages.css";

// Savings: the bank connection, how much is saved across trips, a goal row per upcoming trip
// with a suggested monthly amount, and every contribution in one history list.
// /savings-planner?trip=12 scrolls to and highlights that trip's row.

const STATUS = {
  FUNDED: { label: "Fully funded", tone: "good" },
  ON_TRACK: { label: "On track", tone: "good" },
  BEHIND: { label: "Behind plan", tone: "warn" },
  NO_PLAN: { label: "No monthly plan", tone: "neutral" },
  PAST_DUE: { label: "Target date passed", tone: "warn" },
};

function GoalForm({ trip, savings, onCancel, onSave, busy, error }) {
  const today = todayIso();
  const [form, setForm] = useState(() =>
    savings
      ? {
          targetAmount: String(savings.targetAmount),
          initialSavedAmount: String(savings.initialSavedAmount ?? 0),
          targetDate: savings.targetDate,
          plannedMonthlyContribution:
            savings.plannedMonthlyContribution != null ? String(savings.plannedMonthlyContribution) : "",
        }
      : {
          targetAmount: trip.budgetTotal != null ? String(Math.round(Number(trip.budgetTotal))) : "",
          initialSavedAmount: "",
          // Aim to be funded the day before you leave
          targetDate: trip.startDate > today ? addDays(trip.startDate, -1) : addDays(today, 30),
          plannedMonthlyContribution: "",
        }
  );
  const [localError, setLocalError] = useState("");
  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  function submit(e) {
    e.preventDefault();
    const target = Number(form.targetAmount);
    if (!(target > 0)) return setLocalError("Enter a savings target greater than 0.");
    if (!form.targetDate) return setLocalError("Choose a target date.");
    setLocalError("");
    onSave({
      targetAmount: target,
      initialSavedAmount: Number(form.initialSavedAmount) || 0,
      targetDate: form.targetDate,
      plannedMonthlyContribution: form.plannedMonthlyContribution === "" ? null : Number(form.plannedMonthlyContribution),
    });
  }

  return (
    <form className="sv-form" onSubmit={submit} noValidate>
      <div className="sv-form-grid">
        <label className="tb-field">
          Savings target
          <input className="tb-input" type="number" min="0" inputMode="decimal" value={form.targetAmount} onChange={set("targetAmount")} />
        </label>
        <label className="tb-field">
          Target date
          <input className="tb-input" type="date" value={form.targetDate} onChange={set("targetDate")} />
        </label>
        <label className="tb-field">
          <span>Already saved <em>(optional)</em></span>
          <input className="tb-input" type="number" min="0" inputMode="decimal" placeholder="0" value={form.initialSavedAmount} onChange={set("initialSavedAmount")} />
        </label>
        <label className="tb-field">
          <span>Monthly plan <em>(optional)</em></span>
          <input className="tb-input" type="number" min="0" inputMode="decimal" placeholder="e.g. 300" value={form.plannedMonthlyContribution} onChange={set("plannedMonthlyContribution")} />
        </label>
      </div>
      {(localError || error) && <p role="alert" className="error-text">{localError || error}</p>}
      <div className="sv-form-actions">
        <button type="submit" className="tb-btn tb-btn-primary" disabled={busy}>{busy ? "Saving..." : "Save goal"}</button>
        <button type="button" className="tb-btn tb-btn-ghost" onClick={onCancel} disabled={busy}>Cancel</button>
      </div>
    </form>
  );
}

function LogForm({ account, onCancel, onSave, busy, error }) {
  const today = todayIso();
  const [form, setForm] = useState({ amount: "", date: today, description: "", fromBank: Boolean(account) });
  const [localError, setLocalError] = useState("");
  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.type === "checkbox" ? e.target.checked : e.target.value }));

  function submit(e) {
    e.preventDefault();
    const amount = Number(form.amount);
    if (!(amount > 0)) return setLocalError("Enter an amount greater than 0.");
    setLocalError("");
    onSave({
      amount,
      date: form.date || null,
      description: form.description.trim() || null,
      bankAccountId: account && form.fromBank ? account.id : null,
    });
  }

  return (
    <form className="sv-form" onSubmit={submit} noValidate>
      <div className="sv-form-grid">
        <label className="tb-field">
          Amount
          <input className="tb-input" type="number" min="0" inputMode="decimal" autoFocus value={form.amount} onChange={set("amount")} />
        </label>
        <label className="tb-field">
          Date
          <input className="tb-input" type="date" max={today} value={form.date} onChange={set("date")} />
        </label>
        <label className="tb-field sv-span-2">
          <span>Note <em>(optional)</em></span>
          <input className="tb-input" type="text" maxLength={255} placeholder="e.g. November paycheck" value={form.description} onChange={set("description")} />
        </label>
        {account && (
          <label className="sv-check sv-span-2">
            <input type="checkbox" checked={form.fromBank} onChange={set("fromBank")} />
            From my connected account ••{account.accountLast4}
          </label>
        )}
      </div>
      {(localError || error) && <p role="alert" className="error-text">{localError || error}</p>}
      <div className="sv-form-actions">
        <button type="submit" className="tb-btn tb-btn-primary" disabled={busy}>{busy ? "Saving..." : "Add savings"}</button>
        <button type="button" className="tb-btn tb-btn-ghost" onClick={onCancel} disabled={busy}>Cancel</button>
      </div>
    </form>
  );
}

function GoalRow({ trip, savings, highlighted, open, onOpen, onClose, onUseSuggestion, account, busy, error, onSaveGoal, onLog }) {
  const [photo, clearPhoto] = useDestinationPhoto(trip.destination);
  const money = makeMoney(trip.currency);
  const when = countdown(trip);
  const suggestion = suggestedPlan(trip);
  const hasGoal = trip.savingsTarget != null;
  const status = hasGoal ? STATUS[trip.savingsStatus] ?? STATUS.NO_PLAN : null;
  const pct = hasGoal ? Math.min(100, trip.percentFunded ?? 0) : 0;
  const rowRef = useRef(null);

  useEffect(() => {
    if (highlighted) rowRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [highlighted]);

  let detail;
  if (hasGoal) {
    detail = (
      <>
        <span className="sv-goal-numbers">
          <strong>{money(trip.savedAmount)}</strong> of {money(trip.savingsTarget)} saved
          <span className={`sv-status is-${status.tone}`}>{status.label}</span>
        </span>
        {trip.savingsStatus !== "FUNDED" && savings?.requiredMonthly != null && (
          <span className="sv-goal-hint">
            Need <strong>{money(savings.requiredMonthly)}/month</strong> to reach it by {shortDate(savings.targetDate)}
          </span>
        )}
      </>
    );
  } else if (trip.budgetTotal == null) {
    detail = (
      <span className="sv-goal-hint">
        No budget yet. <Link to={`/budget-planner/${trip.tripId}`}>Set a budget</Link> to get a suggestion.
      </span>
    );
  } else if (!suggestion) {
    detail = <span className="sv-goal-hint">Too close to set a monthly plan. You can still log what you've saved.</span>;
  } else {
    detail = (
      <span className="sv-goal-hint">
        Suggested: <strong>{money(suggestion.monthly)} / month</strong> for {suggestion.months} month{suggestion.months === 1 ? "" : "s"}
      </span>
    );
  }

  return (
    <li ref={rowRef} className={`sv-goal${highlighted ? " is-highlighted" : ""}`}>
      <div className="sv-goal-main">
        <Link to={`/budget-planner/${trip.tripId}`} className="sv-thumb" tabIndex={-1} aria-hidden="true">
          {photo?.imageUrl && <img src={photo.imageUrl} alt="" loading="lazy" onError={clearPhoto} />}
        </Link>
        <div className="sv-goal-name">
          <strong>{trip.name}</strong>
          <span>
            <span className={`sv-when is-${when.tone}`}>{when.label}</span>
            {trip.budgetTotal != null ? ` · ${money(trip.budgetTotal)} budget` : ""}
          </span>
        </div>
        <div className="sv-goal-progress">
          <div className="sv-bar" role="img" aria-label={hasGoal ? `${pct}% saved` : "No savings goal yet"}>
            <span style={{ width: `${pct}%` }} className={pct >= 100 ? "is-full" : ""} />
          </div>
          {detail}
        </div>
        <div className="sv-goal-actions">
          {hasGoal ? (
            <>
              <button type="button" className="tb-btn tb-btn-outline" onClick={() => onOpen("log")} aria-expanded={open === "log"}>
                Log savings
              </button>
              <button type="button" className="sv-text-btn" onClick={() => onOpen("goal")} aria-expanded={open === "goal"}>
                Edit goal
              </button>
            </>
          ) : suggestion ? (
            <>
              <button type="button" className="tb-btn tb-btn-accent" onClick={() => onUseSuggestion(suggestion)} disabled={busy}>
                Use suggestion
              </button>
              <button type="button" className="sv-text-btn" onClick={() => onOpen("goal")} aria-expanded={open === "goal"}>
                Custom goal
              </button>
            </>
          ) : (
            <button type="button" className="tb-btn tb-btn-primary" onClick={() => onOpen("goal")} aria-expanded={open === "goal"}>
              Set goal
            </button>
          )}
        </div>
      </div>

      {open && (
        <div className="sv-goal-panel">
          <div className="sv-goal-panel-head">
            <strong>{open === "goal" ? (hasGoal ? "Edit savings goal" : "Set a savings goal") : `Log savings for ${trip.name}`}</strong>
            <button type="button" className="sv-icon-btn" onClick={onClose} aria-label="Close">
              <X size={16} aria-hidden="true" />
            </button>
          </div>
          {open === "goal" ? (
            <GoalForm trip={trip} savings={savings} busy={busy} error={error} onCancel={onClose} onSave={onSaveGoal} />
          ) : (
            <LogForm account={account} busy={busy} error={error} onCancel={onClose} onSave={onLog} />
          )}
        </div>
      )}
    </li>
  );
}

export default function SavingsPlanner() {
  const [searchParams] = useSearchParams();
  const highlightId = searchParams.get("trip");

  const [overview, setOverview] = useState(null);
  const [error, setError] = useState("");
  const [savingsByTrip, setSavingsByTrip] = useState({}); // tripId -> savings detail
  const [account, setAccount] = useState(null);
  const [openRow, setOpenRow] = useState(null); // { tripId, kind: "goal" | "log" }
  const [busy, setBusy] = useState(false);
  const [rowError, setRowError] = useState("");
  const [toRemove, setToRemove] = useState(null); // { trip, contribution }
  const goalsRef = useRef(null);

  // Overview first, then the savings detail of every trip that has a goal (for the history)
  const load = useCallback(async () => {
    try {
      const trips = await getBudgetOverview();
      setOverview(trips);
      const withGoal = trips.filter((t) => t.savingsTarget != null);
      const details = await Promise.all(withGoal.map((t) => getSavings(t.tripId)));
      setSavingsByTrip(Object.fromEntries(withGoal.map((t, i) => [t.tripId, details[i]])));
    } catch (err) {
      setError(err.message || "Could not load your savings.");
    }
  }, []);

  useEffect(() => {
    // load() only sets state after its requests finish
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  async function run(action) {
    setBusy(true);
    setRowError("");
    try {
      await action();
      setOpenRow(null);
      await load();
    } catch (err) {
      setRowError(err.message || "Something went wrong. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  const { upcoming, past } = splitTrips(overview);
  const today = todayIso();

  // Totals in the most common currency among upcoming trips
  const currencies = [...new Set(upcoming.map((t) => t.currency))];
  const mainCurrency = currencies.sort(
    (a, b) => upcoming.filter((t) => t.currency === b).length - upcoming.filter((t) => t.currency === a).length
  )[0] || "USD";
  const inMain = upcoming.filter((t) => t.currency === mainCurrency);
  const money = makeMoney(mainCurrency);
  const savedTotal = inMain.reduce((s, t) => s + Number(t.savedAmount || 0), 0);
  const budgetedTotal = inMain.reduce((s, t) => s + Number(t.budgetTotal || 0), 0);
  const funded = upcoming.filter((t) => t.savingsStatus === "FUNDED").length;
  const withGoalCount = upcoming.filter((t) => t.savingsTarget != null).length;
  const savedPct = budgetedTotal > 0 ? Math.min(100, Math.round((savedTotal / budgetedTotal) * 100)) : 0;

  // Every contribution across trips, newest first
  const tripsById = Object.fromEntries((overview || []).map((t) => [t.tripId, t]));
  const history = Object.entries(savingsByTrip)
    .flatMap(([id, s]) => {
      const trip = tripsById[id];
      if (!s || !trip) return [];
      const items = s.contributions.map((c) => ({ ...c, trip }));
      if (Number(s.initialSavedAmount) > 0) {
        items.push({ id: `start-${id}`, amount: s.initialSavedAmount, date: null, description: "Already saved when the goal was set", trip, initial: true });
      }
      return items;
    })
    .sort((a, b) => (b.date || "0000").localeCompare(a.date || "0000"));

  const scrollToGoals = useCallback(() => {
    goalsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, []);

  return (
    <div className="dashboard-page bp-page">
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      <main className="dashboard-main bp-main">
        <header className="bp-header">
          <div className="bp-title">
            <span className="tb-kicker">Savings</span>
            <h1 className="tb-wide">Saving up for every trip</h1>
          </div>
        </header>

        {error && <p role="alert" className="error-text">{error}</p>}

        <div className="sv-top">
          <SavingsBankCard onAccountChange={setAccount} onLogSavings={scrollToGoals} />

          <section className="sv-total tb-card" aria-labelledby="sv-total-title">
            <span id="sv-total-title" className="tb-kicker">Saved across all trips</span>
            {!overview ? (
              <p className="bp-muted" role="status">Loading...</p>
            ) : (
              <>
                <div className="sv-total-amount">
                  <span className="tb-num">{money(savedTotal)}</span>
                  <span>of {money(budgetedTotal)} budgeted</span>
                </div>
                <div className="sv-bar is-big" role="img" aria-label={`${savedPct}% of your budgets saved`}>
                  <span style={{ width: `${savedPct}%` }} className={savedPct >= 100 ? "is-full" : ""} />
                </div>
                <span className="bp-muted">
                  {withGoalCount === 0
                    ? "Set a goal on a trip below to get a monthly target."
                    : `${funded} of ${upcoming.length} upcoming trip${upcoming.length === 1 ? "" : "s"} fully funded`}
                  {currencies.length > 1 ? ` · ${mainCurrency} trips only` : ""}
                </span>
              </>
            )}
          </section>
        </div>

        <section ref={goalsRef} className="sv-goals tb-card" aria-labelledby="sv-goals-title">
          <div className="sv-card-head">
            <h2 id="sv-goals-title" className="tb-wide">Trip goals</h2>
            {overview && <span className="bp-muted">{upcoming.length} upcoming</span>}
          </div>
          {overview && upcoming.length === 0 && (
            <p className="bp-muted sv-pad">
              No upcoming trips. <Link to="/create-trip">Plan a trip</Link> to start saving for it.
            </p>
          )}
          <ul>
            {upcoming.map((trip) => (
              <GoalRow
                key={trip.tripId}
                trip={trip}
                savings={savingsByTrip[trip.tripId]}
                highlighted={String(trip.tripId) === highlightId}
                open={openRow?.tripId === trip.tripId ? openRow.kind : null}
                onOpen={(kind) => {
                  setRowError("");
                  setOpenRow({ tripId: trip.tripId, kind });
                }}
                onClose={() => setOpenRow(null)}
                account={account}
                busy={busy}
                error={openRow?.tripId === trip.tripId ? rowError : ""}
                onUseSuggestion={(s) =>
                  run(() =>
                    saveSavingsGoal(trip.tripId, {
                      targetAmount: s.target,
                      initialSavedAmount: 0,
                      targetDate: s.targetDate,
                      plannedMonthlyContribution: s.monthly,
                    })
                  )
                }
                onSaveGoal={(goal) => run(() => saveSavingsGoal(trip.tripId, goal))}
                onLog={(contribution) => run(() => addContribution(trip.tripId, contribution))}
              />
            ))}
          </ul>
          {rowError && !openRow && <p role="alert" className="error-text sv-pad">{rowError}</p>}
        </section>

        {history.length === 0 ? (
          <section className="sv-history-empty" aria-label="Savings history">
            <History size={28} aria-hidden="true" />
            <div>
              <strong>Savings history</strong>
              <span>Every deposit toward a trip will show up here.</span>
            </div>
          </section>
        ) : (
          <section className="sv-history tb-card" aria-labelledby="sv-history-title">
            <div className="sv-card-head">
              <h2 id="sv-history-title" className="tb-wide">Savings history</h2>
              <span className="bp-muted">{history.length} entr{history.length === 1 ? "y" : "ies"}</span>
            </div>
            <ul>
              {history.map((c) => {
                const m = makeMoney(c.trip.currency);
                const isPast = past.some((t) => t.tripId === c.trip.tripId);
                return (
                  <li key={c.id} className="sv-history-row">
                    <span className="sv-history-date">{c.date ? shortDate(c.date, c.date.slice(0, 4) !== today.slice(0, 4)) : "Start"}</span>
                    <span className="sv-history-desc">
                      {c.description || "Savings contribution"}
                      <small>
                        {c.trip.name}
                        {isPast ? " · past trip" : ""}
                        {c.bankAccountId ? " · from your bank" : ""}
                      </small>
                    </span>
                    <strong className="tb-num sv-history-amount">+{m(c.amount)}</strong>
                    {!c.initial && c.canDelete ? (
                      <button
                        type="button"
                        className="sv-icon-btn"
                        aria-label={`Remove the ${m(c.amount)} contribution`}
                        onClick={() => setToRemove({ trip: c.trip, contribution: c })}
                      >
                        <X size={16} aria-hidden="true" />
                      </button>
                    ) : (
                      <span />
                    )}
                  </li>
                );
              })}
            </ul>
          </section>
        )}
      </main>

      {toRemove && (
        <ConfirmDialog
          title="Remove this contribution?"
          busy={busy}
          onCancel={() => setToRemove(null)}
          actions={[
            {
              label: busy ? "Removing..." : "Remove",
              tone: "danger",
              onClick: () =>
                run(async () => {
                  await deleteContribution(toRemove.trip.tripId, toRemove.contribution.id);
                  setToRemove(null);
                }),
            },
          ]}
        >
          <p>
            {makeMoney(toRemove.trip.currency)(toRemove.contribution.amount)} for {toRemove.trip.name}
            {toRemove.contribution.date ? ` on ${shortDate(toRemove.contribution.date, true)}` : ""} comes off your saved total.
          </p>
        </ConfirmDialog>
      )}
    </div>
  );
}
