import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import ItineraryViewer from "../components/ItineraryViewer";
import { getTrip } from "../api/api";
import "../styles/Dashboard.css";
import "../styles/CreateItinerary.css";
import "../styles/Itineraries.css";

export default function ViewItinerary() {
  const { tripId } = useParams();
  const [trip, setTrip] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    setTrip(null);

    getTrip(tripId)
      .then((data) => {
        if (!cancelled) setTrip(data);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || "Could not load this trip.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => { cancelled = true; };
  }, [tripId]);

  return (
    <div className="dashboard-page itinerary-page">
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      <main className="dashboard-main itinerary-main itinerary-main--viewer">
        <header className="itinerary-topbar">
          <Link to="/itineraries" className="itinerary-exit">
            ← Back to Itineraries
          </Link>
        </header>

        <div className="itinerary-body">
          <div className="itinerary-content itinerary-content--viewer">
            {loading && <p role="status">Loading itinerary...</p>}
            {!loading && error && <p role="alert" className="error-text">{error}</p>}
            {!loading && !error && trip && (
              <ItineraryViewer trip={trip} standalone />
            )}
          </div>
        </div>
      </main>
    </div>
  );
}
