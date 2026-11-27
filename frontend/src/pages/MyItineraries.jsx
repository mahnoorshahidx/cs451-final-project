import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, CalendarDays, ChevronDown, Clock, Plus, Trash2 } from "lucide-react";
import Sidebar from "../components/Sidebar";
import ConfirmDialog from "../components/ConfirmDialog";
import useDestinationPhoto from "../lib/useDestinationPhoto";
import { clearItineraryPreferences } from "../lib/itineraryPreferences";
import { listTrips, listItineraryActivities, deleteItinerary } from "../api/api";
import "../styles/Dashboard.css";
import "../styles/Trips.css";
import "../styles/Itinerary.css";
import "../styles/ItineraryList.css";
import { todayIso } from "../lib/formatDate";

function toUtc(iso) {
  const [y, m, d] = iso.split("-").map(Number);
  return Date.UTC(y, m - 1, d);
}

// Every date of the trip as "YYYY-MM-DD"
function tripDays(start, end) {
  if (!start || !end) return [];
  const days = [];
  for (let t = toUtc(start); t <= toUtc(end); t += 86400000) {
    days.push(new Date(t).toISOString().slice(0, 10));
  }
  return days;
}

function shortDay(iso, withWeekday = false) {
  if (!iso) return "";
  return new Intl.DateTimeFormat("en-US", {
    ...(withWeekday ? { weekday: "short" } : {}),
    month: "short",
    day: "numeric",
    timeZone: "UTC",
  }).format(new Date(toUtc(iso)));
}

function clockTime(value) {
  if (!/^\d{2}:\d{2}/.test(value || "")) return "";
  const [h, m] = value.split(":").map(Number);
  return `${h % 12 || 12}:${String(m).padStart(2, "0")} ${h < 12 ? "AM" : "PM"}`;
}

function byTime(a, b) {
  return (a.activityDate + (a.startTime || "99")).localeCompare(b.activityDate + (b.startTime || "99"));
}

// The first activity from today on
function nextActivity(activities, today) {
  return [...activities].sort(byTime).find((a) => a.activityDate >= today) ?? null;
}

// One pill per day: filled when the day has activities. Long trips show the first 21 days.
const STRIP_MAX = 21;
function DayStrip({ days, counts, today, size = "md" }) {
  const planned = days.filter((d) => counts[d]).length;
  return (
    <div className={`itl-strip is-${size}`} role="img" aria-label={`${planned} of ${days.length} days planned`}>
      {days.slice(0, STRIP_MAX).map((day, i) => (
        <span
          key={day}
          className={`itl-pill${counts[day] ? " is-planned" : ""}${day === today ? " is-today" : ""}`}
          title={`Day ${i + 1} · ${shortDay(day, true)} · ${counts[day] ? `${counts[day]} planned` : "nothing planned yet"}`}
        />
      ))}
      {days.length > STRIP_MAX && <span className="itl-more">+{days.length - STRIP_MAX}</span>}
    </div>
  );
}

function planStats(trip, activities) {
  const days = tripDays(trip.startDate, trip.endDate);
  const counts = {};
  for (const a of activities) counts[a.activityDate] = (counts[a.activityDate] || 0) + 1;
  const planned = days.filter((d) => counts[d]).length;
  return { days, counts, planned };
}

function DeleteButton({ trip, onDelete, className = "" }) {
  return (
    <button
      type="button"
      className={`itl-delete ${className}`}
      onClick={() => onDelete(trip)}
      aria-label={`Delete the ${trip.name} itinerary`}
      title="Delete itinerary"
    >
      <Trash2 size={17} aria-hidden="true" />
    </button>
  );
}

// The next trip, with a countdown and one day's plan
function NextTrip({ trip, activities, today, onDelete }) {
  const [photo, clearPhoto] = useDestinationPhoto(trip.destination);
  const { days, counts, planned } = planStats(trip, activities);
  const started = trip.startDate <= today;
  const until = Math.round((toUtc(trip.startDate) - toUtc(today)) / 86400000);

  // Today's plan while travelling; otherwise the first day with plans
  const focusDay = started ? today : days.find((d) => counts[d]) ?? days[0];
  const focusIndex = days.indexOf(focusDay);
  const agenda = activities.filter((a) => a.activityDate === focusDay).sort(byTime);

  return (
    <section className="itl-next" aria-labelledby="itl-next-title">
      <Link to={`/itineraries/${trip.id}`} className="itl-next-photo" tabIndex={-1} aria-hidden="true">
        {photo?.imageUrl && <img src={photo.imageUrl} alt="" onError={clearPhoto} />}
      </Link>

      <div className="itl-next-body">
        <span className="tb-kicker itl-next-kicker">
          {started ? "Happening now" : until === 1 ? "Next trip · tomorrow" : `Next trip · in ${until} days`}
        </span>
        <h2 id="itl-next-title" className="tb-wide">{trip.name}</h2>
        <p className="itl-dates">
          <CalendarDays size={15} aria-hidden="true" />
          {shortDay(trip.startDate)} – {shortDay(trip.endDate)}
          {trip.destination ? ` · ${trip.destination}` : ""}
        </p>

        <div className="itl-progress">
          <DayStrip days={days} counts={counts} today={today} size="lg" />
          <span>{planned} of {days.length} days planned</span>
        </div>

        <div className="itl-agenda">
          <p className="itl-agenda-title">
            {started ? "Today" : `Day ${focusIndex + 1}`} · {shortDay(focusDay, true)}
          </p>
          {agenda.length === 0 ? (
            <p className="itl-agenda-empty">Nothing planned for this day yet.</p>
          ) : (
            <ol>
              {agenda.slice(0, 3).map((a) => (
                <li key={a.id}>
                  <span className="itl-time">{clockTime(a.startTime) || "Any time"}</span>
                  <span className="itl-what">{a.title}</span>
                </li>
              ))}
              {agenda.length > 3 && <li className="itl-agenda-more">+{agenda.length - 3} more</li>}
            </ol>
          )}
        </div>

        <div className="itl-next-actions">
          <Link to={`/itineraries/${trip.id}`} className="tb-btn tb-btn-primary">
            {planned < days.length ? "Continue planning" : "Open itinerary"}
            <ArrowRight size={16} aria-hidden="true" />
          </Link>
          <DeleteButton trip={trip} onDelete={onDelete} />
        </div>
      </div>
    </section>
  );
}

// A later trip as a smaller card with its day strip
function ItineraryCard({ trip, activities, today, onDelete }) {
  const [photo, clearPhoto] = useDestinationPhoto(trip.destination);
  const { days, counts, planned } = planStats(trip, activities);
  const next = nextActivity(activities, today);
  const until = Math.round((toUtc(trip.startDate) - toUtc(today)) / 86400000);

  return (
    <article className="itl-card">
      <Link to={`/itineraries/${trip.id}`} className="itl-card-photo" tabIndex={-1} aria-hidden="true">
        {photo?.imageUrl && <img src={photo.imageUrl} alt="" loading="lazy" onError={clearPhoto} />}
        <span className="itl-countdown">{until <= 0 ? "Now" : until === 1 ? "Tomorrow" : `In ${until} days`}</span>
      </Link>
      <div className="itl-card-body">
        <div>
          <Link to={`/itineraries/${trip.id}`} className="itl-row-name">{trip.name}</Link>
          <p className="itl-dates">
            {shortDay(trip.startDate)} – {shortDay(trip.endDate)}
            {trip.destination ? ` · ${trip.destination}` : ""}
          </p>
        </div>
        <div className="itl-row-plan">
          <DayStrip days={days} counts={counts} today={today} />
          <span>{planned} of {days.length} days planned</span>
        </div>
        <p className="itl-nextup">
          <Clock size={14} aria-hidden="true" />
          {next ? (
            <span>
              Next up: <strong>{next.title}</strong> · {shortDay(next.activityDate)}
              {next.startTime ? `, ${clockTime(next.startTime)}` : ""}
            </span>
          ) : (
            <span>Nothing planned yet</span>
          )}
        </p>
        <div className="itl-row-actions">
          <Link to={`/itineraries/${trip.id}`} className="tb-btn tb-btn-outline">
            {planned < days.length ? "Keep planning" : "Open"}
          </Link>
          <DeleteButton trip={trip} onDelete={onDelete} />
        </div>
      </div>
    </article>
  );
}

// A past trip: compact, no photo
function PastRow({ trip, activities, onDelete }) {
  const { days, planned } = planStats(trip, activities);
  return (
    <li className="itl-past-row">
      <Link to={`/itineraries/${trip.id}`} className="itl-past-name">{trip.name}</Link>
      <span className="itl-past-meta">
        {trip.startDate ? `${shortDay(trip.startDate)} – ${shortDay(trip.endDate)}, ${(trip.endDate || trip.startDate).slice(0, 4)}` : "No dates"}
      </span>
      <span className="itl-past-meta">
        {activities.length} {activities.length === 1 ? "activity" : "activities"}
        {days.length > 0 ? ` · ${planned} of ${days.length} days` : ""}
      </span>
      <DeleteButton trip={trip} onDelete={onDelete} className="is-small" />
    </li>
  );
}

export default function MyItineraries() {
  const [itineraries, setItineraries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [toDelete, setToDelete] = useState(null); // trip whose itinerary is being deleted
  const [deleting, setDeleting] = useState(false);
  const [showPast, setShowPast] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function loadItineraries() {
      try {
        // An itinerary currently consists of activities saved under a trip ID.
        const trips = await listTrips();
        const records = await Promise.all(
          trips.map(async (trip) => ({
            trip,
            activities: await listItineraryActivities(trip.id),
          }))
        );

        if (!cancelled) {
          setItineraries(
            records
              .filter(({ activities }) => Array.isArray(activities) && activities.length > 0)
              .sort((a, b) =>
                (a.trip.startDate ?? "9999").localeCompare(b.trip.startDate ?? "9999")
              )
          );
        }
      } catch (err) {
        if (!cancelled) {
          setError(err.message || "Could not load your itineraries.");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    loadItineraries();
    return () => { cancelled = true; };
  }, []);

  async function confirmDelete() {
    setDeleting(true);
    try {
      await deleteItinerary(toDelete.id);
      clearItineraryPreferences(toDelete.id);
      setItineraries((current) => current.filter(({ trip }) => trip.id !== toDelete.id));
      setToDelete(null);
    } catch (err) {
      setError(err.message || "Could not delete the itinerary.");
      setToDelete(null);
    } finally {
      setDeleting(false);
    }
  }

  // Same split as My Trips: upcoming soonest first, past trips most recent first
  const today = todayIso();
  const upcoming = itineraries.filter(({ trip }) => trip.startDate && trip.endDate && trip.endDate >= today);
  const past = itineraries
    .filter(({ trip }) => !trip.startDate || !trip.endDate || trip.endDate < today)
    .sort((a, b) => (b.trip.startDate ?? "").localeCompare(a.trip.startDate ?? ""));

  return (
    <div className="dashboard-page mytrips-page itinerary-list-page">
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      <main className="dashboard-main mytrips-main">
        <header className="mytrips-header">
          <div className="mytrips-title">
            <span className="tb-kicker">
              {loading ? "Itineraries" : `${itineraries.length} saved`}
            </span>
            <h1 className="tb-wide">Itineraries</h1>
            <p>What you're doing each day, and which days still need a plan.</p>
          </div>

          <Link to="/create-itinerary" className="tb-btn tb-btn-primary">
            <Plus size={18} aria-hidden="true" />
            New itinerary
          </Link>
        </header>

        {loading && <p role="status" className="trips-message">Loading your itineraries...</p>}
        {!loading && error && (
          <p role="alert" className="error-text">Unable to load itineraries: {error}</p>
        )}

        {!loading && !error && itineraries.length === 0 && (
          <section className="mytrips-empty">
            <h2 className="tb-wide">No plans yet</h2>
            <p>Pick a trip and plan each day yourself, or let the AI plan it from your preferences.</p>
            <Link to="/create-itinerary" className="tb-btn tb-btn-accent">Plan an itinerary</Link>
          </section>
        )}

        {upcoming.length > 0 && (
          <NextTrip
            trip={upcoming[0].trip}
            activities={upcoming[0].activities}
            today={today}
            onDelete={setToDelete}
          />
        )}

        {upcoming.length > 1 && (
          <section className="itl-group" aria-labelledby="itl-later">
            <h2 id="itl-later" className="itl-group-title">Coming up</h2>
            <div className="itl-cards">
              {upcoming.slice(1).map(({ trip, activities }) => (
                <ItineraryCard key={trip.id} trip={trip} activities={activities} today={today} onDelete={setToDelete} />
              ))}
            </div>
          </section>
        )}

        {past.length > 0 && (
          <section className="itl-group itl-past">
            <button
              type="button"
              className="itl-past-toggle"
              aria-expanded={showPast}
              onClick={() => setShowPast((open) => !open)}
            >
              Past trips <span>{past.length}</span>
              <ChevronDown size={18} aria-hidden="true" className={showPast ? "is-open" : ""} />
            </button>
            {showPast && (
              <ul className="itl-past-list">
                {past.map(({ trip, activities }) => (
                  <PastRow key={trip.id} trip={trip} activities={activities} onDelete={setToDelete} />
                ))}
              </ul>
            )}
          </section>
        )}
      </main>

      {toDelete && (
        <ConfirmDialog
          title="Delete this itinerary?"
          onCancel={() => setToDelete(null)}
          busy={deleting}
          actions={[{ label: deleting ? "Deleting..." : "Delete itinerary", tone: "danger", onClick: confirmDelete }]}
        >
          <p>Every activity planned for {toDelete.name} is deleted. The trip and its budget stay.</p>
          <p>This can't be undone.</p>
        </ConfirmDialog>
      )}
    </div>
  );
}
