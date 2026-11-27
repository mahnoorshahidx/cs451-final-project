import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { PencilLine, Plus, X } from "lucide-react";
import Sidebar from "../components/Sidebar";
import ConfirmDialog from "../components/ConfirmDialog";
import SavingsBankCard from "../components/SavingsBankCard";
import SavingsChart from "../components/SavingsChart";
import {
  addContribution,
  deleteContribution,
  getBudgetOverview,
  getSavings,
  saveSavingsGoal,
} from "../api/api";
import { todayIso } from "../lib/formatDate";
import {
  addDays, countdown, daysBetween, makeMoney, savingsForecast, shortDate, splitTrips, suggestedPlan,
} from "../lib/tripMoney";
import "../styles/Dashboard.css";
import "../styles/BudgetPages.css";

// Savings: every upcoming trip on one page.
// Top: total saved with a chart of savings over time (and the pace forecast), next to the bank card.
// Middle: one "jar" per trip, a ring that fills as you save, with one main button.
// Bottom: recent contributions. Forms open in a dialog. /savings-planner?trip=12 highlights that jar.

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

// Ring that fills as the trip is saved for
function Ring({ pct, tone }) {
  const r = 52;
  const c = 2 * Math.PI * r;
  return (
    <svg className={`sv-ring is-${tone}`} viewBox="0 0 120 120" aria-hidden="true">
      <circle className="sv-ring-track" cx="60" cy="60" r={r} />
      {pct > 0 && <circle
        className="sv-ring-fill"
        cx="60"
        cy="60"
        r={r}
        strokeDasharray={`${(Math.min(100, pct) / 100) * c} ${c}`}
        transform="rotate(-90 60 60)"
      />}
    </svg>
  );
}

// One line on what's left: per day when the target is close, per month otherwise
function paceLine(trip, savings, money, today) {
  const left = Math.max(0, Number(trip.savingsTarget) - Number(trip.savedAmount || 0));
  if (left <= 0) return "Fully funded. Enjoy the trip!";
  const target = savings?.targetDate || addDays(trip.startDate, -1);
  const days = daysBetween(today, target);
  if (days <= 0) return `${money(left)} to go. The target date has passed.`;
  if (days < 45) return `${money(left)} to go · about ${money(left / days)} a day for ${days} day${days === 1 ? "" : "s"}`;
  const perMonth = savings?.requiredMonthly ?? left / (days / 30.44);
  return `${money(left)} to go · ${money(perMonth)} a month until ${shortDate(target)}`;
}

function Jar({ trip, savings, highlighted, today, busy, onAdd, onEditGoal, onUseSuggestion }) {
  const money = makeMoney(trip.currency);
  const when = countdown(trip, today);
  const hasGoal = trip.savingsTarget != null;
  const pct = hasGoal ? Math.min(100, trip.percentFunded ?? 0) : 0;
  const status = hasGoal ? STATUS[trip.savingsStatus] ?? STATUS.NO_PLAN : null;
  const suggestion = suggestedPlan(trip, today);
  const ref = useRef(null);
  // The last two contributions, newest first
  const recent = [...(savings?.contributions ?? [])]
    .filter((c) => c.date)
    .sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id)
    .slice(0, 2);

  useEffect(() => {
    if (highlighted) ref.current?.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [highlighted]);

  // Funded by when, at the recent pace (from the backend's projection)
  const projected =
    hasGoal && savings?.projectedCompletionDate && ["ON_PACE", "BEHIND_PACE", "PROJECTED"].includes(savings.projectionStatus)
      ? savings.projectedCompletionDate
      : null;

  return (
    <li ref={ref} className={`sv-jar${highlighted ? " is-highlighted" : ""}`}>
      <div className="sv-jar-top">
        <div>
          <h3>{trip.name}</h3>
          <span className={`sv-when is-${when.tone}`}>{when.label}</span>
        </div>
        {hasGoal && (
          <button type="button" className="sv-icon-btn" onClick={onEditGoal} aria-label={`Edit the ${trip.name} goal`} title="Edit goal">
            <PencilLine size={16} aria-hidden="true" />
          </button>
        )}
      </div>

      <div className="sv-jar-ring">
        <Ring pct={pct} tone={!hasGoal ? "empty" : status.tone} />
        <div className="sv-jar-ring-text">
          {hasGoal ? (
            <>
              <span className="tb-num">{pct}%</span>
              <small>saved</small>
            </>
          ) : (
            <small>No goal yet</small>
          )}
        </div>
      </div>

      {hasGoal ? (
        <div className="sv-jar-info">
          <p className="sv-jar-amount">
            <strong className="tb-num">{money(trip.savedAmount)}</strong> of {money(trip.savingsTarget)}
          </p>
          <span className={`sv-status is-${status.tone}`}>{status.label}</span>
          <p className="sv-jar-line">{paceLine(trip, savings, money, today)}</p>
          {projected && trip.savingsStatus !== "FUNDED" && (
            <p className="sv-jar-line">At your pace: funded by {shortDate(projected)}</p>
          )}
        </div>
      ) : (
        <div className="sv-jar-info">
          <p className="sv-jar-amount">
            {trip.budgetTotal != null ? <>Budget <strong className="tb-num">{money(trip.budgetTotal)}</strong></> : "No budget yet"}
          </p>
          <p className="sv-jar-line">
            {trip.budgetTotal == null
              ? <Link to={`/budget-planner/${trip.tripId}`}>Set a budget</Link>
              : suggestion
                ? <>Suggested: <strong>{money(suggestion.monthly)} a month</strong> for {suggestion.months} month{suggestion.months === 1 ? "" : "s"}</>
                : "Leaving soon, so set a goal for what you can still save."}
          </p>
        </div>
      )}

      <div className="sv-jar-actions">
        {hasGoal ? (
          <button type="button" className="tb-btn tb-btn-primary" onClick={onAdd}>
            <Plus size={16} aria-hidden="true" /> Add money
          </button>
        ) : suggestion ? (
          <>
            <button type="button" className="tb-btn tb-btn-accent" onClick={() => onUseSuggestion(suggestion)} disabled={busy}>
              Start saving
            </button>
            <button type="button" className="sv-text-btn" onClick={onEditGoal}>Custom goal</button>
          </>
        ) : (
          <button type="button" className="tb-btn tb-btn-outline" onClick={onEditGoal}>Set a goal</button>
        )}
      </div>

      {recent.length > 0 && (
        <ul className="sv-jar-recent" aria-label={`Recent savings for ${trip.name}`}>
          {recent.map((c) => (
            <li key={c.id}>
              <span className="tb-num">+{money(c.amount)}</span>
              <span>{c.description || (c.bankAccountId ? "From your bank" : "Added")}</span>
              <time dateTime={c.date}>{shortDate(c.date)}</time>
            </li>
          ))}
        </ul>
      )}
    </li>
  );
}

function FormDialog({ title, onClose, busy, children }) {
  useEffect(() => {
    const onKey = (e) => e.key === "Escape" && !busy && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [busy, onClose]);

  return (
    <div className="tb-dialog-backdrop" onMouseDown={() => !busy && onClose()}>
      <div className="tb-dialog sv-form-dialog" role="dialog" aria-modal="true" aria-labelledby="sv-form-title" onMouseDown={(e) => e.stopPropagation()}>
        <div className="sv-dialog-head">
          <h2 id="sv-form-title" className="tb-wide">{title}</h2>
          <button type="button" className="sv-icon-btn" onClick={onClose} disabled={busy} aria-label="Close">
            <X size={18} aria-hidden="true" />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

export default function SavingsPlanner() {
  const [searchParams] = useSearchParams();
  const highlightId = searchParams.get("trip");

  const [overview, setOverview] = useState(null);
  const [error, setError] = useState("");
  const [savingsByTrip, setSavingsByTrip] = useState({}); // tripId -> savings detail
  const [account, setAccount] = useState(null);
  const [dialog, setDialog] = useState(null); // { tripId, kind: "goal" | "add" }
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState("");
  const [pageError, setPageError] = useState("");
  const [toRemove, setToRemove] = useState(null); // { trip, contribution }
  const [showAll, setShowAll] = useState(false);
  const jarsRef = useRef(null);

  // Overview first, then the savings detail of every trip that has a goal
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

  // Runs a save; closes the dialog and reloads on success, shows the error otherwise
  async function run(action, { inDialog = true } = {}) {
    setBusy(true);
    setFormError("");
    setPageError("");
    try {
      await action();
      setDialog(null);
      await load();
    } catch (err) {
      const message = err.message || "Something went wrong. Please try again.";
      if (inDialog) setFormError(message);
      else setPageError(message);
    } finally {
      setBusy(false);
    }
  }

  const closeDialog = useCallback(() => {
    setDialog(null);
    setFormError("");
  }, []);

  const scrollToJars = useCallback(() => {
    jarsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, []);

  const today = todayIso();
  const { upcoming, past } = splitTrips(overview, today);

  // Totals and chart in the most common currency among upcoming trips
  const currencies = [...new Set(upcoming.map((t) => t.currency))];
  const mainCurrency = currencies.sort(
    (a, b) => upcoming.filter((t) => t.currency === b).length - upcoming.filter((t) => t.currency === a).length
  )[0] || "USD";
  const money = makeMoney(mainCurrency);
  const goalTrips = upcoming.filter((t) => t.savingsTarget != null && t.currency === mainCurrency);
  const goalTotal = goalTrips.reduce((s, t) => s + Number(t.savingsTarget), 0);
  const savedTotal = goalTrips.reduce((s, t) => s + Number(t.savedAmount || 0), 0);
  const lastTarget = goalTrips
    .map((t) => savingsByTrip[t.tripId]?.targetDate || addDays(t.startDate, -1))
    .sort()
    .pop() || today;

  // Dated entries for the chart (money saved before the goal was set counts from the first entry)
  const chartEntries = goalTrips.flatMap((t) => {
    const s = savingsByTrip[t.tripId];
    if (!s) return [];
    const dated = s.contributions.filter((c) => c.date).map((c) => ({ date: c.date, amount: Number(c.amount) }));
    const firstDate = dated.map((c) => c.date).sort()[0] || today;
    return Number(s.initialSavedAmount) > 0 ? [{ date: firstDate, amount: Number(s.initialSavedAmount) }, ...dated] : dated;
  });
  const forecast = savingsForecast(chartEntries, goalTotal, lastTarget, today);

  let forecastNote = "";
  if (goalTotal > 0 && savedTotal >= goalTotal) forecastNote = "Every goal is fully funded.";
  else if (forecast.reachDate && forecast.reachDate <= lastTarget) forecastNote = `At your pace you'll reach ${money(goalTotal)} around ${shortDate(forecast.reachDate)}.`;
  else if (forecast.atEnd != null) forecastNote = `At your pace you'll have about ${money(forecast.atEnd)} by ${shortDate(lastTarget)}, ${money(goalTotal - forecast.atEnd)} short.`;
  else if (goalTotal > 0) forecastNote = "Add money a few times and we'll forecast when you'll be funded.";

  // Recent contributions across every trip, newest first
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
  const shownHistory = showAll ? history : [];

  const dialogTrip = dialog ? tripsById[dialog.tripId] : null;

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
          {overview && goalTotal > 0 && (
            <div className="sv-headline" aria-label="Saved across your trips">
              <span className="tb-kicker">Saved so far</span>
              <p>
                <strong className="tb-num">{money(savedTotal)}</strong> of {money(goalTotal)}
                <span className="sv-overview-pct tb-num">{Math.min(100, Math.round((savedTotal / goalTotal) * 100))}%</span>
              </p>
            </div>
          )}
        </header>

        {error && <p role="alert" className="error-text">{error}</p>}
        {pageError && <p role="alert" className="error-text">{pageError}</p>}
        {!overview && !error && <p role="status" className="bp-muted">Loading your savings...</p>}

        {overview && upcoming.length === 0 && (
          <p className="bp-muted">
            No upcoming trips. <Link to="/create-trip">Plan a trip</Link> to start saving for it.
          </p>
        )}

        <ul ref={jarsRef} className="sv-jars" aria-label="Your trips">
          {upcoming.map((trip) => (
            <Jar
              key={trip.tripId}
              trip={trip}
              savings={savingsByTrip[trip.tripId]}
              highlighted={String(trip.tripId) === highlightId}
              today={today}
              busy={busy}
              onAdd={() => setDialog({ tripId: trip.tripId, kind: "add" })}
              onEditGoal={() => setDialog({ tripId: trip.tripId, kind: "goal" })}
              onUseSuggestion={(s) =>
                run(
                  () =>
                    saveSavingsGoal(trip.tripId, {
                      targetAmount: s.target,
                      initialSavedAmount: 0,
                      targetDate: s.targetDate,
                      plannedMonthlyContribution: s.monthly,
                    }),
                  { inDialog: false }
                )
              }
            />
          ))}
        </ul>

        <div className="sv-bottom">
          <section className="sv-overview tb-card" aria-labelledby="sv-overview-title">
            <div className="sv-overview-head">
              <h2 id="sv-overview-title" className="tb-wide">Your pace</h2>
              {forecastNote && <p className="sv-forecast">{forecastNote}</p>}
            </div>
            {overview && (
              <>
                <SavingsChart entries={chartEntries} goal={goalTotal} endDate={lastTarget} today={today} money={money} />
                <div className="sv-chart-legend">
                  <span><i className="is-saved" /> Saved</span>
                  <span><i className="is-pace" /> At this pace</span>
                  <span><i className="is-goal" /> Goal</span>
                </div>
              </>
            )}
          </section>

          <SavingsBankCard onAccountChange={setAccount} onLogSavings={scrollToJars} />
        </div>

        {history.length === 0 ? (
          null
        ) : (
          <section className="sv-history tb-card" aria-labelledby="sv-history-title">
            <div className="sv-card-head">
              <h2 id="sv-history-title" className="tb-wide">All activity</h2>
              <button type="button" className="sv-text-btn" onClick={() => setShowAll((v) => !v)} aria-expanded={showAll}>
                {showAll ? "Hide" : `Show all ${history.length}`}
              </button>
            </div>
            <ul hidden={!showAll}>
              {shownHistory.map((c) => {
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

      {dialog && dialogTrip && (
        <FormDialog
          title={dialog.kind === "add" ? `Add money · ${dialogTrip.name}` : `${dialogTrip.savingsTarget != null ? "Edit" : "Set"} goal · ${dialogTrip.name}`}
          onClose={closeDialog}
          busy={busy}
        >
          {dialog.kind === "add" ? (
            <LogForm
              account={account}
              busy={busy}
              error={formError}
              onCancel={closeDialog}
              onSave={(contribution) => run(() => addContribution(dialogTrip.tripId, contribution))}
            />
          ) : (
            <GoalForm
              trip={dialogTrip}
              savings={savingsByTrip[dialogTrip.tripId]}
              busy={busy}
              error={formError}
              onCancel={closeDialog}
              onSave={(goal) => run(() => saveSavingsGoal(dialogTrip.tripId, goal))}
            />
          )}
        </FormDialog>
      )}

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
                run(
                  async () => {
                    await deleteContribution(toRemove.trip.tripId, toRemove.contribution.id);
                    setToRemove(null);
                  },
                  { inDialog: false }
                ),
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
