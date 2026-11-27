import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { CalendarDays, ListChecks, Plus, Trash2 } from "lucide-react";
import Sidebar from "../components/Sidebar";
import ConfirmDialog from "../components/ConfirmDialog";
import useDestinationPhoto from "../lib/useDestinationPhoto";
import { clearItineraryPreferences } from "../lib/itineraryPreferences";
import { listTrips, listItineraryActivities, deleteItinerary } from "../api/api";
import "../styles/Dashboard.css";
import "../styles/Trips.css";
import "../styles/Itinerary.css";

function formatTripDate(value) {
  if (!value) return "";
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(value));
  if (!match) return "";
  const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])));
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  }).format(date);
}

function daysBetween(start, end) {
  if (!start || !end) return 0;
  const toUtc = (iso) => {
    const [y, m, d] = iso.split("-").map(Number);
    return Date.UTC(y, m - 1, d);
  };
  return Math.round((toUtc(end) - toUtc(start)) / 86400000) + 1;
}

// Photo card for one saved itinerary
function ItineraryCard({ trip, activities, onDelete }) {
  const [photo, clearPhoto] = useDestinationPhoto(trip.destination);
  const place = trip.destination?.split(",")[0]?.trim() || trip.name;
  const totalDays = daysBetween(trip.startDate, trip.endDate);
  const plannedDays = new Set(activities.map((a) => a.activityDate)).size;

  return (
    <article className="mytrip">
      <Link to={`/itineraries/${trip.id}`} className="mytrip-photo" aria-label={`Open the ${trip.name} itinerary`}>
        {photo?.imageUrl && <img src={photo.imageUrl} alt="" loading="lazy" onError={clearPhoto} />}
        <span className="mytrip-status is-later">
          {activities.length} {activities.length === 1 ? "activity" : "activities"}
        </span>
        <span className="tb-wide mytrip-place">{place}</span>
      </Link>

      <div className="mytrip-body">
        <div>
          <h3>{trip.name}</h3>
          <p className="mytrip-destination">{trip.destination || "Destination not specified"}</p>
        </div>

        <div className="mytrip-details">
          {(trip.startDate || trip.endDate) && (
            <span>
              <CalendarDays size={15} aria-hidden="true" />
              {formatTripDate(trip.startDate)}
              {trip.endDate ? ` – ${formatTripDate(trip.endDate)}` : ""}
            </span>
          )}
          {totalDays > 0 && (
            <span>
              <ListChecks size={15} aria-hidden="true" />
              {plannedDays} of {totalDays} {totalDays === 1 ? "day" : "days"} planned
            </span>
          )}
        </div>

        <div className="mytrip-actions">
          <Link to={`/itineraries/${trip.id}`} className="mytrip-link is-main">View itinerary</Link>
          <button
            type="button"
            className="mytrip-delete"
            onClick={() => onDelete(trip)}
            aria-label={`Delete the ${trip.name} itinerary`}
            title="Delete itinerary"
          >
            <Trash2 size={17} aria-hidden="true" />
          </button>
        </div>
      </div>
    </article>
  );
}

export default function MyItineraries() {
  const [itineraries, setItineraries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [toDelete, setToDelete] = useState(null); // trip whose itinerary is being deleted
  const [deleting, setDeleting] = useState(false);

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
            <p>Your day-by-day plans for each trip, all in one place.</p>
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

        {!loading && itineraries.length > 0 && (
          <div className="mytrips-grid">
            {itineraries.map(({ trip, activities }) => (
              <ItineraryCard key={trip.id} trip={trip} activities={activities} onDelete={setToDelete} />
            ))}
          </div>
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
