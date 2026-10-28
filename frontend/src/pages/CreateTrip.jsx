import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import "../styles/CreateTrip.css";
import { createTrip, getProfile, recommendBudget, recommendTiming, saveBudget } from "../api/api";
import { BUDGET_CATEGORIES, fallbackAllocations, scaleAllocations } from "../lib/budgetSuggestions";
import { categoryMeta } from "../lib/budgetCategories";

// Budget comes last so the recommendation can use everything collected before it
const STEPS = [
  { key: "destination", label: "Destination" },
  { key: "timing", label: "Travel timing" },
  { key: "details", label: "Travelers" },
  { key: "tradeoffs", label: "Trade-offs" },
  { key: "budget", label: "Total budget" },
  { key: "ready", label: "Category caps" },
];

const POPULAR_DESTINATIONS = ["Kyoto, Japan", "Lisbon, Portugal", "Denver, Colorado"];
const FALLBACK_PRESETS = [2500, 4200, 6000];
const CURRENCIES = ["USD", "EUR", "GBP", "MAD"];

const TIMING_OPTIONS = [
  { value: "set", title: "I have set dates", description: "Plan around dates that are already decided." },
  { value: "best", title: "Recommend the best time", description: "Prioritize ideal weather, events, and the liveliest season." },
  { value: "cheapest", title: "Find the cheapest time", description: "Use flexible dates to unlock lower flights and room rates." },
];

const NO_COMPROMISE = "none";
const TRADE_OFFS = [
  { value: "budgetHotels", title: "Budget hotels", description: "Simple stays, fewer amenities" },
  { value: "sharedRooms", title: "Shared rooms", description: "Hostels or shared lodging" },
  { value: "lowCostMeals", title: "Low-cost meals", description: "Fast-casual and self-catering" },
  { value: "longLayovers", title: "Long layovers", description: "Trade time for cheaper fares" },
  { value: "lowerCostActivities", title: "Lower-cost activities", description: "Free sights and self-guided days" },
  { value: "skipPaidActivities", title: "Skip paid activities", description: "Focus on the destination itself" },
];

// ---------- date helpers (dates stay "YYYY-MM-DD" strings, no timezone shifts) ----------

function todayIso() {
  const now = new Date();
  return [now.getFullYear(), String(now.getMonth() + 1).padStart(2, "0"), String(now.getDate()).padStart(2, "0")].join("-");
}

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
    weekday: "short",
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  });
}

function currencySymbol(currency) {
  try {
    return new Intl.NumberFormat("en-US", { style: "currency", currency, currencyDisplay: "narrowSymbol" })
      .formatToParts(0)
      .find((p) => p.type === "currency").value;
  } catch {
    return currency;
  }
}

// New trips only. Existing trips are edited on EditTrip (/trips/:tripId/edit).
function CreateTrip() {
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [form, setForm] = useState({
    destination: "",
    timing: "",
    // flexible modes: the window the user could travel in, plus trip length
    windowStart: "",
    windowEnd: "",
    nights: "5",
    // final trip dates (typed in, or filled from the recommendation)
    startDate: "",
    endDate: "",
    numberOfTravelers: "1",
    name: "",
    tradeOffs: [],
    totalBudget: "",
    currency: "USD",
  });

  // Recommendations
  const [timingRec, setTimingRec] = useState(null);
  const [timingLoading, setTimingLoading] = useState(false);
  const [timingError, setTimingError] = useState("");
  const [budgetRec, setBudgetRec] = useState(null);
  const [budgetLoading, setBudgetLoading] = useState(false);
  const [budgetError, setBudgetError] = useState("");
  // true while the total is the one we filled in, so a new recommendation can replace it
  const [totalIsSuggested, setTotalIsSuggested] = useState(false);

  // Category caps (last step): start from the recommendation or empty for a manual split
  const [splitMode, setSplitMode] = useState("recommended"); // "recommended" | "manual"
  const [caps, setCaps] = useState(null);
  const [capsKey, setCapsKey] = useState("");

  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  const current = STEPS[step];
  const isLast = step === STEPS.length - 1;
  const isFlexible = form.timing === "best" || form.timing === "cheapest";
  const total = Number(form.totalBudget) || 0;
  const travelers = Number(form.numberOfTravelers) || 0;
  const tripNights = Math.max(1, nightsBetween(form.startDate, form.endDate));

  const money = (n) =>
    new Intl.NumberFormat("en-US", { style: "currency", currency: form.currency, maximumFractionDigits: 0 }).format(n);

  // ---------- loading ----------

  // Start with the user's saved currency preference
  useEffect(() => {
    getProfile()
      .then((profile) => setForm((p) => ({ ...p, currency: profile.currency || "USD" })))
      .catch(() => {}); // non-blocking: fall back to USD
  }, []);

  // ---------- timing recommendation (refreshes as the window or length changes) ----------

  useEffect(() => {
    if (current.key !== "timing" || !isFlexible) return;

    const nights = Number(form.nights);
    if (!form.windowStart || !form.windowEnd || !Number.isInteger(nights) || nights < 1) {
      setTimingRec(null);
      setTimingError("");
      return;
    }
    if (nightsBetween(form.windowStart, form.windowEnd) < nights) {
      setTimingRec(null);
      setTimingError(`Your window needs to be at least ${nights} night${nights === 1 ? "" : "s"} long.`);
      return;
    }

    let cancelled = false;
    const timer = setTimeout(() => {
      setTimingLoading(true);
      setTimingError("");
      recommendTiming({
        destination: form.destination.trim(),
        mode: form.timing,
        windowStart: form.windowStart,
        windowEnd: form.windowEnd,
        nights,
      })
        .then((rec) => {
          if (cancelled) return;
          setTimingRec(rec);
          setForm((p) => ({ ...p, startDate: rec.startDate, endDate: rec.endDate }));
        })
        .catch((err) => {
          if (cancelled) return;
          setTimingRec(null);
          setTimingError(err.message);
        })
        .finally(() => !cancelled && setTimingLoading(false));
    }, 400); // wait until the user stops typing

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [current.key, isFlexible, form.timing, form.destination, form.windowStart, form.windowEnd, form.nights]);

  // ---------- budget recommendation (on entering the budget step, and when currency changes) ----------

  useEffect(() => {
    if (current.key !== "budget") return;

    let cancelled = false;
    setBudgetLoading(true);
    setBudgetError("");
    recommendBudget({
      destination: form.destination.trim(),
      startDate: form.startDate || null,
      nights: tripNights,
      travelers: Math.max(1, travelers),
      tradeOffs: form.tradeOffs.filter((t) => t !== NO_COMPROMISE),
      currency: form.currency,
    })
      .then((rec) => {
        if (cancelled) return;
        setBudgetRec(rec);
        // Prefill the total, unless the user already chose one themselves
        if (!form.totalBudget || totalIsSuggested) {
          setForm((p) => ({ ...p, totalBudget: String(rec.recommendedTotal) }));
          setTotalIsSuggested(true);
        }
      })
      .catch((err) => {
        if (cancelled) return;
        setBudgetRec(null);
        setBudgetError(err.message);
      })
      .finally(() => !cancelled && setBudgetLoading(false));

    return () => {
      cancelled = true;
    };
    // Only re-run on step entry or currency change; the other inputs are fixed by this step
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [current.key, form.currency]);

  // ---------- form updates ----------

  function update(field, value) {
    setForm((p) => ({ ...p, [field]: value }));
    setError("");
  }

  function selectTiming(value) {
    setError("");
    setTimingError("");
    setTimingRec(null);
    setForm((p) => ({
      ...p,
      timing: value,
      // flexible modes get their dates from the recommendation
      ...(value !== "set" && { startDate: "", endDate: "" }),
    }));
  }

  function setTotal(value, suggested = false) {
    update("totalBudget", value);
    setTotalIsSuggested(suggested);
  }

  function toggleTradeOff(value) {
    setError("");
    setForm((prev) => {
      if (value === NO_COMPROMISE) {
        return { ...prev, tradeOffs: prev.tradeOffs.includes(NO_COMPROMISE) ? [] : [NO_COMPROMISE] };
      }
      const without = prev.tradeOffs.filter((t) => t !== NO_COMPROMISE);
      const next = without.includes(value) ? without.filter((t) => t !== value) : [...without, value];
      return { ...prev, tradeOffs: next };
    });
  }

  // Category caps that will be saved with the budget
  function plannedAllocations() {
    return scaleAllocations(budgetRec, total) ?? fallbackAllocations(total, form.tradeOffs);
  }

  const emptyCaps = () => Object.fromEntries(BUDGET_CATEGORIES.map((c) => [c, ""]));
  const recommendedCaps = () =>
    Object.fromEntries(Object.entries(plannedAllocations()).map(([c, v]) => [c, String(v)]));

  // Fill the caps when entering the last step. Recommended caps re-scale if the total changed;
  // a manual split keeps whatever the user already typed.
  function prepareCaps() {
    const key = splitMode === "manual" ? "manual" : `rec|${total}|${form.currency}|${budgetRec?.recommendedTotal ?? ""}`;
    if (key === capsKey && caps) return;
    setCaps(splitMode === "manual" ? emptyCaps() : recommendedCaps());
    setCapsKey(key);
  }

  function updateCap(category, value) {
    setCaps((c) => ({ ...c, [category]: value }));
    setError("");
  }

  const capCategories = caps
    ? [...BUDGET_CATEGORIES, ...Object.keys(caps).filter((c) => !BUDGET_CATEGORIES.includes(c))]
    : [];
  const capsAllocated = caps ? Object.values(caps).reduce((sum, v) => sum + (Number(v) || 0), 0) : 0;
  const capsRemaining = total - capsAllocated;

  // ---------- validation & navigation ----------

  function validateStep(key) {
    switch (key) {
      case "destination":
        return form.destination.trim() ? "" : "Enter a destination to continue.";
      case "timing":
        if (!form.timing) return "Choose how you'd like to plan your timing.";
        if (isFlexible) {
          if (!form.windowStart || !form.windowEnd) return "Add the window you could travel in.";
          if (!(Number(form.nights) >= 1)) return "Enter how many nights you want to stay.";
          if (timingLoading) return "Still finding your dates, one moment.";
          if (!timingRec) return timingError || "We couldn't recommend dates yet. Check your window.";
          return "";
        }
        if (!form.startDate || !form.endDate) return "Add your start and end dates.";
        if (form.endDate < form.startDate) return "End date can't be before the start date.";
        return "";
      case "details":
        return Number.isInteger(travelers) && travelers >= 1 ? "" : "At least 1 traveler.";
      case "tradeoffs":
        return form.tradeOffs.length ? "" : "Pick at least one option, or choose \u201cI'd rather not compromise.\u201d";
      case "budget":
        return total > 0 ? "" : "Enter a total budget greater than 0.";
      case "ready":
        return capsAllocated > total ? "Your category caps add up to more than your total budget." : "";
      default:
        return "";
    }
  }

  function goNext() {
    const err = validateStep(current.key);
    if (err) return setError(err);
    setError("");
    if (current.key === "budget") prepareCaps();
    setStep((s) => s + 1);
  }

  function goBack() {
    setError("");
    setStep((s) => Math.max(s - 1, 0));
  }

  async function handleFinish() {
    for (let i = 0; i < STEPS.length; i++) {
      const err = validateStep(STEPS[i].key);
      if (err) {
        setStep(i);
        return setError(err);
      }
    }

    setSaving(true);
    setError("");
    try {
      const destination = form.destination.trim();
      const tripBody = {
        name: form.name.trim() || `${destination.split(",")[0]} trip`,
        destination,
        startDate: form.startDate,
        endDate: form.endDate,
        numberOfTravelers: travelers,
        // Ignored by the backend until the trips table has these columns
        timingPreference: form.timing,
        tradeOffs: form.tradeOffs,
      };

      const trip = await createTrip(tripBody);

      await saveBudget(trip.id, {
        totalBudget: total,
        currency: form.currency,
        allocations: Object.fromEntries(
          Object.entries(caps ?? {})
            .filter(([, v]) => Number(v) > 0)
            .map(([k, v]) => [k, Number(v)])
        ),
      });

      // Land on the new trip's budget summary
      navigate(`/budget/${trip.id}`);
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  function handleSubmit(e) {
    e.preventDefault();
    isLast ? handleFinish() : goNext();
  }

  const presets = budgetRec
    ? [Number(budgetRec.lowTotal), Number(budgetRec.recommendedTotal), Number(budgetRec.highTotal)]
    : FALLBACK_PRESETS;
  const presetLabels = budgetRec ? ["Lean", "Recommended", "Comfortable"] : [];

  return (
    <div className="wizard-page">
      <header className="wizard-topbar">
        <div className="wizard-brand">
          <span className="wizard-logo">W</span>
          <span>Wayfare</span>
        </div>
        <button type="button" className="wizard-exit" onClick={() => navigate("/trips")}>
          <span aria-hidden="true">×</span> Exit
        </button>
      </header>

      <div className="wizard-progress">
        <div className="wizard-progress-inner">
          <div className="wizard-progress-labels">
            <span className="wizard-eyebrow">Create your budget</span>
            <span className="wizard-step-name">{current.label}</span>
          </div>
          <div
            className="wizard-bars"
            role="progressbar"
            aria-valuemin={1}
            aria-valuemax={STEPS.length}
            aria-valuenow={step + 1}
            aria-label={`Step ${step + 1} of ${STEPS.length}`}
          >
            {STEPS.map((s, i) => (
              <span key={s.key} className={`wizard-bar ${i <= step ? "is-done" : ""}`} />
            ))}
          </div>
        </div>
      </div>

      <form className={`wizard-body ${isLast ? "is-wide" : ""}`} onSubmit={handleSubmit} noValidate>
        <div className="wizard-content">
          {current.key === "destination" && (
            <>
              <h1>Where do you want to go?</h1>
              <p className="wizard-sub">
                Choose a destination and we'll estimate realistic costs for getting there and staying comfortably.
              </p>
              <label className="wizard-label" htmlFor="destination">City, region, or country</label>
              <input
                id="destination"
                className="wizard-input wizard-input-lg"
                type="text"
                placeholder="Try Kyoto, Japan"
                autoFocus
                value={form.destination}
                onChange={(e) => update("destination", e.target.value)}
              />
              <p className="wizard-hint">Popular right now</p>
              <div className="wizard-chips">
                {POPULAR_DESTINATIONS.map((d) => (
                  <button
                    type="button"
                    key={d}
                    className={`wizard-chip ${form.destination === d ? "is-selected" : ""}`}
                    onClick={() => update("destination", d)}
                  >
                    {d}
                  </button>
                ))}
              </div>
            </>
          )}

          {current.key === "timing" && (
            <>
              <h1>When would you like to travel?</h1>
              <p className="wizard-sub">Set your dates or let Wayfare recommend the right window for your priorities.</p>
              <div className="wizard-options" role="radiogroup" aria-label="Travel timing">
                {TIMING_OPTIONS.map((opt) => (
                  <button
                    type="button"
                    role="radio"
                    aria-checked={form.timing === opt.value}
                    key={opt.value}
                    className={`wizard-option ${form.timing === opt.value ? "is-selected" : ""}`}
                    onClick={() => selectTiming(opt.value)}
                  >
                    <strong>{opt.title}</strong>
                    <span>{opt.description}</span>
                  </button>
                ))}
              </div>

              {form.timing === "set" && (
                <div className="wizard-dates">
                  <div>
                    <label className="wizard-label" htmlFor="startDate">Start date</label>
                    <input
                      id="startDate"
                      className="wizard-input"
                      type="date"
                      value={form.startDate}
                      onChange={(e) => update("startDate", e.target.value)}
                    />
                  </div>
                  <div>
                    <label className="wizard-label" htmlFor="endDate">End date</label>
                    <input
                      id="endDate"
                      className="wizard-input"
                      type="date"
                      min={form.startDate}
                      value={form.endDate}
                      onChange={(e) => update("endDate", e.target.value)}
                    />
                  </div>
                </div>
              )}

              {isFlexible && (
                <>
                  <div className="wizard-dates wizard-dates-3">
                    <div>
                      <label className="wizard-label" htmlFor="windowStart">Earliest you could leave</label>
                      <input
                        id="windowStart"
                        className="wizard-input"
                        type="date"
                        min={todayIso()}
                        value={form.windowStart}
                        onChange={(e) => update("windowStart", e.target.value)}
                      />
                    </div>
                    <div>
                      <label className="wizard-label" htmlFor="windowEnd">Latest you'd be back</label>
                      <input
                        id="windowEnd"
                        className="wizard-input"
                        type="date"
                        min={form.windowStart || todayIso()}
                        value={form.windowEnd}
                        onChange={(e) => update("windowEnd", e.target.value)}
                      />
                    </div>
                    <div>
                      <label className="wizard-label" htmlFor="nights">Nights</label>
                      <input
                        id="nights"
                        className="wizard-input"
                        type="number"
                        min="1"
                        max="60"
                        step="1"
                        value={form.nights}
                        onChange={(e) => update("nights", e.target.value)}
                      />
                    </div>
                  </div>

                  <div className="wizard-rec" aria-live="polite">
                    {timingLoading ? (
                      <p className="wizard-rec-loading">
                        Finding the {form.timing === "cheapest" ? "cheapest" : "best"} dates…
                      </p>
                    ) : timingRec ? (
                      <>
                        <p className="wizard-eyebrow">Recommended dates</p>
                        <p className="wizard-rec-headline">
                          {formatDate(timingRec.startDate)} → {formatDate(timingRec.endDate)}
                        </p>
                        <p className="wizard-rec-reason">{timingRec.reason}</p>
                      </>
                    ) : timingError ? (
                      <p className="wizard-rec-error">{timingError}</p>
                    ) : (
                      <p className="wizard-rec-loading">
                        Add your travel window and trip length, and we'll pick dates.
                      </p>
                    )}
                  </div>
                </>
              )}
            </>
          )}

          {current.key === "details" && (
            <>
              <h1>Who's going?</h1>
              <p className="wizard-sub">We'll size rooms and per-person costs to your group, and name your trip so it's easy to find later.</p>
              <div className="wizard-dates">
                <div>
                  <label className="wizard-label" htmlFor="travelers">Number of travelers</label>
                  <input
                    id="travelers"
                    className="wizard-input"
                    type="number"
                    min="1"
                    step="1"
                    autoFocus
                    value={form.numberOfTravelers}
                    onChange={(e) => update("numberOfTravelers", e.target.value)}
                  />
                </div>
                <div>
                  <label className="wizard-label" htmlFor="tripName">
                    Trip name <span className="wizard-optional">(optional)</span>
                  </label>
                  <input
                    id="tripName"
                    className="wizard-input"
                    type="text"
                    placeholder={`${form.destination.split(",")[0] || "My"} trip`}
                    value={form.name}
                    onChange={(e) => update("name", e.target.value)}
                  />
                </div>
              </div>
            </>
          )}

          {current.key === "tradeoffs" && (
            <>
              <h1>Which trade-offs feel okay?</h1>
              <p className="wizard-sub">Select all you'd consider. We'll use these to keep recommendations within your budget.</p>
              <div className="wizard-grid">
                {TRADE_OFFS.map((opt) => {
                  const selected = form.tradeOffs.includes(opt.value);
                  return (
                    <button
                      type="button"
                      aria-pressed={selected}
                      key={opt.value}
                      className={`wizard-option ${selected ? "is-selected" : ""}`}
                      onClick={() => toggleTradeOff(opt.value)}
                    >
                      <strong>{opt.title}</strong>
                      <span>{opt.description}</span>
                    </button>
                  );
                })}
                <button
                  type="button"
                  aria-pressed={form.tradeOffs.includes(NO_COMPROMISE)}
                  className={`wizard-option wizard-option-wide ${form.tradeOffs.includes(NO_COMPROMISE) ? "is-selected" : ""}`}
                  onClick={() => toggleTradeOff(NO_COMPROMISE)}
                >
                  <strong>I'd rather not compromise on these</strong>
                </button>
              </div>
            </>
          )}

          {current.key === "budget" && (
            <>
              <h1>How much do you want to spend in total?</h1>
              <p className="wizard-sub">
                We've estimated a budget from your trip so far. Use it as-is, or set your own ceiling.
              </p>

              <div className="wizard-rec" aria-live="polite">
                {budgetLoading ? (
                  <p className="wizard-rec-loading">Estimating your trip costs…</p>
                ) : budgetRec ? (
                  <>
                    <div className="wizard-rec-top">
                      <div>
                        <p className="wizard-eyebrow">Recommended budget</p>
                        <p className="wizard-rec-headline">{money(Number(budgetRec.recommendedTotal))}</p>
                        <p className="wizard-rec-reason">
                          Typical range {money(Number(budgetRec.lowTotal))} – {money(Number(budgetRec.highTotal))}
                          {travelers > 1 &&
                            ` · about ${money(Number(budgetRec.recommendedTotal) / travelers)} per traveler`}
                        </p>
                      </div>
                      {Number(form.totalBudget) !== Number(budgetRec.recommendedTotal) && (
                        <button
                          type="button"
                          className="wizard-rec-apply"
                          onClick={() => setTotal(String(budgetRec.recommendedTotal), true)}
                        >
                          Use this amount
                        </button>
                      )}
                    </div>

                    <ul className="wizard-rec-breakdown">
                      {Object.entries(budgetRec.allocations).map(([category, amount]) => (
                        <li key={category}>
                          <span>{category}</span>
                          <strong>{money(Number(amount))}</strong>
                        </li>
                      ))}
                    </ul>

                    <details className="wizard-rec-assumptions">
                      <summary>How we estimated this</summary>
                      <ul>
                        {budgetRec.assumptions.map((a) => (
                          <li key={a}>{a}</li>
                        ))}
                      </ul>
                    </details>
                  </>
                ) : budgetError ? (
                  <p className="wizard-rec-error">Couldn't load a recommendation ({budgetError}). You can still set your own budget.</p>
                ) : null}
              </div>

              <label className="wizard-label" htmlFor="totalBudget">Total trip budget</label>
              <div className="wizard-money">
                <span className="wizard-money-symbol">{currencySymbol(form.currency)}</span>
                <input
                  id="totalBudget"
                  type="number"
                  min="0"
                  inputMode="decimal"
                  placeholder="4,200"
                  value={form.totalBudget}
                  onChange={(e) => setTotal(e.target.value, false)}
                />
                <select
                  aria-label="Currency"
                  value={form.currency}
                  onChange={(e) => update("currency", e.target.value)}
                >
                  {CURRENCIES.map((c) => (
                    <option key={c} value={c}>{c}</option>
                  ))}
                </select>
              </div>
              <p className="wizard-hint">This is your ceiling, not a spending target.</p>
              <div className="wizard-presets">
                {presets.map((amount, i) => (
                  <button
                    type="button"
                    key={`${i}-${amount}`}
                    className={`wizard-chip wizard-preset ${total === amount ? "is-selected" : ""}`}
                    onClick={() => setTotal(String(amount), i === 1 && Boolean(budgetRec))}
                  >
                    {presetLabels[i] && <small>{presetLabels[i]}</small>}
                    {money(amount)}
                  </button>
                ))}
              </div>

              <p className="wizard-label wizard-split-title">How should we split it into categories?</p>
              <div className="wizard-grid" role="radiogroup" aria-label="Category split">
                <button
                  type="button"
                  role="radio"
                  aria-checked={splitMode === "recommended"}
                  className={`wizard-option ${splitMode === "recommended" ? "is-selected" : ""}`}
                  onClick={() => setSplitMode("recommended")}
                >
                  <strong>Use the recommended split</strong>
                  <span>We fill in each category. You can still adjust it next.</span>
                </button>
                <button
                  type="button"
                  role="radio"
                  aria-checked={splitMode === "manual"}
                  className={`wizard-option ${splitMode === "manual" ? "is-selected" : ""}`}
                  onClick={() => setSplitMode("manual")}
                >
                  <strong>I'll split it myself</strong>
                  <span>Start with empty categories and enter your own amounts.</span>
                </button>
              </div>
            </>
          )}

          {current.key === "ready" && caps && (
            <>
              <h1>Fine-tune your category caps</h1>
              <p className="wizard-sub">
                {splitMode === "manual"
                  ? "Split your budget however you like. Anything you leave unassigned stays as a buffer."
                  : "We've split your budget using the recommendation. Adjust any category, or keep it as-is."}{" "}
                Nothing is booked or charged.
              </p>

              <div className="wizard-caps">
                <section className="wizard-caps-list" aria-label="Category caps">
                  <div className="wizard-caps-tools">
                    <button type="button" className="wizard-textbtn" onClick={() => setCaps(recommendedCaps())}>
                      Use recommended split
                    </button>
                    <button type="button" className="wizard-textbtn" onClick={() => setCaps(emptyCaps())}>
                      Clear all
                    </button>
                  </div>

                  {capCategories.map((category) => {
                    const meta = categoryMeta(category);
                    const amount = Number(caps[category]) || 0;
                    return (
                      <div className="wizard-cap" key={category}>
                        <span className="wizard-cap-icon" aria-hidden="true">{meta.icon}</span>
                        <div className="wizard-cap-text">
                          <strong>{category}</strong>
                          <span>{meta.description}</span>
                        </div>
                        <span className="wizard-cap-pct">
                          {total > 0 && amount > 0 ? `${Math.round((amount / total) * 100)}%` : ""}
                        </span>
                        <div className="wizard-cap-input">
                          <span aria-hidden="true">{currencySymbol(form.currency)}</span>
                          <input
                            type="number"
                            min="0"
                            placeholder="0"
                            aria-label={`${category} cap`}
                            value={caps[category] ?? ""}
                            onChange={(e) => updateCap(category, e.target.value)}
                          />
                        </div>
                      </div>
                    );
                  })}
                </section>

                <aside className="wizard-caps-summary">
                  <p className="wizard-eyebrow">Budget breakdown</p>
                  <p className="wizard-rec-headline">
                    {money(total)} <small>{form.currency}</small>
                  </p>
                  <p className="wizard-rec-reason">
                    {form.destination} · {formatDate(form.startDate)} – {formatDate(form.endDate)} · {travelers} traveler
                    {travelers === 1 ? "" : "s"}
                  </p>

                  <div className="wizard-caps-meter" aria-hidden="true">
                    <span
                      className={capsRemaining < 0 ? "is-over" : ""}
                      style={{ width: `${total > 0 ? Math.min(100, (capsAllocated / total) * 100) : 0}%` }}
                    />
                  </div>
                  <p className="wizard-caps-meter-label">
                    {money(capsAllocated)} of {money(total)} planned
                  </p>

                  <ul className="wizard-caps-lines">
                    {capCategories.map((category) => (
                      <li key={category}>
                        <span>{category}</span>
                        <strong>{money(Number(caps[category]) || 0)}</strong>
                      </li>
                    ))}
                  </ul>

                  <div className={`wizard-caps-remaining ${capsRemaining < 0 ? "is-over" : ""}`}>
                    <span>{capsRemaining < 0 ? "Over budget by" : "Unassigned buffer"}</span>
                    <strong>{money(Math.abs(capsRemaining))}</strong>
                  </div>
                </aside>
              </div>
            </>
          )}

          {error && <p className="wizard-error" role="alert">{error}</p>}
        </div>

        <footer className="wizard-footer">
          {step > 0 ? (
            <button type="button" className="wizard-back" onClick={goBack} disabled={saving}>
              ← Back
            </button>
          ) : (
            <span />
          )}
          <button type="submit" className="wizard-continue" disabled={saving}>
            {isLast
              ? saving
                ? "Saving..."
                : "Save trip & budget"
              : "Continue →"}
          </button>
        </footer>
      </form>
    </div>
  );
}

export default CreateTrip;
