import { useState } from "react";
import { Check, ChevronDown, RefreshCw, Sparkles } from "lucide-react";
import "../styles/ItineraryOptions.css";

// UC5: the AI's options side by side. Cheapest first is how the backend sends them
// (budget, balanced, luxury); each shows its total, cost per person and per day, where the
// money goes, and a day-by-day preview. Choosing one adds it to the itinerary.

const TONE = { budget: "sage", balanced: "blue", luxury: "marigold" };

function money(amount, currency) {
  if (amount == null) return "—";
  try {
    return new Intl.NumberFormat("en-US", { style: "currency", currency: currency || "USD", maximumFractionDigits: 0 }).format(amount);
  } catch {
    return `${Math.round(amount)} ${currency || ""}`;
  }
}

function shortDay(iso) {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d)).toLocaleDateString("en-US", { weekday: "short", month: "short", day: "numeric", timeZone: "UTC" });
}

function clock(t) {
  if (!t) return "";
  const [h, m] = t.split(":").map(Number);
  return `${h % 12 || 12}:${String(m).padStart(2, "0")} ${h < 12 ? "AM" : "PM"}`;
}

function OptionCard({ option, travelers, days, cheapest, choosing, onChoose }) {
  const [open, setOpen] = useState(false);
  const total = option.totalCost != null ? Number(option.totalCost) : null;
  const categories = Object.entries(option.costByCategory || {}).sort((a, b) => b[1] - a[1]);
  const catTotal = categories.reduce((s, [, v]) => s + Number(v), 0) || 1;
  const byDay = option.activities.reduce((acc, a) => {
    (acc[a.activityDate] ||= []).push(a);
    return acc;
  }, {});
  const extra = cheapest != null && total != null && total > cheapest ? total - cheapest : 0;

  return (
    <article className={`io-card is-${TONE[option.style] || "blue"}`}>
      <header className="io-card-head">
        <span className="io-style">{option.label}</span>
        {extra === 0 && cheapest != null && <span className="io-badge">Lowest cost</span>}
      </header>

      <div className="io-total">
        <span className="tb-num">{money(total, option.currency)}</span>
        <small>
          {total != null
            ? `${money(total / travelers, option.currency)} per person · ${money(total / days, option.currency)} a day`
            : "No cost estimate"}
        </small>
        {extra > 0 && <small className="io-extra">+{money(extra, option.currency)} vs. the lowest</small>}
      </div>

      {option.summary && <p className="io-summary">{option.summary}</p>}

      {categories.length > 0 && (
        <div className="io-split" aria-label="Where the money goes">
          <div className="io-split-bar" aria-hidden="true">
            {categories.map(([name, value]) => (
              <span key={name} className={`io-cat io-cat-${name.toLowerCase()}`} style={{ width: `${(Number(value) / catTotal) * 100}%` }} />
            ))}
          </div>
          <ul>
            {categories.slice(0, 4).map(([name, value]) => (
              <li key={name}>
                <i className={`io-cat io-cat-${name.toLowerCase()}`} aria-hidden="true" />
                {name} <b>{money(Number(value), option.currency)}</b>
              </li>
            ))}
          </ul>
        </div>
      )}

      <button type="button" className="io-preview-toggle" onClick={() => setOpen((v) => !v)} aria-expanded={open}>
        {option.activityCount} activities over {Object.keys(byDay).length} days
        <ChevronDown size={16} aria-hidden="true" className={open ? "is-open" : ""} />
      </button>
      {open && (
        <ol className="io-days">
          {Object.entries(byDay).map(([day, items]) => (
            <li key={day}>
              <strong>{shortDay(day)}</strong>
              <ul>
                {items.map((a, i) => (
                  <li key={`${day}-${i}`}>
                    <span className="io-time">{clock(a.startTime) || "Any time"}</span>
                    <span>{a.title}</span>
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ol>
      )}

      <button
        type="button"
        className="tb-btn tb-btn-primary io-choose"
        onClick={() => onChoose(option)}
        disabled={choosing != null}
      >
        {choosing === option.id ? "Adding..." : <><Check size={16} aria-hidden="true" /> Choose this plan</>}
      </button>
    </article>
  );
}

export default function ItineraryOptionsCompare({ trip, options, choosing, error, onChoose, onRegenerate, onBack, regenerating }) {
  const travelers = Math.max(1, Number(trip?.numberOfTravelers) || 1);
  const first = options[0]?.startDate;
  const last = options[0]?.endDate;
  const days = first && last
    ? Math.round((Date.parse(last) - Date.parse(first)) / 86400000) + 1
    : 1;
  const totals = options.map((o) => (o.totalCost != null ? Number(o.totalCost) : null)).filter((v) => v != null);
  const cheapest = totals.length ? Math.min(...totals) : null;

  return (
    <section className="io-compare" aria-labelledby="io-title">
      <div className="io-head">
        <div>
          <p className="tb-kicker"><Sparkles size={14} aria-hidden="true" /> AI trip options</p>
          <h1 id="io-title" className="tb-wide">Compare your options</h1>
          <p>
            {options.length} plans for {trip?.name}, made from your preferences. Pick one to add it to your itinerary;
            you can still edit any day afterwards.
          </p>
        </div>
        <div className="io-head-actions">
          <button type="button" className="tb-btn tb-btn-ghost" onClick={onBack} disabled={choosing != null || regenerating}>
            Change preferences
          </button>
          <button type="button" className="tb-btn tb-btn-outline" onClick={onRegenerate} disabled={choosing != null || regenerating}>
            <RefreshCw size={16} aria-hidden="true" /> {regenerating ? "Making new options..." : "New options"}
          </button>
        </div>
      </div>

      {error && <p className="error-text" role="alert">{error}</p>}

      <div className="io-grid">
        {options.map((option) => (
          <OptionCard
            key={option.id}
            option={option}
            travelers={travelers}
            days={days}
            cheapest={cheapest}
            choosing={choosing}
            onChoose={onChoose}
          />
        ))}
      </div>
    </section>
  );
}
