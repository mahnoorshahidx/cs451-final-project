import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import "../styles/BudgetDashboard.css";
import Sidebar from "../components/Sidebar";
import {
  addContribution,
  deleteContribution,
  getBudget,
  getBudgetOverview,
  getSavings,
  saveSavingsGoal,
} from "../api/api";
import { BUDGET_CATEGORIES } from "../lib/budgetSuggestions";
import { categoryMeta } from "../lib/budgetCategories";

// Budget & savings: /budget (next upcoming trip) or /budget/:tripId
// Top: totals across upcoming trips. Left: every trip with its savings progress.
// Right: the selected trip's savings goal, contributions and budget envelopes.

// ---------- helpers (dates stay "YYYY-MM-DD" strings) ----------

function todayIso() {
  const now = new Date();
  return [now.getFullYear(), String(now.getMonth() + 1).padStart(2, "0"), String(now.getDate()).padStart(2, "0")].join("-");
}

function toUtc(iso) {
  const [y, m, d] = iso.split("-").map(Number);
  return Date.UTC(y, m - 1, d);
}

function daysBetween(start, end) {
  if (!start || !end) return 0;
  return Math.round((toUtc(end) - toUtc(start)) / 86400000);
}

function formatDate(iso, withYear = true) {
  if (!iso) return "";
  return new Date(toUtc(iso)).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    ...(withYear && { year: "numeric" }),
    timeZone: "UTC",
  });
}

function countdown(trip) {
  const today = todayIso();
  if (trip.endDate < today) return "Completed";
  if (trip.startDate <= today) return "Happening now";
  const days = daysBetween(today, trip.startDate);
  return days === 1 ? "Tomorrow" : `In ${days} days`;
}

function makeMoney(currency) {
  const fmt = new Intl.NumberFormat("en-US", { style: "currency", currency: currency || "USD", maximumFractionDigits: 0 });
  return (n) => fmt.format(Number(n) || 0);
}

const STATUS = {
  FUNDED: { label: "Fully funded", cls: "is-good" },
  ON_TRACK: { label: "On track", cls: "is-good" },
  BEHIND: { label: "Behind plan", cls: "is-warn" },
  NO_PLAN: { label: "No monthly plan", cls: "is-neutral" },
  PAST_DUE: { label: "Target date passed", cls: "is-warn" },
};

// ---------- donut chart ----------

function Donut({ segments, size = 200, stroke = 30, label, children }) {
  const r = (size - stroke) / 2;
  const circumference = 2 * Math.PI * r;
  const sum = segments.reduce((s, seg) => s + seg.value, 0) || 1;
  const gap = segments.length > 1 ? 3 : 0;
  let offset = 0;

  return (
    <div className="bd-donut" style={{ width: size, height: size }}>
      <svg viewBox={`0 0 ${size} ${size}`} role="img" aria-label={label}>
        <circle className="bd-donut-track" cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke} />
        {segments.map((seg) => {
          const length = (seg.value / sum) * circumference;
          const circle = (
            <circle
              key={seg.key}
              className={`bd-seg ${seg.cls}`}
              cx={size / 2}
              cy={size / 2}
              r={r}
              fill="none"
              strokeWidth={stroke}
              strokeDasharray={`${Math.max(length - gap, 0.01)} ${circumference}`}
              strokeDashoffset={-offset}
              transform={`rotate(-90 ${size / 2} ${size / 2})`}
            >
              <title>{seg.title}</title>
            </circle>
          );
          offset += length;
          return circle;
        })}
      </svg>
      <div className="bd-donut-center">{children}</div>
    </div>
  );
}

function BudgetDashboard() {
  const { tripId } = useParams();
  const navigate = useNavigate();

  const [overview, setOverview] = useState(null);
  const [overviewError, setOverviewError] = useState("");
  const [showPast, setShowPast] = useState(false);

  const [budget, setBudget] = useState(undefined); // undefined = loading, null = none
  const [savings, setSavings] = useState(undefined);
  const [detailError, setDetailError] = useState("");

  const [panel, setPanel] = useState(""); // "" | "goal" | "contribution"
  const [goalForm, setGoalForm] = useState(null);
  const [contribForm, setContribForm] = useState({ amount: "", date: todayIso(), description: "" });
  const [formError, setFormError] = useState("");
  const [busy, setBusy] = useState(false);

  const refreshOverview = useCallback(() => {
    return getBudgetOverview()
      .then(setOverview)
      .catch((err) => setOverviewError(err.message));
  }, []);

  useEffect(() => {
    refreshOverview();
  }, [refreshOverview]);

  const today = todayIso();
  const upcoming = (overview || []).filter((t) => t.endDate >= today);
  const past = (overview || []).filter((t) => t.endDate < today);
  const selected = overview
    ? tripId
      ? overview.find((t) => String(t.tripId) === String(tripId))
      : upcoming[0] ?? overview[0]
    : null;
  const selectedId = selected?.tripId;

  useEffect(() => {
    if (selectedId === undefined) return;
    let cancelled = false;
    setBudget(undefined);
    setSavings(undefined);
    setDetailError("");
    setPanel("");
    Promise.all([getBudget(selectedId), getSavings(selectedId)])
      .then(([b, s]) => {
        if (cancelled) return;
        setBudget(b);
        setSavings(s);
      })
      .catch((err) => !cancelled && setDetailError(err.message));
    return () => {
      cancelled = true;
    };
  }, [selectedId]);

  // ---------- actions ----------

  function openGoalForm() {
    setFormError("");
    setGoalForm(
      savings
        ? {
            targetAmount: String(savings.targetAmount),
            initialSavedAmount: String(savings.initialSavedAmount ?? 0),
            targetDate: savings.targetDate,
            plannedMonthlyContribution:
              savings.plannedMonthlyContribution != null ? String(savings.plannedMonthlyContribution) : "",
          }
        : {
            targetAmount: budget ? String(budget.totalBudget) : "",
            initialSavedAmount: "",
            // Aim to be funded the day before you leave
            targetDate: selected.startDate > today ? addDays(selected.startDate, -1) : addDays(today, 30),
            plannedMonthlyContribution: "",
          }
    );
    setPanel("goal");
  }

  function openContributionForm() {
    setFormError("");
    setContribForm({ amount: "", date: today, description: "" });
    setPanel("contribution");
  }

  async function submitGoal(e) {
    e.preventDefault();
    const target = Number(goalForm.targetAmount);
    if (!(target > 0)) return setFormError("Enter a savings target greater than 0.");
    if (!goalForm.targetDate) return setFormError("Choose a target date.");

    setBusy(true);
    setFormError("");
    try {
      const saved = await saveSavingsGoal(selectedId, {
        targetAmount: target,
        initialSavedAmount: Number(goalForm.initialSavedAmount) || 0,
        targetDate: goalForm.targetDate,
        plannedMonthlyContribution:
          goalForm.plannedMonthlyContribution === "" ? null : Number(goalForm.plannedMonthlyContribution),
      });
      setSavings(saved);
      setPanel("");
      refreshOverview();
    } catch (err) {
      setFormError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function submitContribution(e) {
    e.preventDefault();
    const amount = Number(contribForm.amount);
    if (!(amount > 0)) return setFormError("Enter an amount greater than 0.");

    setBusy(true);
    setFormError("");
    try {
      const saved = await addContribution(selectedId, {
        amount,
        date: contribForm.date || null,
        description: contribForm.description.trim() || null,
      });
      setSavings(saved);
      setPanel("");
      refreshOverview();
    } catch (err) {
      setFormError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function removeContribution(c) {
    if (!window.confirm(`Remove the ${money(c.amount)} contribution from ${formatDate(c.date)}?`)) return;
    try {
      setSavings(await deleteContribution(selectedId, c.id));
      refreshOverview();
    } catch (err) {
      setDetailError(err.message);
    }
  }

  const currency = budget?.currency || selected?.currency || "USD";
  const money = makeMoney(currency);

  // ---------- render ----------

  return (
    <div className="bd-page">
      <aside className="bd-sidebar">
        <Sidebar />
      </aside>

      <main className="bd-main">
        <header className="bd-header">
          <div>
            <p className="bd-eyebrow">Budget</p>
            <h1>Budget &amp; savings</h1>
            <p className="bd-muted">What each trip costs, and how close you are to paying for it.</p>
          </div>
          <Link to="/create-trip" className="bd-ghost">+ New trip</Link>
        </header>

        {overviewError && <p className="bd-message bd-error">{overviewError}</p>}
        {!overview && !overviewError && <p className="bd-message">Loading your budgets...</p>}

        {overview && overview.length === 0 && (
          <section className="bd-empty">
            <h2>No trips yet</h2>
            <p className="bd-muted">Create a trip and we'll recommend a budget for it.</p>
            <Link to="/create-trip" className="bd-primary">Plan a trip</Link>
          </section>
        )}

        {overview && overview.length > 0 && (
          <>
            {renderTotals()}
            <div className="bd-layout">
              {renderTripList()}
              <div className="bd-detail">{selected ? renderDetail() : (
                <p className="bd-message">We couldn't find that trip. <Link to="/budget">Show my next trip</Link></p>
              )}</div>
            </div>
          </>
        )}
      </main>
    </div>
  );

  // Totals across upcoming trips (in the most common currency)
  function renderTotals() {
    const withBudget = upcoming.filter((t) => t.budgetTotal != null);
    const currencies = [...new Set(withBudget.map((t) => t.currency))];
    const main = currencies.sort(
      (a, b) => withBudget.filter((t) => t.currency === b).length - withBudget.filter((t) => t.currency === a).length
    )[0] || "USD";
    const inMain = withBudget.filter((t) => t.currency === main);
    const m = makeMoney(main);

    const budgeted = inMain.reduce((s, t) => s + Number(t.budgetTotal), 0);
    const savedTotal = inMain.reduce((s, t) => s + Number(t.savedAmount || 0), 0);
    const goalTotal = inMain.reduce((s, t) => s + Number(t.savingsTarget || 0), 0);
    const funded = upcoming.filter((t) => t.savingsStatus === "FUNDED").length;
    const pct = goalTotal > 0 ? Math.min(100, Math.floor((savedTotal / goalTotal) * 100)) : 0;

    return (
      <section className="bd-totals" aria-label="Upcoming trips summary">
        <div className="bd-total-tile">
          <span>Budgeted for upcoming trips</span>
          <strong>{m(budgeted)}</strong>
          <small>{inMain.length} of {upcoming.length} trip{upcoming.length === 1 ? "" : "s"} have a budget</small>
        </div>
        <div className="bd-total-tile">
          <span>Saved so far</span>
          <strong>{m(savedTotal)}</strong>
          <div className="bd-mini-meter"><span style={{ width: `${pct}%` }} /></div>
          <small>{goalTotal > 0 ? `${pct}% of ${m(goalTotal)} in savings goals` : "No savings goals yet"}</small>
        </div>
        <div className="bd-total-tile">
          <span>Still to save</span>
          <strong>{m(Math.max(0, goalTotal - savedTotal))}</strong>
          <small>Across your savings goals</small>
        </div>
        <div className="bd-total-tile">
          <span>Fully funded</span>
          <strong>{funded} / {upcoming.length}</strong>
          <small>upcoming trip{upcoming.length === 1 ? "" : "s"}</small>
        </div>
        {currencies.length > 1 && (
          <p className="bd-note">Totals include {main} trips only; trips in other currencies are listed below.</p>
        )}
      </section>
    );
  }

  function renderTripRow(t) {
    const m = makeMoney(t.currency);
    const active = t.tripId === selectedId;
    return (
      <li key={t.tripId}>
        <button
          type="button"
          className={`bd-trip ${active ? "is-active" : ""}`}
          aria-current={active ? "true" : undefined}
          onClick={() => navigate(`/budget/${t.tripId}`)}
        >
          <span className="bd-trip-top">
            <strong>{t.name}</strong>
            <span className="bd-trip-when">{countdown(t)}</span>
          </span>
          <span className="bd-trip-meta">
            {t.destination} · {formatDate(t.startDate, false)} – {formatDate(t.endDate)}
          </span>
          {t.savingsTarget != null ? (
            <>
              <span className="bd-trip-bar">
                <span style={{ width: `${t.percentFunded}%` }} className={t.percentFunded >= 100 ? "is-full" : ""} />
              </span>
              <span className="bd-trip-numbers">
                <span>{m(t.savedAmount)} of {m(t.savingsTarget)} saved</span>
                <strong>{t.percentFunded}%</strong>
              </span>
            </>
          ) : (
            <span className="bd-trip-numbers">
              <span>{t.budgetTotal != null ? `Budget ${m(t.budgetTotal)}` : "No budget yet"}</span>
              <span className="bd-trip-hint">No savings goal</span>
            </span>
          )}
        </button>
      </li>
    );
  }

  function renderTripList() {
    return (
      <nav className="bd-trips" aria-label="Your trips">
        <h2>Your trips</h2>
        {upcoming.length === 0 && <p className="bd-muted">No upcoming trips.</p>}
        <ul>{upcoming.map(renderTripRow)}</ul>
        {past.length > 0 && (
          <>
            <button type="button" className="bd-past-toggle" onClick={() => setShowPast((v) => !v)} aria-expanded={showPast}>
              {showPast ? "Hide" : "Show"} past trips ({past.length})
            </button>
            {showPast && <ul>{past.map(renderTripRow)}</ul>}
          </>
        )}
      </nav>
    );
  }

  function renderDetail() {
    const t = selected;
    const nights = Math.max(0, daysBetween(t.startDate, t.endDate));

    return (
      <>
        <section className="bd-hero">
          <svg className="bd-hero-art" viewBox="0 0 1200 200" preserveAspectRatio="none" aria-hidden="true">
            <path d="M0 160 L140 90 L230 140 L360 50 L470 130 L560 80 L680 150 L800 60 L920 120 L1030 70 L1200 140 L1200 200 L0 200 Z" />
            <path className="bd-hero-art-front" d="M0 180 L120 130 L260 170 L390 115 L520 165 L650 120 L780 175 L910 125 L1050 170 L1200 130 L1200 200 L0 200 Z" />
          </svg>
          <div className="bd-hero-content">
            <div>
              <h2>{t.name}</h2>
              <p>
                {t.destination} · {formatDate(t.startDate)} – {formatDate(t.endDate)}
              </p>
            </div>
            <span className="bd-tag">
              {t.numberOfTravelers} traveler{t.numberOfTravelers === 1 ? "" : "s"} · {nights} night{nights === 1 ? "" : "s"} · {countdown(t)}
            </span>
          </div>
        </section>

        {detailError && <p className="bd-message bd-error">{detailError}</p>}
        {(budget === undefined || savings === undefined) && !detailError ? (
          <p className="bd-message">Loading trip budget...</p>
        ) : (
          <>
            {renderSavings()}
            <div className="bd-grid">
              {renderEnvelopes()}
              {renderHistory()}
            </div>
          </>
        )}
      </>
    );
  }

  function renderSavings() {
    if (!savings) {
      return (
        <section className="bd-card bd-savings">
          <div className="bd-card-head">
            <div>
              <p className="bd-eyebrow">Savings goal</p>
              <h2>Start saving for this trip</h2>
            </div>
          </div>
          <p className="bd-muted">
            Set a target and date, and we'll work out how much to put aside each month
            {budget ? ` to cover your ${money(budget.totalBudget)} budget` : ""}.
          </p>
          {panel === "goal" ? renderGoalForm() : (
            <button type="button" className="bd-primary" onClick={openGoalForm}>Set a savings goal</button>
          )}
        </section>
      );
    }

    const status = STATUS[savings.status] ?? STATUS.NO_PLAN;
    const target = Number(savings.targetAmount);
    const saved = Number(savings.savedAmount);

    // Milestones: category caps added up in order, e.g. "Flights covered" once savings pass them
    const allocations = budget?.allocations || {};
    const order = [
      ...BUDGET_CATEGORIES.filter((c) => Number(allocations[c]) > 0),
      ...Object.keys(allocations).filter((c) => !BUDGET_CATEGORIES.includes(c) && Number(allocations[c]) > 0),
    ];
    let running = 0;
    const milestones = order
      .map((c) => {
        running += Number(allocations[c]);
        return { name: c, at: running };
      })
      .filter((ms) => ms.at < target);

    return (
      <section className="bd-card bd-savings">
        <div className="bd-card-head">
          <div>
            <p className="bd-eyebrow">Savings goal</p>
            <p className="bd-saved">
              {money(saved)} <small>of {money(target)} saved</small>
            </p>
          </div>
          <div className="bd-savings-side">
            <strong>{savings.percentFunded}% funded</strong>
            <span className={`bd-status ${status.cls}`}>{status.label}</span>
          </div>
        </div>

        <div className="bd-progress" aria-label={`${savings.percentFunded}% funded`}>
          <div className="bd-progress-fill" style={{ width: `${savings.percentFunded}%` }} />
          {milestones.map((ms) => (
            <span
              key={ms.name}
              className={`bd-progress-tick ${saved >= ms.at ? "is-reached" : ""}`}
              style={{ left: `${(ms.at / target) * 100}%` }}
            />
          ))}
        </div>

        {milestones.length > 0 && (
          <ol className="bd-milestones">
            {milestones.map((ms) => (
              <li key={ms.name} className={saved >= ms.at ? "is-reached" : ""}>
                <strong>{money(ms.at)}</strong>
                <span>{ms.name}</span>
                <small>{saved >= ms.at ? "Covered" : "Pending"}</small>
              </li>
            ))}
            <li className={saved >= target ? "is-reached" : ""}>
              <strong>{money(target)}</strong>
              <span>Full goal</span>
              <small>{saved >= target ? "Reached" : "Target"}</small>
            </li>
          </ol>
        )}

        <div className="bd-pace">
          {savings.status === "FUNDED" ? (
            <span>You've saved the full amount. 🎉</span>
          ) : (
            <>
              <span>
                Need <strong>{money(savings.requiredMonthly)}/month</strong> (about {money(savings.requiredWeekly)}/week) to
                reach it by <strong>{formatDate(savings.targetDate)}</strong>
              </span>
              {savings.plannedMonthlyContribution != null && (
                <span>
                  Your plan: <strong>{money(savings.plannedMonthlyContribution)}/month</strong>
                </span>
              )}
            </>
          )}
        </div>

        {panel === "goal" && renderGoalForm()}
        {panel === "contribution" && renderContributionForm()}
        {panel === "" && (
          <div className="bd-actions">
            <button type="button" className="bd-primary" onClick={openContributionForm}>+ Add savings</button>
            <button type="button" className="bd-ghost" onClick={openGoalForm}>Edit goal</button>
          </div>
        )}
      </section>
    );
  }

  function renderGoalForm() {
    const set = (field) => (e) => setGoalForm((f) => ({ ...f, [field]: e.target.value }));
    return (
      <form className="bd-form" onSubmit={submitGoal} noValidate>
        <div className="bd-form-grid">
          <label>
            <span>Savings target</span>
            <input type="number" min="0" inputMode="decimal" value={goalForm.targetAmount} onChange={set("targetAmount")} />
          </label>
          <label>
            <span>Target date</span>
            <input type="date" value={goalForm.targetDate} onChange={set("targetDate")} />
          </label>
          <label>
            <span>Already saved <em>(optional)</em></span>
            <input type="number" min="0" inputMode="decimal" placeholder="0" value={goalForm.initialSavedAmount} onChange={set("initialSavedAmount")} />
          </label>
          <label>
            <span>Monthly plan <em>(optional)</em></span>
            <input type="number" min="0" inputMode="decimal" placeholder="e.g. 300" value={goalForm.plannedMonthlyContribution} onChange={set("plannedMonthlyContribution")} />
          </label>
        </div>
        {formError && <p className="bd-form-error" role="alert">{formError}</p>}
        <div className="bd-actions">
          <button type="submit" className="bd-primary" disabled={busy}>{busy ? "Saving…" : "Save goal"}</button>
          <button type="button" className="bd-ghost" onClick={() => setPanel("")} disabled={busy}>Cancel</button>
        </div>
      </form>
    );
  }

  function renderContributionForm() {
    const set = (field) => (e) => setContribForm((f) => ({ ...f, [field]: e.target.value }));
    return (
      <form className="bd-form" onSubmit={submitContribution} noValidate>
        <div className="bd-form-grid">
          <label>
            <span>Amount</span>
            <input type="number" min="0" inputMode="decimal" autoFocus value={contribForm.amount} onChange={set("amount")} />
          </label>
          <label>
            <span>Date</span>
            <input type="date" max={today} value={contribForm.date} onChange={set("date")} />
          </label>
          <label className="bd-span-2">
            <span>Note <em>(optional)</em></span>
            <input type="text" maxLength={255} placeholder="e.g. November paycheck" value={contribForm.description} onChange={set("description")} />
          </label>
        </div>
        {formError && <p className="bd-form-error" role="alert">{formError}</p>}
        <div className="bd-actions">
          <button type="submit" className="bd-primary" disabled={busy}>{busy ? "Saving…" : "Add savings"}</button>
          <button type="button" className="bd-ghost" onClick={() => setPanel("")} disabled={busy}>Cancel</button>
        </div>
      </form>
    );
  }

  function renderEnvelopes() {
    const editLink = `/trips/${selectedId}/edit?tab=budget`;

    if (!budget) {
      return (
        <section className="bd-card">
          <div className="bd-card-head"><h2>Budget envelopes</h2></div>
          <p className="bd-muted">This trip doesn't have a budget yet.</p>
          <Link to={editLink} className="bd-primary">Set a budget</Link>
        </section>
      );
    }

    const total = Number(budget.totalBudget) || 0;
    const allocations = budget.allocations || {};
    const names = [
      ...BUDGET_CATEGORIES.filter((c) => Number(allocations[c]) > 0),
      ...Object.keys(allocations).filter((c) => !BUDGET_CATEGORIES.includes(c) && Number(allocations[c]) > 0),
    ];
    const categories = names.map((name, i) => ({ name, amount: Number(allocations[name]), cls: `bd-c${i % 5}`, meta: categoryMeta(name) }));
    const planned = categories.reduce((s, c) => s + c.amount, 0);
    const buffer = Math.max(0, total - planned);
    const pct = (n) => (total > 0 ? `${((n / total) * 100).toFixed(0)}%` : "0%");
    const travelers = Math.max(1, Number(selected.numberOfTravelers) || 1);

    const segments = [
      ...categories.map((c) => ({ key: c.name, value: c.amount, cls: c.cls, title: `${c.name}: ${money(c.amount)}` })),
      ...(buffer > 0 ? [{ key: "buffer", value: buffer, cls: "bd-buffer", title: `Buffer: ${money(buffer)}` }] : []),
    ];

    return (
      <section className="bd-card">
        <div className="bd-card-head">
          <h2>Budget envelopes</h2>
          <Link to={editLink} className="bd-link">Adjust caps</Link>
        </div>

        {categories.length === 0 ? (
          <p className="bd-muted">
            Your {money(total)} budget isn't split into categories yet. <Link to={editLink}>Set category caps</Link>
          </p>
        ) : (
          <div className="bd-envelopes">
            <Donut segments={segments} label={`Budget split for ${selected.name}`}>
              <span className="bd-donut-label">Total budget</span>
              <strong>{money(total)}</strong>
              <span className="bd-donut-sub">{money(total / travelers)} / person</span>
            </Donut>
            <ul className="bd-legend">
              {categories.map((c) => (
                <li key={c.name}>
                  <span className={`bd-swatch ${c.cls}`} aria-hidden="true" />
                  <span className="bd-legend-name">
                    <span aria-hidden="true">{c.meta.icon}</span> {c.name}
                    <small>{pct(c.amount)} of budget</small>
                  </span>
                  <strong>{money(c.amount)}</strong>
                </li>
              ))}
              {buffer > 0 && (
                <li>
                  <span className="bd-swatch bd-buffer" aria-hidden="true" />
                  <span className="bd-legend-name">
                    Buffer
                    <small>{pct(buffer)} unassigned</small>
                  </span>
                  <strong>{money(buffer)}</strong>
                </li>
              )}
            </ul>
          </div>
        )}
      </section>
    );
  }

  function renderHistory() {
    return (
      <section className="bd-card">
        <div className="bd-card-head">
          <h2>Savings history</h2>
          {savings && <span className="bd-muted-small">{savings.contributions.length} contribution{savings.contributions.length === 1 ? "" : "s"}</span>}
        </div>

        {!savings ? (
          <p className="bd-muted">Contributions show up here once you set a savings goal.</p>
        ) : (
          <ul className="bd-history">
            {savings.contributions.map((c) => (
              <li key={c.id}>
                <span className="bd-history-date">{formatDate(c.date, false)}</span>
                <span className="bd-history-desc">{c.description || "Savings contribution"}</span>
                <strong>+{money(c.amount)}</strong>
                <button type="button" className="bd-history-remove" aria-label={`Remove ${money(c.amount)} contribution`} onClick={() => removeContribution(c)}>
                  ×
                </button>
              </li>
            ))}
            {Number(savings.initialSavedAmount) > 0 && (
              <li className="bd-history-initial">
                <span className="bd-history-date">Start</span>
                <span className="bd-history-desc">Already saved when the goal was set</span>
                <strong>+{money(savings.initialSavedAmount)}</strong>
                <span />
              </li>
            )}
            {savings.contributions.length === 0 && Number(savings.initialSavedAmount) === 0 && (
              <li className="bd-history-empty">No savings logged yet. Use “+ Add savings” to record money you've set aside.</li>
            )}
          </ul>
        )}
      </section>
    );
  }
}

function addDays(iso, days) {
  const d = new Date(toUtc(iso) + days * 86400000);
  return d.toISOString().slice(0, 10);
}

export default BudgetDashboard;
