
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import { listTrips } from "../api/api";
import "../styles/Dashboard.css";
import "../styles/CreateItinerary.css";

function CreateItinerary() {
  const [trips, setTrips] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // NEW: Store which trip the user selected.
  const [selectedTripId, setSelectedTripId] = useState(null);

  // NEW: Track the current questionnaire step.
  const [step, setStep] = useState(1);

  // Retrieve saved trips.
  useEffect(() => {
    listTrips()
      .then((data) => setTrips(data))
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  // Find the complete record for the selected trip.
  const selectedTrip = trips.find(
    (trip) => trip.id === selectedTripId
  );

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
            <span>Step {step} of 4</span>
          </div>

          <div className="itinerary-progress-track">
            {[1, 2, 3, 4].map((number) => (
              <div
                key={number}
                className={
                  number <= step
                    ? "itinerary-progress-active"
                    : ""
                }
              />
            ))}
          </div>
        </div>

        {/* Main questionnaire */}
        <div className="itinerary-body">
          <div className="itinerary-content">

            {step === 1 ? (
              <>
                <div className="itinerary-heading">
                  <h1>Which trip are we planning?</h1>
                  <p>
                    Select an existing trip to start
                    building your itinerary.
                  </p>
                </div>

                {loading && (
                  <p role="status">Loading your trips...</p>
                )}

                {!loading && error && (
                  <p role="alert" className="error-text">
                    Unable to load trips: {error}
                  </p>
                )}

                {!loading && !error &&
                  trips.length === 0 && (
                    <p>
                      You haven't created any trips yet.
                      Create a trip to get started.
                    </p>
                  )}

                {/* Selectable trip cards */}
                {!loading && !error &&
                  trips.length > 0 && (
                    <section className="itinerary-trip-list">
                      {trips.map((trip) => (
                        <button
                          key={trip.id}
                          type="button"
                          aria-pressed={
                            selectedTripId === trip.id
                          }
                          onClick={() =>
                            setSelectedTripId(trip.id)
                          }
                          className={
                            "itinerary-trip-card " +
                            (selectedTripId === trip.id
                              ? "is-selected"
                              : "")
                          }
                        >
                          <div className="itinerary-trip-info">
                            <h2>{trip.name}</h2>

                            <p>
                              {trip.destination ||
                                "Destination not provided"}
                            </p>
                          </div>

                          <span
                            className="itinerary-radio"
                            aria-hidden="true"
                          />
                        </button>
                      ))}
                    </section>
                  )}

                <Link
                  to="/create-trip"
                  className="itinerary-new-trip"
                >
                  + Create a new trip
                </Link>
              </>
            ) : (
              <>
                {/* Temporary next-step placeholder */}
                <div className="itinerary-heading">
                  <h1>Let's plan your itinerary</h1>
                  <p>
                    Selected trip: {selectedTrip?.name}
                  </p>
                </div>

                <p>
                  Next, you'll choose how you want to
                  plan this trip.
                </p>
              </>
            )}

            {/* Bottom navigation */}
            <footer className="itinerary-footer">
              {step === 1 ? (
                <Link
                  to="/trips"
                  className="itinerary-back"
                >
                  ← Back
                </Link>
              ) : (
                <button
                  type="button"
                  className="itinerary-back itinerary-back-button"
                  onClick={() => setStep(1)}
                >
                  ← Back
                </button>
              )}

              <button
                type="button"
                className="itinerary-continue"
                disabled={step !== 1 || !selectedTrip}
                onClick={() => setStep(2)}
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
