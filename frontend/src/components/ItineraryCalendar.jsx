import { useEffect, useState } from "react";
import "../styles/ItineraryCalendar.css";

const DAY_MS = 24 * 60 * 60 * 1000;
const HOURS = Array.from({ length: 24 }, (_, hour) => hour);
const HOUR_HEIGHT = 82; // Pixels per hour in Week and Day views.
const MIN_EVENT_HEIGHT = 36; // Short events stay tall enough for a readable title.
const COMPACT_EVENT_THRESHOLD = 64; // Hide secondary details below this height.
const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

function addDays(date, amount) {
  return new Date(date.getTime() + amount * DAY_MS);
}

function startOfWeek(date) {
  return addDays(date, -date.getUTCDay());
}

function startOfMonth(date) {
  return new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), 1));
}

function formatDate(date, options) {
  return new Intl.DateTimeFormat("en-US", {
    ...options,
    timeZone: "UTC",
  }).format(date);
}

function formatHour(hour) {
  return `${hour % 12 || 12} ${hour < 12 ? "AM" : "PM"}`;
}

function formatActivityTime(time) {
  if (!/^\d{2}:\d{2}$/.test(time || "")) return "Flexible";
  const [hours, minutes] = time.split(":").map(Number);
  return `${hours % 12 || 12}:${String(minutes).padStart(2, "0")} ${hours < 12 ? "AM" : "PM"}`;
}

function formatActivityTimeRange(activity) {
  const start = formatActivityTime(activity.time);
  if (!activity.endTime || !activity.time) return start;
  return `${start} – ${formatActivityTime(activity.endTime)}`;
}

function categoryColor(category) {
  return {
    Activity: "activity",
    "Food & Drink": "food",
    Sightseeing: "sightseeing",
    Nature: "nature",
    Transportation: "transportation",
  }[category] || "other";
}

// Convert HTML time input values (HH:mm) to minutes from midnight.
function timeToMinutes(value) {
  if (!/^\d{2}:\d{2}$/.test(value || "")) return null;
  const [hours, minutes] = value.split(":").map(Number);
  if (hours > 23 || minutes > 59) return null;
  return hours * 60 + minutes;
}

// Place overlapping activities side by side, with their vertical positions
// determined by their actual start and end times.
function positionTimedActivities(activities) {
  const events = activities
    .map((activity) => {
      const start = timeToMinutes(activity.time);
      if (start === null) return null;
      const explicitEnd = timeToMinutes(activity.endTime);
      // A missing end time uses a one-hour visual placeholder, not a saved end time.
      const end = explicitEnd !== null && explicitEnd > start
        ? explicitEnd
        : Math.min(1440, start + 60);
      // Use a visual minimum for layout only; retain the real event end time.
      // This also reserves another column for nearby events whose visible
      // blocks would otherwise collide, even when their real times do not.
      const visualEnd = Math.max(
        end,
        start + ((MIN_EVENT_HEIGHT + 4) / HOUR_HEIGHT) * 60
      );
      return { activity, start, end, visualEnd };
    })
    .filter(Boolean)
    .sort((a, b) => a.start - b.start || b.end - a.end ||
      String(a.activity.id).localeCompare(String(b.activity.id)));

  // Events belong to the same group whenever their intervals overlap directly
  // or indirectly. Separate groups may use the full column width.
  const groups = [];
  let group = [];
  let groupEnd = -1;
  for (const event of events) {
    if (group.length && event.start >= groupEnd) {
      groups.push(group);
      group = [];
      groupEnd = -1;
    }
    group.push(event);
    groupEnd = Math.max(groupEnd, event.visualEnd);
  }
  if (group.length) groups.push(group);

  return groups.flatMap((overlappingGroup) => {
    const laneEnds = [];
    const placed = overlappingGroup.map((event) => {
      let lane = laneEnds.findIndex((end) => end <= event.start);
      if (lane === -1) {
        lane = laneEnds.length;
        laneEnds.push(event.visualEnd);
      } else {
        laneEnds[lane] = event.visualEnd;
      }
      return { ...event, lane };
    });
    const laneCount = laneEnds.length;
    return placed.map((event) => ({ ...event, laneCount }));
  });
}

function TimelineActivity({ activity, view, style, timed = false, compact = false }) {
  return (
    <article
      className={`itcal-event itcal-event--${view} itcal-color--${categoryColor(activity.category)}${timed ? " itcal-timed-event" : ""}${compact ? " itcal-event--compact" : ""}`}
      style={style}
      title={`${activity.title} · ${formatActivityTimeRange(activity)}${activity.location ? ` · ${activity.location}` : ""}`}
    >
      <strong className="itcal-event-title">{activity.title}</strong>
      <span className="itcal-event-time">{formatActivityTimeRange(activity)}</span>
      {activity.location && (
        <span className="itcal-event-location">📍 {activity.location}</span>
      )}
      {activity.notes && (
        <p className="itcal-event-description">{activity.notes}</p>
      )}
      {view === "day" && (
        <span className="itcal-event-category">{activity.category}</span>
      )}
    </article>
  );
}

export default function ItineraryCalendar({
  startDate,
  endDate,
  selectedDate,
  activities,
  onSelectDay,
}) {
  const [view, setView] = useState("month");
  const [visibleMonth, setVisibleMonth] = useState(() => startOfMonth(selectedDate));

  useEffect(() => {
    setVisibleMonth(startOfMonth(selectedDate));
  }, [selectedDate.getTime()]);

  const weekStart = startOfWeek(selectedDate);
  const weekDates = Array.from({ length: 7 }, (_, i) => addDays(weekStart, i));
  const displayedDates = view === "week" ? weekDates : [selectedDate];
  const firstTripMonth = startOfMonth(startDate);
  const lastTripMonth = startOfMonth(endDate);

  const canPrevious =
    view === "month"
      ? visibleMonth > firstTripMonth
      : view === "week"
        ? addDays(weekStart, -1) >= startDate
        : selectedDate > startDate;
  const canNext =
    view === "month"
      ? visibleMonth < lastTripMonth
      : view === "week"
        ? addDays(weekStart, 7) <= endDate
        : selectedDate < endDate;

  function navigate(direction) {
    if (view === "month") {
      setVisibleMonth((previous) =>
        new Date(Date.UTC(previous.getUTCFullYear(), previous.getUTCMonth() + direction, 1))
      );
      return;
    }
    const requested = addDays(selectedDate, direction * (view === "week" ? 7 : 1));
    const clamped = new Date(Math.min(endDate.getTime(), Math.max(startDate.getTime(), requested.getTime())));
    onSelectDay(Math.round((clamped - startDate) / DAY_MS) + 1);
  }

  function pickDate(date) {
    onSelectDay(Math.round((date - startDate) / DAY_MS) + 1);
  }

  function eventsForDate(date) {
    const tripDay = Math.round((date - startDate) / DAY_MS) + 1;
    if (date < startDate || date > endDate) return [];
    return activities
      .filter((activity) => activity.day === tripDay)
      .sort((a, b) => (a.time || "99:99").localeCompare(b.time || "99:99"));
  }

  const title = view === "month"
    ? formatDate(visibleMonth, { month: "long", year: "numeric" })
    : view === "week"
      ? `${formatDate(weekStart, { month: "short", day: "numeric" })} – ${formatDate(addDays(weekStart, 6), { month: "short", day: "numeric", year: "numeric" })}`
      : formatDate(selectedDate, { weekday: "long", month: "long", day: "numeric", year: "numeric" });

  const monthDays = new Date(
    Date.UTC(visibleMonth.getUTCFullYear(), visibleMonth.getUTCMonth() + 1, 0)
  ).getUTCDate();
  const firstWeekday = visibleMonth.getUTCDay();
  const monthCellCount = Math.ceil((firstWeekday + monthDays) / 7) * 7;

  return (
    <section className="itcal" aria-label="Itinerary calendar">
      <header className="itcal-header">
        <div className="itcal-view-toggle" role="group" aria-label="Calendar view">
          {[
            ["month", "Month"],
            ["week", "Week"],
            ["day", "Day"],
          ].map(([mode, label]) => (
            <button
              key={mode}
              type="button"
              className={view === mode ? "is-active" : ""}
              aria-pressed={view === mode}
              onClick={() => {
                setView(mode);
                setVisibleMonth(startOfMonth(selectedDate));
              }}
            >
              {label}
            </button>
          ))}
        </div>
        <div className="itcal-navigation">
          <button type="button" aria-label={`Previous ${view}`} disabled={!canPrevious} onClick={() => navigate(-1)}>‹</button>
          <h2>{title}</h2>
          <button type="button" aria-label={`Next ${view}`} disabled={!canNext} onClick={() => navigate(1)}>›</button>
        </div>
      </header>

      {view === "month" ? (
        <div className="itcal-scroll" role="region" aria-label="Month calendar" tabIndex="0">
          <div className="itcal-month-grid">
            {WEEKDAYS.map((day) => (
              <span key={day} className="itcal-month-weekday">{day}</span>
            ))}
            {Array.from({ length: monthCellCount }, (_, index) => {
              const number = index - firstWeekday + 1;
              if (number < 1 || number > monthDays) {
                return <div key={`blank-${index}`} className="itcal-month-cell is-blank" aria-hidden="true" />;
              }
              const date = new Date(Date.UTC(visibleMonth.getUTCFullYear(), visibleMonth.getUTCMonth(), number));
              const available = date >= startDate && date <= endDate;
              const selected = date.getTime() === selectedDate.getTime();
              const items = eventsForDate(date);
              return (
                <div key={number} className={`itcal-month-cell${selected ? " is-selected" : ""}${available ? "" : " is-outside"}`}>
                  <button
                    type="button"
                    className={`itcal-month-date${selected ? " is-selected" : ""}`}
                    disabled={!available}
                    aria-pressed={selected}
                    aria-label={formatDate(date, { weekday: "long", month: "long", day: "numeric", year: "numeric" })}
                    onClick={() => pickDate(date)}
                  >
                    {number}
                  </button>
                  <div className="itcal-month-activities">
                    {items.slice(0, 3).map((item) => (
                      <button
                        key={item.id}
                        type="button"
                        className={`itcal-month-pill itcal-color--${categoryColor(item.category)}`}
                        title={`${formatActivityTimeRange(item)} · ${item.title}`}
                        onClick={() => {
                          pickDate(date);
                          setView("day");
                        }}
                      >
                        {item.title}
                      </button>
                    ))}
                    {items.length > 3 && (
                      <button
                        type="button"
                        className="itcal-more"
                        onClick={() => {
                          pickDate(date);
                          setView("day");
                        }}
                      >
                        +{items.length - 3} more
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      ) : (
        <div className="itcal-scroll" role="region" aria-label={`${view} schedule`} tabIndex="0">
          <div className={`itcal-timeline itcal-timeline--${view}`}>
            <div className="itcal-timeline-corner" aria-hidden="true" />
            {displayedDates.map((date) => {
              const active = date.getTime() === selectedDate.getTime();
              return (
                <button
                  key={date.toISOString()}
                  type="button"
                  className={`itcal-timeline-day${active ? " is-selected" : ""}`}
                  disabled={date < startDate || date > endDate}
                  onClick={() => pickDate(date)}
                >
                  {formatDate(date, { weekday: "short", month: "short", day: "numeric" })}
                </button>
              );
            })}

            {/* Unscheduled activities live in the separate Anytime row. */}
            <div className="itcal-anytime-label">Anytime</div>
            {displayedDates.map((date) => (
              <div key={`anytime-${date.toISOString()}`} className="itcal-anytime-cell">
                {eventsForDate(date)
                  .filter((item) => timeToMinutes(item.time) === null)
                  .map((item) => (
                    <TimelineActivity key={item.id} activity={item} view={view} />
                  ))}
              </div>
            ))}

            {/* One continuous 24-hour ruler, instead of separate event rows. */}
            <div className="itcal-time-ruler" aria-hidden="true">
              {HOURS.map((hour) => (
                <div
                  key={hour}
                  className="itcal-hour-mark"
                  style={{ top: `${hour * HOUR_HEIGHT}px` }}
                >
                  {formatHour(hour)}
                </div>
              ))}
            </div>

            {displayedDates.map((date) => {
              const positioned = positionTimedActivities(eventsForDate(date));
              return (
                <div
                  key={`track-${date.toISOString()}`}
                  className={`itcal-day-track${date < startDate || date > endDate ? " is-outside" : ""}`}
                >
                  {positioned.map(({ activity, start, end, lane, laneCount }) => (
                    <TimelineActivity
                      key={activity.id}
                      activity={activity}
                      view={view}
                      timed
                      compact={((end - start) / 60) * HOUR_HEIGHT - 4 < COMPACT_EVENT_THRESHOLD}
                      style={{
                        top: `${(start / 60) * HOUR_HEIGHT + 2}px`,
                        height: `${Math.max(MIN_EVENT_HEIGHT, ((end - start) / 60) * HOUR_HEIGHT - 4)}px`,
                        left: `calc(${(lane / laneCount) * 100}% + 2px)`,
                        width: `calc(${100 / laneCount}% - 4px)`,
                      }}
                    />
                  ))}
                </div>
              );
            })}
          </div>
        </div>
      )}
      <p className="itcal-help">
        {view === "month"
          ? "Activities appear in time order. Select an activity or +more to open its day."
          : "Scroll inside the calendar for later hours. Timed blocks span their duration; overlapping activities appear side by side."}
      </p>
    </section>
  );
}
