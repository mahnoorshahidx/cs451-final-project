import { useEffect, useRef, useState } from "react";
import {
  listItineraryActivities,
  createItineraryActivity,
  updateItineraryActivity,
  deleteItineraryActivity,
  getDestinationPhoto,
} from "../api/api";
import "../styles/ItineraryViewer.css";

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

function categoryIcon(activity) {
  const category = (activity.category ?? "").toLowerCase();
  const title = (activity.title ?? "").toLowerCase();
  if (category.includes("food") || category.includes("drink")) return "🍽️";
  if (category.includes("nature") || category.includes("outdoor")) return "🌳";
  if (category.includes("transport")) {
    if (/bus|shuttle/.test(title)) return "🚌";
    if (/taxi|uber|lyft|cab/.test(title)) return "🚕";
    if (/flight|airport|plane/.test(title)) return "✈️";
    if (/train|metro|subway/.test(title)) return "🚆";
    return "🚗";
  }
  if (category.includes("accommodation") || category.includes("hotel") || category.includes("lodging")) return "🏨";
  if (category.includes("sightseeing")) return "🏛️";
  if (/hotel|check.in|check.out|airbnb|lodging/.test(title)) return "🏨";
  if (/trail|hike|park|garden|forest/.test(title)) return "🌳";
  if (category.includes("activity")) return "🎟️";
  return "📌";
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

export default function ItineraryViewer({ trip, standalone = false }) {
  const [activities, setActivities] = useState([]);
  const [photo, setPhoto] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [reload, setReload] = useState(0);
  const [editDayKey, setEditDayKey] = useState(null);
  const [draftItems, setDraftItems] = useState([]);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");
  const nextDraftKey = useRef(1);

  const startDate = parseTripDate(trip?.startDate ?? trip?.start_date);
  const endDate = parseTripDate(trip?.endDate ?? trip?.end_date);
  const validDates = startDate && endDate && endDate >= startDate;
  const startKey = startDate ? dateString(startDate) : "";
  const endKey = endDate ? dateString(endDate) : "";

  useEffect(() => {
    if (!trip?.id || !validDates) return;
    let cancelled = false;
    setLoading(true);
    setLoadError("");
    setActivities([]);
    setEditDayKey(null);
    setDraftItems([]);
    listItineraryActivities(trip.id)
      .then((data) => {
        if (!cancelled) setActivities(Array.isArray(data) ? data : []);
      })
      .catch((err) => {
        if (!cancelled) setLoadError(err.message || "Could not load the itinerary.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [trip?.id, startKey, endKey, reload]);


  useEffect(() => {
    const destination = trip?.destination?.trim();

    setPhoto(null);

    if (!destination) return;

    let cancelled = false;

    getDestinationPhoto(destination)
      .then((result) => {
        if (!cancelled && result?.imageUrl) {
          setPhoto(result);
        }
      })
      .catch((error) => {
        if (!cancelled) {
          console.warn("Destination photo unavailable:", error);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [trip?.destination]);


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

  return (
    <section className="itinerary-viewer" aria-label="Complete trip itinerary">

      <header className="itinerary-viewer-hero">
        {photo?.imageUrl && (
          <img
            className="itinerary-viewer-hero-image"
            src={photo.imageUrl}
            alt=""
            onError={() => setPhoto(null)}
          />
        )}

        <div className="itinerary-viewer-hero-copy">
          <p className="itinerary-viewer-eyebrow">
            {standalone ? "SAVED ITINERARY" : "YOUR ITINERARY · STEP 4 OF 4"}
          </p>

          <h1>{trip.name}</h1>

          <p className="itinerary-viewer-meta">
            📍 {trip.destination || "Destination not specified"}
          </p>

          <p className="itinerary-viewer-meta">
            📅 {readableDate(startDate)} – {readableDate(endDate)}
          </p>
        </div>

        <div
          className="itinerary-viewer-summary"
          aria-label="Trip summary"
        >
          <strong>{totalDays}</strong>
          <span>{totalDays === 1 ? "day" : "days"}</span>

          <strong>{activities.length}</strong>
          <span>
            {activities.length === 1 ? "activity" : "activities"}
          </span>
        </div>

        {photo?.photographerUrl && (
          <p className="itinerary-viewer-photo-credit">
            Photo by{" "}
            <a
              href={photo.photographerUrl}
              target="_blank"
              rel="noopener noreferrer"
            >
              {photo.photographer}
            </a>
            {" "}on{" "}
            <a
              href={photo.unsplashUrl}
              target="_blank"
              rel="noopener noreferrer"
            >
              Unsplash
            </a>
          </p>
        )}
      </header>

      <div className="itinerary-viewer-intro">
        <h2>Day-by-Day Schedule</h2>
        <p>View your entire trip. Click ✏️ Edit Day to add, change, move, or remove activities for that day. Saving updates the Step 3 calendar too.</p>
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
          {days.map(({ day, date, key, events }) => (
            <section key={key} className="itinerary-viewer-day" aria-label={`Day ${day}: ${readableDate(date)}`}>
              <header className="itinerary-viewer-day-heading">
                <span className="itinerary-viewer-day-pill">Day {day}</span>
                <div className="itinerary-viewer-day-title">
                  <h3>{readableDate(date)}</h3>
                  <p>{events.length} {events.length === 1 ? "activity" : "activities"}</p>
                </div>
                <button
                  type="button"
                  className="itinerary-viewer-edit"
                  disabled={editDayKey !== null || saving}
                  onClick={() => beginEditDay(key, events)}
                  aria-label={`Edit all activities for Day ${day}`}
                >✏️ Edit Day</button>
              </header>

              {editDayKey === key ? (
                <form className="itinerary-viewer-day-editor" onSubmit={saveDay}>
                  <p className="itinerary-viewer-editor-instruction">Edit the whole day's activities below, then save all changes together.</p>
                  {draftItems.length === 0 && (
                    <p className="itinerary-viewer-empty">No activities in this day yet. Use Add Activity to plan one.</p>
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
                  <button type="button" className="itinerary-viewer-add" onClick={addDraft} disabled={saving}>+ Add Activity</button>
                  {saveError && <p role="alert" className="itinerary-viewer-save-error">{saveError}</p>}
                  <div className="itinerary-viewer-form-buttons">
                    <button type="button" className="itinerary-viewer-cancel" onClick={cancelDayEdit} disabled={saving}>Cancel</button>
                    <button type="submit" className="itinerary-viewer-save" disabled={saving}>{saving ? "Saving..." : "Save Day Changes"}</button>
                  </div>
                </form>
              ) : events.length === 0 ? (
                <p className="itinerary-viewer-empty">No activities planned for this day.</p>
              ) : (
                <ol className="itinerary-viewer-timeline">
                  {events.map((activity) => (
                    <li key={activity.id} className="itinerary-viewer-timeline-item">
                      <span className="itinerary-viewer-icon" aria-hidden="true">{categoryIcon(activity)}</span>
                      <article className="itinerary-viewer-activity">
                        <p className="itinerary-viewer-time">{timeRange(activity)}</p>
                        <h4>{activity.title}</h4>
                        <span className="itinerary-viewer-category">{activity.category}</span>
                        {activity.location && <p className="itinerary-viewer-location">📍 {activity.location}</p>}
                        {activity.notes && <p className="itinerary-viewer-notes">{activity.notes}</p>}
                      </article>
                    </li>
                  ))}
                </ol>
              )}
            </section>
          ))}
        </div>
      )}
    </section>
  );
}
