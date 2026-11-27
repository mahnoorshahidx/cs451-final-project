import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Plus } from "lucide-react";
import "../styles/Dashboard.css";
import "../styles/Trips.css";
import Sidebar from "../components/Sidebar";
import TripCard from "../components/TripCard";
import { deleteTrip, listTrips } from "../api/api";
import { todayIso } from "../lib/formatDate";

// My Trips (SCRUM-32). Lists all trips from the backend, with edit and delete on each card.
// Upcoming trips come first (soonest first), past trips after (most recent first).
function MyTrips() {
  const [trips, setTrips] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    listTrips()
      .then(setTrips)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  async function handleDelete(trip) {
    if (!window.confirm(`Delete "${trip.name}"? Its budget will be deleted too.`)) return;
    try {
      await deleteTrip(trip.id);
      setTrips((prev) => prev.filter((t) => t.id !== trip.id));
    } catch (err) {
      setError(err.message);
    }
  }

  const today = todayIso();
  const upcoming = trips
    .filter((trip) => trip.endDate >= today)
    .sort((a, b) => a.startDate.localeCompare(b.startDate));
  const past = trips
    .filter((trip) => trip.endDate < today)
    .sort((a, b) => b.startDate.localeCompare(a.startDate));

  return (
    <div className="dashboard-page mytrips-page">
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      <main className="dashboard-main mytrips-main">
        <header className="mytrips-header">
          <div className="mytrips-title">
            <span className="tb-kicker">
              {trips.length} trip{trips.length === 1 ? "" : "s"} planned
            </span>
            <h1 className="tb-wide">My trips</h1>
            <p>Every trip you're planning, soonest first.</p>
          </div>

          <Link to="/create-trip" className="tb-btn tb-btn-primary">
            <Plus size={18} aria-hidden="true" />
            New trip
          </Link>
        </header>

        {loading && <p className="trips-message">Loading your trips...</p>}
        {error && <p className="error-text">{error}</p>}

        {!loading && !error && trips.length === 0 && (
          <section className="mytrips-empty">
            <h2 className="tb-wide">Where to first?</h2>
            <p>You haven't planned any trips yet. Add one and we'll help you budget for it.</p>
            <Link to="/create-trip" className="tb-btn tb-btn-accent">Plan a trip</Link>
          </section>
        )}

        {upcoming.length > 0 && (
          <section className="mytrips-group" aria-labelledby="upcoming-heading">
            <h2 id="upcoming-heading" className="mytrips-group-title">
              Upcoming <span>{upcoming.length}</span>
            </h2>
            <div className="mytrips-grid">
              {upcoming.map((trip) => (
                <TripCard key={trip.id} trip={trip} onDelete={handleDelete} />
              ))}
            </div>
          </section>
        )}

        {past.length > 0 && (
          <section className="mytrips-group" aria-labelledby="past-heading">
            <h2 id="past-heading" className="mytrips-group-title">
              Past trips <span>{past.length}</span>
            </h2>
            <div className="mytrips-grid">
              {past.map((trip) => (
                <TripCard key={trip.id} trip={trip} onDelete={handleDelete} />
              ))}
            </div>
          </section>
        )}
      </main>
    </div>
  );
}

export default MyTrips;
