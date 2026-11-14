import ItineraryViewer from "../components/ItineraryViewer";
import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import { listTrips } from "../api/api";
import "../styles/Dashboard.css";
import "../styles/CreateItinerary.css";
import ManualItineraryEditor from "../components/ManualItineraryEditor";
import AIItineraryQuestionnaire from "../components/AIItineraryQuestionnaire";

function CreateItinerary() {

  // Temporary manual itinerary drafts, organized by trip ID.
  const [manualPlans, setManualPlans] = useState({});

  const [trips, setTrips] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // NEW: Store which trip the user selected.
  const [selectedTripId, setSelectedTripId] = useState(null);

  // NEW: Track the current questionnaire step.
  const [step, setStep] = useState(1);

  // Stores the selected itinerary planning method
  const [planningMethod, setPlanningMethod] = useState(null);
  const [aiPreferences, setAiPreferences] = useState({});

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

  const updateManualPlan = useCallback((change) => {
    if (selectedTripId == null) return;

    setManualPlans((previous) => {
      const existing = previous[selectedTripId] ?? {
        activeDay: 1,
        activities: [],
        nextId: 1,
      };

      const updated =
        typeof change === "function"
          ? change(existing)
          : change;

      return {
        ...previous,
        [selectedTripId]: updated,
      };
    });
  }, [selectedTripId]);

  return (
    <div className="dashboard-page itinerary-page">
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      <main
        className={`dashboard-main itinerary-main ${step === 3 && planningMethod === "manual"
          ? "itinerary-main--manual"
          : step === 4 && planningMethod === "manual"
            ? "itinerary-main--viewer"
            : ""
          }`}
      >

        {/* Top bar */}
        <header className="itinerary-topbar">
          <Link to="/itineraries" className="itinerary-exit">
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
          <div
            className={`itinerary-content ${step === 3 && planningMethod === "manual"
              ? "itinerary-content--manual"
              : step === 4 && planningMethod === "manual"
                ? "itinerary-content--viewer"
                : ""
              }`}
          >

            {step === 4 && planningMethod === "manual" ? (
              <ItineraryViewer trip={selectedTrip} />
            ) : step === 1 ? (
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

            ) : step === 2 ? (
              <>
                {/* Step 2: Choose planning method */}
                <div className="itinerary-heading">
                  <h1>How would you like to plan?</h1>
                  <p>
                    Choose how to build your itinerary
                    for <strong>{selectedTrip?.name}</strong>.
                  </p>
                </div>

                <section
                  className="itinerary-method-list"
                  aria-label="Choose your planning method"
                >
                  {/* Manual planning card */}
                  <button
                    type="button"
                    className={`itinerary-method-card ${planningMethod === "manual" ? "is-selected" : ""
                      }`}
                    aria-pressed={planningMethod === "manual"}
                    onClick={() => setPlanningMethod("manual")}
                  >
                    <span className="itinerary-method-icon" aria-hidden="true">
                      ▦
                    </span>

                    <span className="itinerary-method-info">
                      <strong>Plan Manually</strong>
                      <span>
                        Build your itinerary yourself by adding
                        activities, restaurants, and places to visit.
                      </span>
                    </span>
                  </button>

                  {/* AI planning card */}
                  <button
                    type="button"
                    className={`itinerary-method-card ${planningMethod === "ai" ? "is-selected" : ""
                      }`}
                    aria-pressed={planningMethod === "ai"}
                    onClick={() => setPlanningMethod("ai")}
                  >
                    <span className="itinerary-method-icon" aria-hidden="true">
                      ✧
                    </span>

                    <span className="itinerary-method-info">
                      <strong>Plan with AI</strong>
                      <span>
                        Get personalized itinerary suggestions
                        based on your interests and travel preferences.
                      </span>
                    </span>
                  </button>
                </section>
              </>

            ) : planningMethod === "manual" ? (
              <ManualItineraryEditor
                trip={selectedTrip}
                plan={manualPlans[selectedTrip.id]}
                onChange={updateManualPlan}
              />

            ) : (
              <>
                <AIItineraryQuestionnaire
                  key={selectedTrip.id}
                  trip={selectedTrip}
                  initialPreferences={aiPreferences[selectedTrip.id]}
                  onBack={() => setStep(2)}
                  onSave={(preferences) => {
                    setAiPreferences((previous) => ({
                      ...previous,
                      [selectedTrip.id]: preferences,
                    }));
                  }}
                />

                {aiPreferences[selectedTrip.id] && (
                  <p role="status" className="ai-questionnaire-saved">
                    Preferences saved for this session.
                    AI itinerary generation will be connected next.
                  </p>
                )}
              </>
            )}


            {/* Bottom navigation */}

            <footer
              className="itinerary-footer"
              style={
                step === 3 && planningMethod === "ai"
                  ? { display: "none" }
                  : undefined
              }
            >
              {step === 1 ? (
                <Link to="/trips" className="itinerary-back">
                  ← Back
                </Link>
              ) : (
                <button
                  type="button"
                  className="itinerary-back"
                  onClick={() => setStep((previous) => Math.max(previous - 1, 1))}
                >
                  ← Back
                </button>
              )}

              {step < 4 && !(step === 3 && planningMethod === "ai") && (
                <button
                  type="button"
                  className="itinerary-next"
                  disabled={
                    (step === 1 && selectedTripId === null) ||
                    (step === 2 && !planningMethod) ||
                    (step === 3 && planningMethod !== "manual")
                  }
                  onClick={() =>
                    setStep((previous) => Math.min(previous + 1, 4))
                  }
                >
                  {step === 3
                    ? "Go to Itinerary View →"
                    : "Continue →"}
                </button>
              )}
            </footer>


          </div>
        </div>

      </main>
    </div>
  );
}

export default CreateItinerary;
