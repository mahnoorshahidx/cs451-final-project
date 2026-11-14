import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import { listTrips, listItineraryActivities } from "../api/api";
import "../styles/Dashboard.css";
import "../styles/Trips.css";
import "../styles/Itineraries.css";

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

export default function MyItineraries() {
  const [itineraries, setItineraries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

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

  return (
    <div className="dashboard-page itinerary-list-page">
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      <main className="dashboard-main">
        <header className="dashboard-header" />

        <section className="dashboard-banner">
          <div>
            <h1>Itinerary</h1>
            <p>Your saved trip itineraries, all in one place.</p>
          </div>
        </section>

        <section className="my-trips">
          <div className="trips-section-header">
            <h2>
              {loading ? "Saved itineraries" : `${itineraries.length} ${itineraries.length === 1 ? "itinerary" : "itineraries"}`}
            </h2>
            <Link to="/create-itinerary" className="trips-new-btn">
              + New Itinerary
            </Link>
          </div>

          {loading && <p role="status" className="trips-message">Loading your itineraries...</p>}
          {!loading && error && (
            <p role="alert" className="error-text">Unable to load itineraries: {error}</p>
          )}
          {!loading && !error && itineraries.length === 0 && (
            <p className="trips-message">
              You don't have any saved itineraries yet. Choose <strong>New Itinerary</strong> to start planning.
            </p>
          )}

          {!loading && !error && (
            <div className="trip-list">
              {itineraries.map(({ trip, activities }) => (
                <article className="trip-card itinerary-list-card" key={trip.id}>
                  <div className="trip-card-top">
                    <h3>{trip.name}</h3>
                    <span className="trip-card-badge">Saved</span>
                  </div>
                  <p className="trip-card-destination">
                    📍 {trip.destination || "Destination not specified"}
                  </p>
                  {(trip.startDate || trip.endDate) && (
                    <p className="trip-card-meta">
                      📅 {formatTripDate(trip.startDate)}
                      {trip.endDate ? ` – ${formatTripDate(trip.endDate)}` : ""}
                    </p>
                  )}
                  <p className="trip-card-meta">
                    {activities.length} saved {activities.length === 1 ? "activity" : "activities"}
                  </p>
                  <div className="trip-card-actions">
                    <Link to={`/itineraries/${trip.id}`} className="trip-card-link">
                      View Itinerary →
                    </Link>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      </main>
    </div>
  );
}
