import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, CheckCircle2 } from "lucide-react";
import Sidebar from "../components/Sidebar";
import ItineraryViewer from "../components/ItineraryViewer";
import { getTrip } from "../api/api";
import "../styles/Dashboard.css";
import "../styles/Itinerary.css";

export default function ViewItinerary() {
  const { tripId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const justSaved = location.state?.saved;

  // { id, trip, error } for the last trip that finished loading
  const [result, setResult] = useState({ id: null, trip: null, error: "" });
  const loading = result.id !== tripId;

  useEffect(() => {
    let cancelled = false;

    getTrip(tripId)
      .then((data) => {
        if (!cancelled) setResult({ id: tripId, trip: data, error: "" });
      })
      .catch((err) => {
        if (!cancelled) setResult({ id: tripId, trip: null, error: err.message || "Could not load this trip." });
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
            <ArrowLeft size={16} aria-hidden="true" /> All itineraries
          </Link>
        </header>

        <div className="itinerary-body">
          <div className="itinerary-content itinerary-content--viewer">
            {justSaved && (
              <p className="itinerary-saved-banner" role="status">
                <CheckCircle2 size={18} aria-hidden="true" />
                Itinerary saved. You'll find it under Itinerary any time.
              </p>
            )}
            {loading && <p role="status">Loading itinerary...</p>}
            {!loading && result.error && <p role="alert" className="error-text">{result.error}</p>}
            {!loading && result.trip && (
              <ItineraryViewer
                trip={result.trip}
                standalone
                onDeleted={() => navigate("/itineraries")}
              />
            )}
          </div>
        </div>
      </main>
    </div>
  );
}
