import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import "../styles/Dashboard.css";
import "../styles/Trips.css";
import Sidebar from "../components/Sidebar";
import TripCard from "../components/TripCard";
import { deleteTrip, listTrips } from "../api/api";

// New page for My Trips (SCRUM-32). Lists all trips from the backend,
// with edit and delete on each card. Reuses the dashboard layout.
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

  return (
    <div className="dashboard-page">
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      <main className="dashboard-main">
        <header className="dashboard-header"></header>

        <section className="dashboard-banner">
          <div>
            <h1>My Trips</h1>
            <p>Every trip you're planning, soonest first.</p>
          </div>
        </section>

        <section className="my-trips">
          <div className="trips-section-header">
            <h2>{trips.length} trip{trips.length === 1 ? "" : "s"}</h2>
            <Link to="/create-trip" className="trips-new-btn">+ New trip</Link>
          </div>

          {loading && <p className="trips-message">Loading your trips...</p>}
          {error && <p className="error-text">{error}</p>}

          {!loading && !error && trips.length === 0 && (
            <p className="trips-message">
              You haven't planned any trips yet. <Link to="/create-trip">Create your first one</Link>.
            </p>
          )}

          <div className="trip-list">
            {trips.map((trip) => (
              <TripCard key={trip.id} trip={trip} onDelete={handleDelete} />
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}

export default MyTrips;