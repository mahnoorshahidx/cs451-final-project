import ItineraryViewer from "../components/ItineraryViewer";
import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Check, PenLine, Sparkles, X } from "lucide-react";
import Sidebar from "../components/Sidebar";
import ConfirmDialog from "../components/ConfirmDialog";
import {
  listTrips,
  listItineraryActivities,
  generateItinerary,
  deleteItineraryActivity,
} from "../api/api";
import {
  generationRequest,
  loadItineraryPreferences,
  saveItineraryPreferences,
} from "../lib/itineraryPreferences";
import "../styles/Dashboard.css";
import "../styles/Itinerary.css";
import ManualItineraryEditor from "../components/ManualItineraryEditor";
import AIItineraryQuestionnaire from "../components/AIItineraryQuestionnaire";

function CreateItinerary() {
  const navigate = useNavigate();

  // /create-itinerary?trip=12&method=ai opens the AI questions for that trip
  // (used by "Change preferences" on a saved itinerary)
  const [searchParams] = useSearchParams();
  const startTripId = Number(searchParams.get("trip")) || null;
  const startMethod = ["ai", "manual"].includes(searchParams.get("method")) ? searchParams.get("method") : null;

  // Temporary manual itinerary drafts, organized by trip ID.
  const [manualPlans, setManualPlans] = useState({});

  const [trips, setTrips] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // NEW: Store which trip the user selected.
  const [selectedTripId, setSelectedTripId] = useState(startTripId);

  // NEW: Track the current questionnaire step.
  const [step, setStep] = useState(startTripId ? (startMethod ? 3 : 2) : 1);

  // Stores the selected itinerary planning method
  const [planningMethod, setPlanningMethod] = useState(startMethod);
  const [aiPreferences, setAiPreferences] = useState({});

  // AI itinerary generation
  const [generating, setGenerating] = useState(false);
  const [generateError, setGenerateError] = useState("");

  // Asked when the trip already has activities: { preferences, count }
  const [pendingGenerate, setPendingGenerate] = useState(null);
  // Activities the AI added in this visit, so "Discard" can remove them
  const [sessionActivityIds, setSessionActivityIds] = useState([]);
  const [confirmDiscard, setConfirmDiscard] = useState(false);
  const [discarding, setDiscarding] = useState(false);
  const [finishError, setFinishError] = useState("");

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

  // Sends the questionnaire answers to the AI, which saves the suggested activities on the trip.
  // If the trip already has activities, first asks whether to replace them or add to them.
  async function handleGenerate(preferences) {
    const tripId = selectedTrip.id;
    setAiPreferences((previous) => ({ ...previous, [tripId]: preferences }));
    saveItineraryPreferences(tripId, preferences);
    setGenerateError("");

    try {
      const existing = await listItineraryActivities(tripId);
      if (existing.length > 0) {
        setPendingGenerate({ preferences, count: existing.length });
        return;
      }
    } catch {
      // Couldn't check; just add to whatever is there
    }
    runGenerate(preferences, false);
  }

  async function runGenerate(preferences, replaceExisting) {
    setPendingGenerate(null);
    setGenerating(true);
    try {
      const created = await generateItinerary(
        selectedTrip.id,
        generationRequest(preferences, { replaceExisting })
      );
      setSessionActivityIds((ids) => [...ids, ...created.map((a) => a.id)]);
      setStep(4);
    } catch (err) {
      setGenerateError(
        err.message || "We couldn't generate your itinerary. Please try again."
      );
    } finally {
      setGenerating(false);
    }
  }

  const trackGenerated = useCallback((created) => {
    setSessionActivityIds((ids) => [...ids, ...created.map((a) => a.id)]);
  }, []);

  // Removes what the AI added during this visit, then leaves
  async function discardItinerary() {
    setDiscarding(true);
    setFinishError("");
    try {
      for (const id of sessionActivityIds) {
        try {
          await deleteItineraryActivity(selectedTrip.id, id);
        } catch (err) {
          // Already gone (replaced by a later regenerate, or edited away) is fine
          if (err.status !== 404) throw err;
        }
      }
      navigate("/itineraries");
    } catch (err) {
      setFinishError(`Couldn't discard everything: ${err.message}. Some activities may still be saved.`);
      setConfirmDiscard(false);
    } finally {
      setDiscarding(false);
    }
  }

  return (
    <div className="dashboard-page itinerary-page">
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      <main
        className={`dashboard-main itinerary-main ${step === 3 && planningMethod === "manual"
          ? "itinerary-main--manual"
          : step === 4
            ? "itinerary-main--viewer"
            : ""
          }`}
      >

        {/* Top bar */}
        <header className="itinerary-topbar">
          <Link to="/itineraries" className="itinerary-exit">
            <X size={16} aria-hidden="true" /> Exit
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
              : step === 4
                ? "itinerary-content--viewer"
                : ""
              }`}
          >

            {step > 1 && !selectedTrip ? (
              // Opened with ?trip= before the trips have loaded (or the trip is gone)
              loading ? (
                <p role="status">Loading your trip...</p>
              ) : (
                <p role="alert" className="error-text">
                  {error || "That trip couldn't be found."} <Link to="/create-itinerary">Pick another trip</Link>
                </p>
              )
            ) : step === 4 ? (
              <ItineraryViewer
                trip={selectedTrip}
                onGenerated={trackGenerated}
                onDeleted={() => navigate("/itineraries")}
              />
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
                      <PenLine size={24} />
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
                      <Sparkles size={24} />
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
              <AIItineraryQuestionnaire
                key={selectedTrip.id}
                trip={selectedTrip}
                initialPreferences={
                  aiPreferences[selectedTrip.id] ?? loadItineraryPreferences(selectedTrip.id) ?? undefined
                }
                onBack={() => setStep(2)}
                onSave={handleGenerate}
                generating={generating}
                generateError={generateError}
              />
            )}


            {/* Last step: save or throw away */}
            {step === 4 && selectedTrip && (
              <div className="itinerary-finish" role="region" aria-label="Finish itinerary">
                <div className="itinerary-finish-text">
                  <strong>Happy with this plan?</strong>
                  <span>
                    {sessionActivityIds.length > 0
                      ? "Save it to keep it in your itineraries, or discard what the AI added."
                      : "Save it to keep it in your itineraries."}
                  </span>
                  {finishError && <span className="itinerary-finish-error" role="alert">{finishError}</span>}
                </div>
                <div className="itinerary-finish-actions">
                  {sessionActivityIds.length > 0 && (
                    <button
                      type="button"
                      className="tb-btn itinerary-delete-btn"
                      onClick={() => setConfirmDiscard(true)}
                      disabled={discarding}
                    >
                      <X size={16} aria-hidden="true" /> Discard
                    </button>
                  )}
                  <button
                    type="button"
                    className="tb-btn tb-btn-primary"
                    onClick={() => navigate(`/itineraries/${selectedTrip.id}`, { state: { saved: true } })}
                    disabled={discarding}
                  >
                    <Check size={16} aria-hidden="true" /> Save itinerary
                  </button>
                </div>
              </div>
            )}

            {/* Bottom navigation */}

            <footer
              className="itinerary-footer"
              style={
                (step === 3 && planningMethod === "ai") || step === 4
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

      {pendingGenerate && (
        <ConfirmDialog
          title="This trip already has a plan"
          onCancel={() => setPendingGenerate(null)}
          actions={[
            { label: "Keep them and add", tone: "plain", onClick: () => runGenerate(pendingGenerate.preferences, false) },
            { label: "Replace them", tone: "primary", onClick: () => runGenerate(pendingGenerate.preferences, true) },
          ]}
        >
          <p>
            {selectedTrip?.name} already has {pendingGenerate.count}{" "}
            {pendingGenerate.count === 1 ? "activity" : "activities"}.
          </p>
          <p>Replace them with the AI plan, or keep them and add the AI suggestions around them?</p>
        </ConfirmDialog>
      )}

      {confirmDiscard && (
        <ConfirmDialog
          title="Discard this AI plan?"
          onCancel={() => setConfirmDiscard(false)}
          busy={discarding}
          actions={[{ label: discarding ? "Discarding..." : "Discard", tone: "danger", onClick: discardItinerary }]}
        >
          <p>
            The {sessionActivityIds.length} {sessionActivityIds.length === 1 ? "activity" : "activities"} the AI
            added are deleted. Anything you had before and chose to replace can't be brought back.
          </p>
        </ConfirmDialog>
      )}
    </div>
  );
}

export default CreateItinerary;