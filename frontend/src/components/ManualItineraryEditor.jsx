import { useEffect, useState } from "react";
import ItineraryCalendar from "./ItineraryCalendar";
import "../styles/ManualItineraryEditor.css";

import {
  listItineraryActivities,
  createItineraryActivity,
  updateItineraryActivity,
  deleteItineraryActivity,
} from "../api/api";

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

const EMPTY_FORM = {
  title: "",
  time: "",
  endTime: "",
  category: "Activity",
  location: "",
  notes: "",
};

function parseTripDate(value) {
  if (typeof value !== "string") return null;
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  if (!match) return null;

  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const date = new Date(Date.UTC(year, month - 1, day));

  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day
  ) {
    return null;
  }
  return date;
}

function formatDate(date, options) {
  return new Intl.DateTimeFormat("en-US", {
    ...options,
    timeZone: "UTC",
  }).format(date);
}

function formatTime(value) {
  if (!/^\d{2}:\d{2}$/.test(value || "")) return null;
  const [hours, minutes] = value.split(":").map(Number);
  return `${hours % 12 || 12}:${String(minutes).padStart(2, "0")} ${hours < 12 ? "AM" : "PM"}`;
}

function formatTimeRange(start, end) {
  if (!start) return "Flexible time";
  return end
    ? `${formatTime(start)} â€“ ${formatTime(end)}`
    : formatTime(start);
}

function toEditorActivity(activity, tripStartDate) {
  const activityDate = parseTripDate(activity.activityDate);

  const day =
    Math.round(
      (activityDate.getTime() - tripStartDate.getTime()) /
        DAY_MS
    ) + 1;

  return {
    id: activity.id,
    day,
    title: activity.title,
    time: activity.startTime?.slice(0, 5) ?? "",
    endTime: activity.endTime?.slice(0, 5) ?? "",
    category: activity.category,
    location: activity.location ?? "",
    notes: activity.notes ?? "",
  };
}

export default function ManualItineraryEditor({ trip, plan, onChange }) {
  const current = plan ?? {
    activeDay: 1,
    activities: [],
    nextId: 1,
  };

  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [formError, setFormError] = useState("");
  const [loadingActivities, setLoadingActivities] = useState(true);
  const [saving, setSaving] = useState(false);
  const [activityError, setActivityError] = useState("");

  const startDate = parseTripDate(
    trip?.startDate ?? trip?.start_date ?? trip?.departureDate ?? trip?.departure_date
  );
  const endDate = parseTripDate(
    trip?.endDate ?? trip?.end_date ?? trip?.returnDate ?? trip?.return_date
  );

  useEffect(() => {
    const firstDate = parseTripDate(trip.startDate);
    const lastDate = parseTripDate(trip.endDate);

    if (!firstDate || !lastDate || lastDate < firstDate) {
      return;
    }

    let cancelled = false;

    setLoadingActivities(true);
    setActivityError("");

    listItineraryActivities(trip.id)
      .then((savedActivities) => {
        if (cancelled) return;

        const converted = savedActivities.map((activity) =>
          toEditorActivity(activity, firstDate)
        );

        onChange((previous) => ({
          ...previous,
          activities: converted,
        }));
      })
      .catch((error) => {
        if (!cancelled) {
          setActivityError(
            error.message || "Unable to load saved activities."
          );
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingActivities(false);
      });

    return () => {
      cancelled = true;
    };
  }, [trip.id, trip.startDate, trip.endDate, onChange]);


  // Display a clear error rather than inventing dates.
  if (!startDate || !endDate || endDate < startDate) {
    return (
      <section className="manual-itinerary">
        <h1>Build Your Itinerary</h1>
        <p role="alert">
          This trip needs valid start and end dates before its calendar can be displayed.
        </p>
      </section>
    );
  }

  const totalDays = Math.round((endDate.getTime() - startDate.getTime()) / DAY_MS) + 1;
  const activeDay = Math.min(Math.max(current.activeDay ?? 1, 1), totalDays);
  const selectedDate = new Date(startDate.getTime() + (activeDay - 1) * DAY_MS);
  const activities = current.activities ?? [];
  const dayActivities = activities
    .filter((activity) => activity.day === activeDay)
    .sort((a, b) => (a.time || "99:99").localeCompare(b.time || "99:99"));

  function updateField(field, value) {
    setForm((previous) => ({ ...previous, [field]: value }));
    setFormError("");
  }

  function closeForm() {
    setForm(EMPTY_FORM);
    setEditingId(null);
    setFormError("");
    setFormOpen(false);
  }

  function selectDay(day) {
    onChange({ ...current, activeDay: day });
    closeForm();
  }

  function openAddForm() {
    setForm(EMPTY_FORM);
    setEditingId(null);
    setFormError("");
    setFormOpen(true);
  }

  function editActivity(activity) {
    setEditingId(activity.id);
    setForm({
      title: activity.title,
      time: activity.time || "",
      endTime: activity.endTime || "",
      category: activity.category,
      location: activity.location || "",
      notes: activity.notes || "",
    });
    setFormError("");
    setFormOpen(true);
  }


  async function removeActivity(id) {
    if (saving || loadingActivities || activityError) return;

    setSaving(true);

    try {
      await deleteItineraryActivity(trip.id, id);

      onChange((previous) => ({
        ...previous,
        activities: (previous.activities ?? []).filter(
          (activity) => activity.id !== id
        ),
      }));

      if (editingId === id) closeForm();
    } catch (error) {
      setActivityError(
        error.message || "Could not delete the activity."
      );
    } finally {
      setSaving(false);
    }
  }


  async function saveActivity(event) {
    event.preventDefault();

    if (saving || loadingActivities || activityError) return;

    const title = form.title.trim();
    if (!title) return;

    if (form.endTime && (!form.time || form.endTime <= form.time)) {
      setFormError(
        "End time must be later than the start time."
      );
      return;
    }

    const request = {
      activityDate: selectedDate.toISOString().slice(0, 10),
      title,
      startTime: form.time ? `${form.time}:00` : null,
      endTime: form.endTime ? `${form.endTime}:00` : null,
      category: form.category,
      location: form.location.trim() || null,
      notes: form.notes.trim() || null,
    };

    setSaving(true);
    setFormError("");

    try {
      const savedActivity =
        editingId !== null
          ? await updateItineraryActivity(
              trip.id,
              editingId,
              request
            )
          : await createItineraryActivity(trip.id, request);

      const converted = toEditorActivity(
        savedActivity,
        startDate
      );

      onChange((previous) => {
        const previousActivities = previous.activities ?? [];

        return {
          ...previous,
          activities:
            editingId !== null
              ? previousActivities.map((activity) =>
                  activity.id === editingId
                    ? converted
                    : activity
                )
              : [...previousActivities, converted],
        };
      });

      closeForm();
    } catch (error) {
      setFormError(
        error.message || "Could not save the activity."
      );
    } finally {
      setSaving(false);
    }
  }


  return (
    <section className="manual-itinerary">
      <div className="manual-itinerary-heading">
        <p className="manual-itinerary-kicker">MANUAL PLANNING</p>
        <h1>Build Your Itinerary</h1>
        <p>{trip?.name} Â· Day {activeDay} of {totalDays}</p>
      </div>

      {loadingActivities && (
        <p role="status">Loading saved activities...</p>
      )}

      {activityError && (
        <div role="alert" className="manual-itinerary-error">
          {activityError}
        </div>
      )}

      <div className="manual-itinerary-layout">
        {/* Stationary left panel: header, form, then a separately scrolling list. */}
        <div className="manual-itinerary-panel">
          <div className="manual-itinerary-panel-header">
            <div>
              <h2>{formatDate(selectedDate, {
                weekday: "long", month: "long", day: "numeric",
              })}</h2>
              <p>
                {dayActivities.length} planned {dayActivities.length === 1 ? "activity" : "activities"}
              </p>
            </div>
            <button
              type="button"
              className="manual-itinerary-primary"
              onClick={openAddForm}
              disabled={loadingActivities || saving || !!activityError}
            >
              + Add Activity
            </button>
          </div>

          {/* Always directly below Add Activity, even when activities exist. */}
          {formOpen && (
            <form className="manual-itinerary-form" onSubmit={saveActivity}>
              <h3>{editingId !== null ? "Edit Activity" : "Add an Activity"}</h3>

              <label>
                Activity Name *
                <input
                  type="text"
                  required
                  value={form.title}
                  placeholder="e.g., Visit a museum"
                  onChange={(event) => updateField("title", event.target.value)}
                />
              </label>

              <div className="manual-itinerary-form-row">
                <label>
                  Start time
                  <input
                    type="time"
                    value={form.time}
                    onChange={(event) => updateField("time", event.target.value)}
                  />
                </label>
                <label>
                  End time
                  <input
                    type="time"
                    value={form.endTime}
                    onChange={(event) => updateField("endTime", event.target.value)}
                  />
                </label>
              </div>

              <label>
                Category
                <select
                  value={form.category}
                  onChange={(event) => updateField("category", event.target.value)}
                >
                  {CATEGORIES.map((category) => (
                    <option key={category} value={category}>{category}</option>
                  ))}
                </select>
              </label>

              <label>
                Location (optional)
                <input
                  type="text"
                  value={form.location}
                  placeholder="e.g., National Mall, Washington DC"
                  onChange={(event) => updateField("location", event.target.value)}
                />
              </label>

              <label>
                Description / Notes (optional)
                <textarea
                  rows="2"
                  value={form.notes}
                  placeholder="Additional details..."
                  onChange={(event) => updateField("notes", event.target.value)}
                />
              </label>

              {formError && <p className="manual-itinerary-error" role="alert">{formError}</p>}

              <div className="manual-itinerary-form-actions">
                <button type="button" className="manual-itinerary-cancel" onClick={closeForm}>
                  Cancel
                </button>
                <button
                  type="submit"
                  className="manual-itinerary-primary"
                  disabled={saving}
                >
                  {saving
                    ? "Saving..."
                    : editingId !== null
                      ? "Save Changes"
                      : "Add Activity"}
                </button>
              </div>
            </form>
          )}

          <div className="manual-itinerary-activity-list" aria-label="Activities for selected day">
            {dayActivities.length === 0 ? (
              <div className="manual-itinerary-empty">
                <h3>Nothing planned yet</h3>
                <p>Add an activity to start organizing this date.</p>
              </div>
            ) : (
              dayActivities.map((activity) => (
                <article key={activity.id} className="manual-itinerary-activity">
                  <div className="manual-itinerary-time">
                    {formatTimeRange(activity.time, activity.endTime)}
                  </div>
                  <div className="manual-itinerary-activity-actions">
                    <button type="button" onClick={() => editActivity(activity)}>Edit</button>
                    <button
                      type="button"
                      className="manual-itinerary-remove"
                      onClick={() => removeActivity(activity.id)}
                    >
                      Remove
                    </button>
                  </div>
                  <div className="manual-itinerary-activity-info">
                    <h3>{activity.title}</h3>
                    <span>{activity.category}</span>
                    {activity.location && <p>ðŸ“ {activity.location}</p>}
                    {activity.notes && <p>{activity.notes}</p>}
                  </div>
                </article>
              ))
            )}
          </div>
        </div>

        {/* Only the calendar's own content scrolls in its viewport. */}
        <ItineraryCalendar
          key={trip.id}
          startDate={startDate}
          endDate={endDate}
          selectedDate={selectedDate}
          activities={activities}
          onSelectDay={selectDay}
        />
      </div>

      <p className="manual-itinerary-notice">
        Activity changes are saved to your account when the request succeeds.
      </p>
    </section>
  );
}
