import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import {
  BedDouble, Bus, CalendarDays, Car, CheckSquare, Landmark, MapPin, Pencil, Plane,
  RefreshCw, ShoppingBag, Sparkles, Square, Ticket, TrainFront, Trash2, Trees, UtensilsCrossed,
} from "lucide-react";
import {
  listItineraryActivities,
  createItineraryActivity,
  updateItineraryActivity,
  deleteItineraryActivity,
  deleteItinerary,
  generateItinerary,
} from "../api/api";
import useDestinationPhoto from "../lib/useDestinationPhoto";
import {
  dayChunks,
  generationRequest,
  loadItineraryPreferences,
  preferenceSummary,
} from "../lib/itineraryPreferences";
import ConfirmDialog from "./ConfirmDialog";
import "../styles/Itinerary.css";

// Most days regenerated in one go (each 14 days is one AI request)
const MAX_REGENERATE_DAYS = 56;

const DAY_MS = 24 * 60 * 60 * 1000;
const CATEGORIES = [
  "Activity",
  "Food & Drink",
  "Sightseeing",
  "Nature",
  "Transportation",
  "Accommodation",
  "Other",
];

function parseTripDate(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value ?? "");
  if (!match) return null;
  const date = new Date(Date.UTC(+match[1], +match[2] - 1, +match[3]));
  if (
    date.getUTCFullYear() !== +match[1] ||
    date.getUTCMonth() !== +match[2] - 1 ||
    date.getUTCDate() !== +match[3]
  ) return null;
  return date;
}

function dateString(date) {
  return date.toISOString().slice(0, 10);
}

function readableDate(date) {
  return new Intl.DateTimeFormat("en-US", {
    weekday: "long", month: "long", day: "numeric", year: "numeric", timeZone: "UTC",
  }).format(date);
}

function readableTime(value) {
  if (!value) return "Flexible time";
  const [h, m] = value.slice(0, 5).split(":").map(Number);
  if (!Number.isFinite(h) || !Number.isFinite(m)) return value;
  return `${h % 12 || 12}:${String(m).padStart(2, "0")} ${h < 12 ? "AM" : "PM"}`;
}

function timeRange(activity) {
  if (!activity.startTime) return "Flexible time";
  return activity.endTime
    ? `${readableTime(activity.startTime)} – ${readableTime(activity.endTime)}`
    : readableTime(activity.startTime);
}

function CategoryIcon({ activity }) {
  const category = (activity.category ?? "").toLowerCase();
  const title = (activity.title ?? "").toLowerCase();
  const props = { size: 18, strokeWidth: 2, "aria-hidden": true };

  if (category.includes("food") || category.includes("drink")) return <UtensilsCrossed {...props} />;
  if (category.includes("nature") || category.includes("outdoor")) return <Trees {...props} />;
  if (category.includes("transport")) {
    if (/bus|shuttle/.test(title)) return <Bus {...props} />;
    if (/flight|airport|plane/.test(title)) return <Plane {...props} />;
    if (/train|metro|subway/.test(title)) return <TrainFront {...props} />;
    return <Car {...props} />;
  }
  if (category.includes("accommodation") || category.includes("hotel") || category.includes("rest")) return <BedDouble {...props} />;
  if (category.includes("shopping")) return <ShoppingBag {...props} />;
  if (category.includes("sightseeing")) return <Landmark {...props} />;
  if (/hotel|check.in|check.out|airbnb|lodging/.test(title)) return <BedDouble {...props} />;
  if (/trail|hike|park|garden|forest/.test(title)) return <Trees {...props} />;
  if (category.includes("activity")) return <Ticket {...props} />;
  return <MapPin {...props} />;
}

function money(amount, currency) {
  try {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: currency || "USD",
      maximumFractionDigits: 0,
    }).format(amount);
  } catch {
    return `${Math.round(amount)} ${currency || ""}`;
  }
}

function editFields(activity) {
  return {
    activityDate: activity.activityDate,
    title: activity.title ?? "",
    startTime: activity.startTime?.slice(0, 5) ?? "",
    endTime: activity.endTime?.slice(0, 5) ?? "",
    category: activity.category ?? "Activity",
    location: activity.location ?? "",
    notes: activity.notes ?? "",
  };
}

function requestFromDraft(draft) {
  return {
    activityDate: draft.activityDate,
    title: draft.title.trim(),
    startTime: draft.startTime ? `${draft.startTime}:00` : null,
    endTime: draft.endTime ? `${draft.endTime}:00` : null,
    category: draft.category,
    location: draft.location.trim() || null,
    notes: draft.notes.trim() || null,
  };
}

function sameActivity(activity, request) {
  return activity.activityDate === request.activityDate &&
    activity.title === request.title &&
    (activity.startTime ?? null) === request.startTime &&
    (activity.endTime ?? null) === request.endTime &&
    activity.category === request.category &&
    (activity.location ?? null) === request.location &&
    (activity.notes ?? null) === request.notes;
}

// standalone: the saved itinerary page (not step 4 of the create flow).
// onGenerated(activities): called with activities the AI added here, so the create flow can discard them.
// onDeleted(): called after "Delete itinerary".
export default function ItineraryViewer({ trip, standalone = false, onGenerated, onDeleted }) {
  const [activities, setActivities] = useState([]);
  const [photo, clearPhoto] = useDestinationPhoto(trip?.destination);
  // Which load finished last ({ key, error }); "loading" while it doesn't match the current key
  const [loaded, setLoaded] = useState({ key: "", error: "" });
  const [reload, setReload] = useState(0);
  const [editDayKey, setEditDayKey] = useState(null);
  const [draftItems, setDraftItems] = useState([]);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");
  const nextDraftKey = useRef(1);

  // AI preferences remembered for this trip (null if it was planned by hand or in another browser)
  const [preferences] = useState(() => (trip?.id ? loadItineraryPreferences(trip.id) : null));
  const [selecting, setSelecting] = useState(false);
  const [selected, setSelected] = useState(() => new Set());
  const [dialog, setDialog] = useState(null); // { type: "days" | "all" | "delete", days?: [] }
  const [progress, setProgress] = useState(null); // { done, total } while regenerating
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState("");
  const [notice, setNotice] = useState("");

  const startDate = parseTripDate(trip?.startDate ?? trip?.start_date);
  const endDate = parseTripDate(trip?.endDate ?? trip?.end_date);
  const validDates = startDate && endDate && endDate >= startDate;
  const startKey = startDate ? dateString(startDate) : "";
  const endKey = endDate ? dateString(endDate) : "";

  const loadKey = `${trip?.id}|${startKey}|${endKey}|${reload}`;
  const loading = loaded.key !== loadKey;
  const loadError = loading ? "" : loaded.error;

  useEffect(() => {
    if (!trip?.id || !validDates) return undefined;
    let cancelled = false;
    listItineraryActivities(trip.id)
      .then((data) => {
        if (cancelled) return;
        setActivities(Array.isArray(data) ? data : []);
        setLoaded({ key: loadKey, error: "" });
      })
      .catch((err) => {
        if (!cancelled) setLoaded({ key: loadKey, error: err.message || "Could not load the itinerary." });
      });
    return () => { cancelled = true; };
  }, [trip?.id, validDates, loadKey]);

  if (!validDates) {
    return <section className="itinerary-viewer"><p role="alert">This trip needs valid travel dates before its itinerary can be displayed.</p></section>;
  }

  const totalDays = Math.round((endDate.getTime() - startDate.getTime()) / DAY_MS) + 1;
  const days = Array.from({ length: totalDays }, (_, index) => {
    const date = new Date(startDate.getTime() + index * DAY_MS);
    const key = dateString(date);
    const events = activities
      .filter((activity) => activity.activityDate === key)
      .sort((a, b) =>
        (a.startTime ?? "99:99").localeCompare(b.startTime ?? "99:99") ||
        (a.title ?? "").localeCompare(b.title ?? "")
      );
    return { day: index + 1, date, key, events };
  });

  function beginEditDay(dayKey, events) {
    if (editDayKey !== null || saving) return;
    setEditDayKey(dayKey);
    setDraftItems(events.map((activity) => ({
      ...editFields(activity),
      id: activity.id,
      clientKey: `saved-${activity.id}`,
    })));
    setSaveError("");
  }

  function cancelDayEdit() {
    if (saving) return;
    setEditDayKey(null);
    setDraftItems([]);
    setSaveError("");
  }

  function changeDraft(clientKey, field, value) {
    setDraftItems((current) => current.map((item) =>
      item.clientKey === clientKey ? { ...item, [field]: value } : item
    ));
    setSaveError("");
  }

  function addDraft() {
    setDraftItems((current) => [...current, {
      clientKey: `new-${nextDraftKey.current++}`,
      id: null,
      activityDate: editDayKey,
      title: "",
      startTime: "",
      endTime: "",
      category: "Activity",
      location: "",
      notes: "",
    }]);
    setSaveError("");
  }

  function removeDraft(clientKey) {
    setDraftItems((current) => current.filter((item) => item.clientKey !== clientKey));
    setSaveError("");
  }

  async function saveDay(event) {
    event.preventDefault();
    if (editDayKey === null || saving) return;

    for (const draft of draftItems) {
      if (!draft.title.trim()) {
        setSaveError("Each activity must have a name. Remove an unused blank activity or fill it in.");
        return;
      }
      if (draft.activityDate < startKey || draft.activityDate > endKey) {
        setSaveError("Each activity date must be within the trip dates.");
        return;
      }
      if (draft.endTime && (!draft.startTime || draft.endTime <= draft.startTime)) {
        setSaveError("End time must be later than start time for every activity.");
        return;
      }
    }

    // Remove activities from this day only when the user removed their draft.
    const deletedIds = activities
      .filter((activity) => activity.activityDate === editDayKey)
      .filter((activity) => !draftItems.some((draft) => draft.id === activity.id))
      .map((activity) => activity.id);

    setSaving(true);
    setSaveError("");
    try {
      // Apply changes one at a time; keep the editor open on any failure.
      // Successful creates receive database IDs immediately, so retrying does not duplicate them.
      for (const draft of draftItems) {
        const request = requestFromDraft(draft);
        if (draft.id != null) {
          const previous = activities.find((activity) => activity.id === draft.id);
          if (previous && sameActivity(previous, request)) continue;
          const updated = await updateItineraryActivity(trip.id, draft.id, request);
          setActivities((current) => current.map((item) => item.id === draft.id ? updated : item));
        } else {
          const created = await createItineraryActivity(trip.id, request);
          setActivities((current) => [...current, created]);
          setDraftItems((current) => current.map((item) =>
            item.clientKey === draft.clientKey ? { ...item, id: created.id } : item
          ));
        }
      }

      for (const id of deletedIds) {
        await deleteItineraryActivity(trip.id, id);
        setActivities((current) => current.filter((item) => item.id !== id));
      }

      setEditDayKey(null);
      setDraftItems([]);
    } catch (error) {
      setSaveError(
        `Could not finish saving this day: ${error.message || "Server request failed."} ` +
        "Any completed requests may already be saved. Your editor remains open so you can retry."
      );
    } finally {
      setSaving(false);
    }
  }

  // ---------- Regenerate, delete ----------

  const travelers = Math.max(1, Number(trip.numberOfTravelers) || 1);
  const tripCurrency = trip.currency || preferences?.currency || "USD";
  const dailyBudget = Number(preferences?.dailyBudget) || null;
  const allKeys = days.map((day) => day.key);

  function toggleDay(key) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  function stopSelecting() {
    setSelecting(false);
    setSelected(new Set());
  }

  // Ask the AI for new activities on these days (replacing what's there), a chunk at a time.
  async function regenerateDays(keys) {
    if (!preferences || keys.length === 0) return;
    const chunks = dayChunks(keys);
    const chosen = new Set(keys);
    let current = activities;
    setDialog(null);
    setBusy(true);
    setActionError("");
    setNotice("");
    setProgress({ done: 0, total: chunks.length });

    try {
      for (const [index, chunk] of chunks.entries()) {
        // Places kept on the other days, so the AI doesn't repeat them
        const avoidTitles = [...new Set(
          current.filter((a) => !chosen.has(a.activityDate)).map((a) => a.title)
        )].slice(0, 300);

        const created = await generateItinerary(trip.id, generationRequest(preferences, {
          startDate: chunk.startDate,
          endDate: chunk.endDate,
          replaceExisting: true,
          avoidTitles,
        }));

        const inChunk = new Set(chunk.days);
        current = [...current.filter((a) => !inChunk.has(a.activityDate)), ...created];
        setActivities(current);
        onGenerated?.(created);
        setProgress({ done: index + 1, total: chunks.length });
      }
      setNotice(`${keys.length} ${keys.length === 1 ? "day" : "days"} replanned with your preferences.`);
      stopSelecting();
    } catch (error) {
      setActionError(
        `Couldn't finish replanning: ${error.message || "the AI request failed."} ` +
        "Days that already finished were updated; the rest were left as they were."
      );
    } finally {
      setBusy(false);
      setProgress(null);
    }
  }

  async function removeItinerary() {
    setBusy(true);
    setActionError("");
    try {
      await deleteItinerary(trip.id);
      setActivities([]);
      setDialog(null);
      onDeleted?.();
    } catch (error) {
      setActionError(`Couldn't delete the itinerary: ${error.message || "server error"}`);
      setDialog(null);
    } finally {
      setBusy(false);
    }
  }

  // Estimated cost per person for a day, from the activities' group costs in the trip currency
  function dayCost(events) {
    const costed = events.filter((a) => a.estimatedCost != null && (a.costCurrency || tripCurrency) === tripCurrency);
    if (costed.length === 0) return null;
    return costed.reduce((sum, a) => sum + Number(a.estimatedCost), 0) / travelers;
  }

  const editing = editDayKey !== null;
  const locked = editing || saving || busy;
  const selectedKeys = allKeys.filter((key) => selected.has(key));
  const tooMany = selectedKeys.length > MAX_REGENERATE_DAYS;
  const preferencesLink = `/create-itinerary?trip=${trip.id}&method=ai`;

  return (
    <section className="itinerary-viewer" aria-label="Complete trip itinerary">

      <header className="itinerary-viewer-hero">
        {photo?.imageUrl && (
          <img
            className="itinerary-viewer-hero-image"
            src={photo.imageUrl}
            alt=""
            onError={clearPhoto}
          />
        )}

        <div className="itinerary-viewer-hero-copy">
          <p className="itinerary-viewer-eyebrow">
            {standalone ? "Saved itinerary" : "Review your itinerary"}
          </p>

          <h1 className="tb-wide">{trip.name}</h1>

          <p className="itinerary-viewer-meta">
            <MapPin size={16} aria-hidden="true" /> {trip.destination || "Destination not specified"}
          </p>

          <p className="itinerary-viewer-meta">
            <CalendarDays size={16} aria-hidden="true" /> {readableDate(startDate)} – {readableDate(endDate)}
          </p>
        </div>

        <div className="itinerary-viewer-summary" aria-label="Trip summary">
          <strong>{totalDays}</strong>
          <span>{totalDays === 1 ? "day" : "days"}</span>

          <strong>{activities.length}</strong>
          <span>{activities.length === 1 ? "activity" : "activities"}</span>
        </div>

        {photo?.photographerUrl && (
          <p className="itinerary-viewer-photo-credit">
            Photo by{" "}
            <a href={photo.photographerUrl} target="_blank" rel="noopener noreferrer">
              {photo.photographer}
            </a>
            {" "}on{" "}
            <a href={photo.unsplashUrl} target="_blank" rel="noopener noreferrer">
              Unsplash
            </a>
          </p>
        )}
      </header>

      {/* Preferences + whole-itinerary actions */}
      <div className="itinerary-toolbar">
        <div className="itinerary-toolbar-prefs">
          <span className="tb-kicker">
            <Sparkles size={14} aria-hidden="true" /> {preferences ? "Planned with your preferences" : "AI preferences"}
          </span>
          <p>
            {preferences
              ? preferenceSummary(preferences)
              : "Answer a few questions to let the AI plan or replan days for this trip."}
          </p>
          <Link to={preferencesLink} className="itinerary-toolbar-link">
            {preferences ? "Change preferences" : "Set preferences"}
          </Link>
        </div>

        <div className="itinerary-toolbar-actions">
          <button
            type="button"
            className="tb-btn tb-btn-outline"
            disabled={locked || !preferences}
            onClick={() => (selecting ? stopSelecting() : setSelecting(true))}
          >
            <CheckSquare size={16} aria-hidden="true" />
            {selecting ? "Done selecting" : "Select days"}
          </button>
          <button
            type="button"
            className="tb-btn tb-btn-primary"
            disabled={locked || !preferences}
            onClick={() => setDialog({ type: "all", days: allKeys })}
          >
            <RefreshCw size={16} aria-hidden="true" />
            Regenerate all
          </button>
          <button
            type="button"
            className="tb-btn itinerary-delete-btn"
            disabled={locked || activities.length === 0}
            onClick={() => setDialog({ type: "delete" })}
          >
            <Trash2 size={16} aria-hidden="true" />
            Delete itinerary
          </button>
        </div>

        {selecting && (
          <div className="itinerary-selectbar" role="region" aria-label="Selected days">
            <strong>{selectedKeys.length} {selectedKeys.length === 1 ? "day" : "days"} selected</strong>
            <button
              type="button"
              className="itinerary-toolbar-link"
              onClick={() => setSelected(new Set(days.filter((d) => d.events.length === 0).map((d) => d.key).slice(0, MAX_REGENERATE_DAYS)))}
            >
              Select empty days
            </button>
            <button type="button" className="itinerary-toolbar-link" onClick={() => setSelected(new Set())}>
              Clear
            </button>
            {tooMany && <span className="itinerary-selectbar-warn">Pick up to {MAX_REGENERATE_DAYS} days at a time.</span>}
            <button
              type="button"
              className="tb-btn tb-btn-accent"
              disabled={locked || selectedKeys.length === 0 || tooMany}
              onClick={() => setDialog({ type: "days", days: selectedKeys })}
            >
              <RefreshCw size={16} aria-hidden="true" />
              Regenerate selected
            </button>
          </div>
        )}

        {progress && (
          <p className="itinerary-toolbar-status" role="status">
            <span className="itinerary-ai-spinner" aria-hidden="true" />
            Replanning with your preferences… {progress.done} of {progress.total}{" "}
            {progress.total === 1 ? "request" : "requests"} done. This can take up to 30 seconds each.
          </p>
        )}
        {notice && !progress && <p className="itinerary-toolbar-notice" role="status">{notice}</p>}
        {actionError && <p className="itinerary-toolbar-error" role="alert">{actionError}</p>}
      </div>

      <div className="itinerary-viewer-intro">
        <h2 className="tb-wide">Day by day</h2>
        <p>
          Edit a day to add, change, move or remove activities. Regenerate a day to replan it with
          your preferences. Changes are saved as you make them.
        </p>
      </div>

      {loading && <p role="status" className="itinerary-viewer-message">Loading saved activities...</p>}
      {!loading && loadError && (
        <div role="alert" className="itinerary-viewer-message itinerary-viewer-error">
          <p>{loadError}</p>
          <button type="button" onClick={() => setReload((n) => n + 1)}>Retry</button>
        </div>
      )}
      {!loading && !loadError && (
        <div className="itinerary-viewer-days">
          {days.map(({ day, date, key, events }) => {
            const cost = dayCost(events);
            const over = cost != null && dailyBudget != null && cost > dailyBudget;
            const isSelected = selected.has(key);

            return (
              <section
                key={key}
                className={`itinerary-viewer-day ${events.length === 0 ? "is-empty" : ""} ${isSelected ? "is-selected" : ""}`}
                aria-label={`Day ${day}: ${readableDate(date)}`}
              >
                <header className="itinerary-viewer-day-heading">
                  {selecting && (
                    <button
                      type="button"
                      className="itinerary-day-check"
                      aria-pressed={isSelected}
                      aria-label={`${isSelected ? "Unselect" : "Select"} Day ${day}`}
                      onClick={() => toggleDay(key)}
                      disabled={busy}
                    >
                      {isSelected ? <CheckSquare size={20} /> : <Square size={20} />}
                    </button>
                  )}
                  <span className="itinerary-viewer-day-pill">Day {day}</span>
                  <div className="itinerary-viewer-day-title">
                    <h3>{readableDate(date)}</h3>
                    <p>
                      {events.length} {events.length === 1 ? "activity" : "activities"}
                      {cost != null && (
                        <span className={`itinerary-day-cost ${over ? "is-over" : dailyBudget ? "is-ok" : ""}`}>
                          ≈ {money(cost, tripCurrency)} per person
                          {dailyBudget != null && (over
                            ? ` · ${money(cost - dailyBudget, tripCurrency)} over your daily budget`
                            : " · within your daily budget")}
                        </span>
                      )}
                    </p>
                  </div>
                  <div className="itinerary-viewer-day-actions">
                    {preferences && (
                      <button
                        type="button"
                        className="itinerary-viewer-edit"
                        disabled={locked}
                        onClick={() => setDialog({ type: "days", days: [key] })}
                        aria-label={`Regenerate Day ${day}`}
                      >
                        <RefreshCw size={15} aria-hidden="true" /> {events.length ? "Regenerate" : "Plan with AI"}
                      </button>
                    )}
                    <button
                      type="button"
                      className="itinerary-viewer-edit"
                      disabled={locked}
                      onClick={() => beginEditDay(key, events)}
                      aria-label={`Edit all activities for Day ${day}`}
                    >
                      <Pencil size={15} aria-hidden="true" /> Edit day
                    </button>
                  </div>
                </header>

                {editDayKey === key ? (
                  <form className="itinerary-viewer-day-editor" onSubmit={saveDay}>
                    <p className="itinerary-viewer-editor-instruction">Edit the whole day's activities below, then save all changes together.</p>
                    {draftItems.length === 0 && (
                      <p className="itinerary-viewer-empty">No activities in this day yet. Use Add activity to plan one.</p>
                    )}
                    {draftItems.map((draft, index) => (
                      <fieldset key={draft.clientKey} className="itinerary-viewer-draft">
                        <legend>Activity {index + 1}</legend>
                        <button type="button" className="itinerary-viewer-remove" disabled={saving}
                          onClick={() => removeDraft(draft.clientKey)} aria-label={`Remove activity ${index + 1} from day`}>Remove</button>
                        <div className="itinerary-viewer-form">
                          <label>Activity name
                            <input required maxLength={255} disabled={saving} value={draft.title}
                              onChange={(e) => changeDraft(draft.clientKey, "title", e.target.value)} />
                          </label>
                          <label>Date
                            <input type="date" min={startKey} max={endKey} required disabled={saving} value={draft.activityDate}
                              onChange={(e) => changeDraft(draft.clientKey, "activityDate", e.target.value)} />
                          </label>
                          <div className="itinerary-viewer-form-times">
                            <label>Start time
                              <input type="time" disabled={saving} value={draft.startTime}
                                onChange={(e) => changeDraft(draft.clientKey, "startTime", e.target.value)} />
                            </label>
                            <label>End time
                              <input type="time" disabled={saving} value={draft.endTime}
                                onChange={(e) => changeDraft(draft.clientKey, "endTime", e.target.value)} />
                            </label>
                          </div>
                          <label>Category
                            <select disabled={saving} value={draft.category}
                              onChange={(e) => changeDraft(draft.clientKey, "category", e.target.value)}>
                              {!CATEGORIES.includes(draft.category) && <option value={draft.category}>{draft.category}</option>}
                              {CATEGORIES.map((name) => <option key={name} value={name}>{name}</option>)}
                            </select>
                          </label>
                          <label>Location
                            <input maxLength={255} disabled={saving} value={draft.location}
                              onChange={(e) => changeDraft(draft.clientKey, "location", e.target.value)} />
                          </label>
                          <label>Notes
                            <textarea rows={2} disabled={saving} value={draft.notes}
                              onChange={(e) => changeDraft(draft.clientKey, "notes", e.target.value)} />
                          </label>
                        </div>
                      </fieldset>
                    ))}
                    <button type="button" className="itinerary-viewer-add" onClick={addDraft} disabled={saving}>+ Add activity</button>
                    {saveError && <p role="alert" className="itinerary-viewer-save-error">{saveError}</p>}
                    <div className="itinerary-viewer-form-buttons">
                      <button type="button" className="itinerary-viewer-cancel" onClick={cancelDayEdit} disabled={saving}>Cancel</button>
                      <button type="submit" className="itinerary-viewer-save" disabled={saving}>{saving ? "Saving..." : "Save day"}</button>
                    </div>
                  </form>
                ) : events.length === 0 ? (
                  <p className="itinerary-viewer-empty">No activities planned for this day.</p>
                ) : (
                  <ol className="itinerary-viewer-timeline">
                    {events.map((activity) => (
                      <li key={activity.id} className="itinerary-viewer-timeline-item">
                        <span className="itinerary-viewer-icon" aria-hidden="true"><CategoryIcon activity={activity} /></span>
                        <article className="itinerary-viewer-activity">
                          <p className="itinerary-viewer-time">{timeRange(activity)}</p>
                          <h4>{activity.title}</h4>
                          <span className="itinerary-viewer-category">{activity.category}</span>
                          {activity.estimatedCost != null && (
                            <span className="itinerary-viewer-cost">
                              {money(Number(activity.estimatedCost) / travelers, activity.costCurrency || tripCurrency)} / person
                            </span>
                          )}
                          {activity.location && (
                            <p className="itinerary-viewer-location"><MapPin size={14} aria-hidden="true" /> {activity.location}</p>
                          )}
                          {activity.notes && <p className="itinerary-viewer-notes">{activity.notes}</p>}
                        </article>
                      </li>
                    ))}
                  </ol>
                )}
              </section>
            );
          })}
        </div>
      )}

      {dialog?.type === "days" && (
        <ConfirmDialog
          title={dialog.days.length === 1 ? "Replan this day?" : `Replan ${dialog.days.length} days?`}
          onCancel={() => setDialog(null)}
          busy={busy}
          actions={[{ label: "Regenerate", tone: "primary", onClick: () => regenerateDays(dialog.days) }]}
        >
          <p>
            The AI will plan {dialog.days.length === 1 ? "this day" : "these days"} again using your preferences
            ({preferenceSummary(preferences)}).
          </p>
          <p>Everything currently on {dialog.days.length === 1 ? "this day" : "these days"} is replaced, including activities you added yourself.</p>
        </ConfirmDialog>
      )}

      {dialog?.type === "all" && (
        <ConfirmDialog
          title="Regenerate the whole itinerary?"
          onCancel={() => setDialog(null)}
          busy={busy}
          actions={totalDays > MAX_REGENERATE_DAYS
            ? []
            : [{ label: "Regenerate all", tone: "primary", onClick: () => regenerateDays(allKeys) }]}
        >
          {totalDays > MAX_REGENERATE_DAYS ? (
            <p>
              This trip is {totalDays} days long, which is too many to replan at once. Use
              <strong> Select days</strong> to replan up to {MAX_REGENERATE_DAYS} days at a time.
            </p>
          ) : (
            <>
              <p>Every day is planned again with your preferences ({preferenceSummary(preferences)}).</p>
              <p>All {activities.length} current activities are replaced, including ones you added yourself.</p>
            </>
          )}
        </ConfirmDialog>
      )}

      {dialog?.type === "delete" && (
        <ConfirmDialog
          title="Delete this itinerary?"
          onCancel={() => setDialog(null)}
          busy={busy}
          actions={[{ label: busy ? "Deleting..." : "Delete itinerary", tone: "danger", onClick: removeItinerary }]}
        >
          <p>All {activities.length} activities for {trip.name} are deleted. The trip itself and its budget stay.</p>
          <p>This can't be undone.</p>
        </ConfirmDialog>
      )}
    </section>
  );
}
