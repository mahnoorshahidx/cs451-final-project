
import { Link } from "react-router-dom";
import { useEffect, useState } from "react";
import { listTrips } from "../api/api";
import Sidebar from "../components/Sidebar";
import "../styles/Dashboard.css";
import "../styles/CreateItinerary.css";


function CreateItinerary() {
  const [trips, setTrips] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // Retrieve the user's saved trips when the page opens.
  useEffect(() => {
    listTrips()
      .then((data) => setTrips(data))
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="dashboard-page itinerary-page">
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      <main className="dashboard-main itinerary-main">

        {/* Top bar */}
        <header className="itinerary-topbar">
          <Link to="/trips" className="itinerary-exit">
            × &nbsp; Exit
          </Link>
        </header>

        {/* Progress indicator */}
        <div className="itinerary-progress">
          <div className="itinerary-progress-header">
            <span>CREATE YOUR ITINERARY</span>
            <span>Step 1 of 4</span>
          </div>

          <div className="itinerary-progress-track">
            <div className="itinerary-progress-active" />
            <div />
            <div />
            <div />
          </div>
        </div>

        {/* Main questionnaire */}
        <div className="itinerary-body">
          <div className="itinerary-content">

            <div className="itinerary-heading">
              <h1>Which trip are we planning?</h1>
              <p>
                Select an existing trip to start building
                your itinerary.
              </p>
            </div>

            {/* Loading message */}
            {loading && (
              <p role="status">
                Loading your trips...
              </p>
            )}

            {/* Error message */}
            {!loading && error && (
              <p role="alert" className="error-text">
                Unable to load trips: {error}
              </p>
            )}

            {/* No trips found */}
            {!loading && !error && trips.length === 0 && (
              <p>
                You haven't created any trips yet.
                Create a trip to get started.
              </p>
            )}

            {/* Saved trip cards */}
            {!loading && !error && trips.length > 0 && (
              <section className="itinerary-trip-list">
                {trips.map((trip) => (
                  <div
                    key={trip.id}
                    className="itinerary-trip-card"
                  >
                    <div className="itinerary-trip-info">
                      <h2>{trip.name}</h2>

                      <p>
                        {trip.destination || "Destination not provided"}
                      </p>

                      <div className="itinerary-trip-meta">
                        {/*
                          Additional date/traveler fields
                          will be mapped after confirming
                          the API response field names.
                        */}
                      </div>
                    </div>

                    <span
                      className="itinerary-radio"
                      aria-hidden="true"
                    />
                  </div>
                ))}
              </section>
            )}

            <Link
              to="/create-trip"
              className="itinerary-new-trip"
            >
              + Create a new trip
            </Link>

            {/* Bottom navigation */}
            <footer className="itinerary-footer">
              <Link to="/trips" className="itinerary-back">
                ← Back
              </Link>

              <button
                type="button"
                className="itinerary-continue"
                disabled
                title="Trip selection will be enabled in a later feature."
              >
                Continue →
              </button>
            </footer>

          </div>
        </div>

      </main>
    </div>
  );
}

export default CreateItinerary;
