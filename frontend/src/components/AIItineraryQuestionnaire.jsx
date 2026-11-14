import { useEffect, useRef, useState } from "react";
import { getProfile } from "../api/api";
import "../styles/AIItineraryQuestionnaire.css";

const INTERESTS = [
  "Food & Drink",
  "Sightseeing",
  "Nature",
  "Museums & Culture",
  "Shopping",
  "Entertainment",
  "Relaxation",
];

const DIETARY_OPTIONS = [
  "No restrictions",
  "Halal",
  "Vegetarian",
  "Vegan",
  "Pescatarian",
  "Kosher",
  "Gluten-free",
  "Dairy-free",
  "Local cuisine",
];

const TRAVEL_STYLES = [
  { value: "budget", label: "Budget-friendly" },
  { value: "balanced", label: "Balanced" },
  { value: "luxury", label: "Luxury" },
];

const PACES = [
  { value: "relaxed", label: "Relaxed" },
  { value: "balanced", label: "Balanced" },
  { value: "packed", label: "Packed" },
];

const PAGE_TITLES = [
  "Budget & Travel Style",
  "Minimum Place Rating",
  "Your Interests",
  "Activity Preferences",
];

const DEFAULT_PREFERENCES = {
  dailyBudget: "",
  travelStyle: "balanced",
  minimumRating: "4.0",
  interests: [],
  pace: "balanced",
  diningPreferences: ["No restrictions"],
  specialRequests: "",
};

export default function AIItineraryQuestionnaire({
  trip,
  initialPreferences,
  onSave,
  onBack,
}) {
  const [page, setPage] = useState(0);
  const headingRef = useRef(null);
  const [currency, setCurrency] = useState(null);
  const [currencyError, setCurrencyError] = useState("");

  // Match the currency saved under Settings > Preferences.
  useEffect(() => {
    let cancelled = false;

    getProfile()
      .then((profile) => {
        if (!cancelled) setCurrency(profile.currency || "USD");
      })
      .catch(() => {
        if (!cancelled) {
          setCurrencyError("Couldn't load your currency preference. Refresh and try again.");
        }
      });

    return () => { cancelled = true; };
  }, []);
  const [preferences, setPreferences] = useState(() => ({
    ...DEFAULT_PREFERENCES,
    ...initialPreferences,
    interests: Array.isArray(initialPreferences?.interests)
      ? initialPreferences.interests
      : [],
    diningPreferences: Array.isArray(initialPreferences?.diningPreferences)
      ? initialPreferences.diningPreferences
      : typeof initialPreferences?.diningPreferences === "string"
        ? initialPreferences.diningPreferences.split(",").map((item) => item.trim())
        : ["No restrictions"],
  }));
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);

  function updateField(field, value) {
    setPreferences((previous) => ({ ...previous, [field]: value }));
    setError("");
    setSaved(false);
  }

  function toggleInterest(interest) {
    updateField(
      "interests",
      preferences.interests.includes(interest)
        ? preferences.interests.filter((item) => item !== interest)
        : [...preferences.interests, interest]
    );
  }

  function toggleDietary(choice) {
    if (choice === "No restrictions") {
      updateField("diningPreferences", ["No restrictions"]);
      return;
    }

    const current = preferences.diningPreferences.filter(
      (item) => item !== "No restrictions"
    );
    const updated = current.includes(choice)
      ? current.filter((item) => item !== choice)
      : [...current, choice];

    updateField(
      "diningPreferences",
      updated.length ? updated : ["No restrictions"]
    );
  }

  function goToPage(nextPage) {
    setPage(nextPage);
    setError("");
    setSaved(false);
    headingRef.current?.scrollIntoView({
      behavior: "smooth",
      block: "start",
    });
  }

  function handleSubmit(event) {
    event.preventDefault();

    if (page === 0) {
      if (!currency) {
        setError("Your saved currency is unavailable. Refresh and try again.");
        return;
      }
      if (
        preferences.dailyBudget === "" ||
        !Number.isFinite(Number(preferences.dailyBudget)) ||
        Number(preferences.dailyBudget) <= 0
      ) {
        setError("Enter a valid daily budget.");
        return;
      }
      goToPage(1);
      return;
    }

    if (page === 1) {
      goToPage(2);
      return;
    }

    if (page === 2) {
      if (preferences.interests.length === 0) {
        setError("Select at least one interest.");
        return;
      }
      goToPage(3);
      return;
    }

    onSave({ ...preferences, currency });
    setSaved(true);
    setError("");
  }

  const rating = preferences.minimumRating === "any"
    ? 0
    : Number(preferences.minimumRating);

  return (
    <section className="ai-questionnaire" ref={headingRef}>
      <div className="itinerary-heading">
        <p className="ai-questionnaire-kicker">AI TRIP PLANNING</p>
        <h1>Personalize Your Itinerary</h1>
        <p>Planning for <strong>{trip.name}</strong></p>
      </div>

      <div className="ai-questionnaire-progress">
        <div className="ai-questionnaire-progress-label">
          <strong>{PAGE_TITLES[page]}</strong>
          <span>Question {page + 1} of 4</span>
        </div>
        <div className="ai-questionnaire-progress-track">
          {[0, 1, 2, 3].map((index) => (
            <span
              key={index}
              className={index <= page ? "is-active" : ""}
            />
          ))}
        </div>
      </div>

      <form className="ai-questionnaire-form" onSubmit={handleSubmit}>
        {page === 0 && (
          <div className="ai-questionnaire-section">
            <h2>Budget & Travel Style</h2>
            <p>Choose a daily budget and your preferred travel style.</p>

            <label>
              Daily budget for food and activities ({currency || "Loading currency..."}) *
              <input
                type="number"
                min="1"
                step="1"
                required
                placeholder="e.g., 150"
                value={preferences.dailyBudget}
                onChange={(event) => updateField("dailyBudget", event.target.value)}
              />
            </label>

            <div className="ai-questionnaire-slider">
              <label htmlFor="ai-travel-style">Preferred travel style</label>
              <p className="ai-questionnaire-current-choice">
                {TRAVEL_STYLES.find(
                  (style) => style.value === preferences.travelStyle
                )?.label}
              </p>
              <input
                id="ai-travel-style"
                type="range"
                min="0"
                max="2"
                step="1"
                value={Math.max(
                  0,
                  TRAVEL_STYLES.findIndex(
                    (style) => style.value === preferences.travelStyle
                  )
                )}
                aria-valuetext={TRAVEL_STYLES.find(
                  (style) => style.value === preferences.travelStyle
                )?.label}
                onChange={(event) =>
                  updateField(
                    "travelStyle",
                    TRAVEL_STYLES[Number(event.target.value)].value
                  )
                }
              />
              <div className="ai-questionnaire-slider-labels">
                {TRAVEL_STYLES.map((style) => (
                  <span key={style.value}>{style.label}</span>
                ))}
              </div>
            </div>
          </div>
        )}

        {page === 1 && (
          <div className="ai-questionnaire-section">
            <h2>Minimum Place Rating</h2>
            <p>Choose the minimum rating you prefer for recommended places.</p>

            <div className="ai-questionnaire-rating">
              <p className="ai-questionnaire-field-label">Minimum place rating</p>
              <div
                className="ai-questionnaire-stars"
                role="group"
                aria-label="Choose a minimum star rating"
              >
                {[0, 1, 2, 3, 4].map((index) => {
                  const fill = Math.min(1, Math.max(0, rating - index));
                  return (
                    <span key={index} className="ai-questionnaire-star">
                      <span className="ai-star-empty" aria-hidden="true">★</span>
                      <span
                        className="ai-star-filled"
                        aria-hidden="true"
                        style={{ width: `${fill * 100}%` }}
                      >
                        ★
                      </span>
                      <button
                        type="button"
                        className="ai-star-hit ai-star-half"
                        aria-label={`${index + 0.5} stars minimum`}
                        aria-pressed={rating === index + 0.5}
                        onClick={() => updateField("minimumRating", String(index + 0.5))}
                      />
                      <button
                        type="button"
                        className="ai-star-hit ai-star-full"
                        aria-label={`${index + 1} stars minimum`}
                        aria-pressed={rating === index + 1}
                        onClick={() => updateField("minimumRating", String(index + 1))}
                      />
                    </span>
                  );
                })}
              </div>
              <p className="ai-questionnaire-rating-value">
                {preferences.minimumRating === "any"
                  ? "No minimum rating"
                  : `${rating.toFixed(1)} out of 5 stars`}
              </p>
              <button
                type="button"
                className="ai-questionnaire-clear"
                onClick={() => updateField("minimumRating", "any")}
              >
                No rating preference
              </button>
              <p className="ai-questionnaire-help">
                Click the left half of a star for a half-star rating,
                or the right half for a whole star.
              </p>
            </div>
          </div>
        )}

        {page === 2 && (
          <div className="ai-questionnaire-section">
            <h2>Your Interests</h2>
            <p>Select everything you'd enjoy doing on your trip.</p>
            <div className="ai-questionnaire-interests">
              {INTERESTS.map((interest) => (
                <button
                  key={interest}
                  type="button"
                  className={
                    "ai-questionnaire-interest " +
                    (preferences.interests.includes(interest) ? "is-selected" : "")
                  }
                  aria-pressed={preferences.interests.includes(interest)}
                  onClick={() => toggleInterest(interest)}
                >
                  {interest}
                </button>
              ))}
            </div>
          </div>
        )}

        {page === 3 && (
          <div className="ai-questionnaire-section">
            <h2>Activity Preferences</h2>
            <p>Customize your daily schedule and dining needs.</p>

            <div className="ai-questionnaire-slider">
              <label htmlFor="ai-pace">How busy should your days be?</label>
              <p className="ai-questionnaire-current-choice">
                {PACES.find((pace) => pace.value === preferences.pace)?.label}
              </p>
              <input
                id="ai-pace"
                type="range"
                min="0"
                max="2"
                step="1"
                value={Math.max(
                  0,
                  PACES.findIndex((pace) => pace.value === preferences.pace)
                )}
                aria-valuetext={PACES.find(
                  (pace) => pace.value === preferences.pace
                )?.label}
                onChange={(event) =>
                  updateField("pace", PACES[Number(event.target.value)].value)
                }
              />
              <div className="ai-questionnaire-slider-labels">
                {PACES.map((pace) => (
                  <span key={pace.value}>{pace.label}</span>
                ))}
              </div>
            </div>

            <div className="ai-questionnaire-dietary">
              <p className="ai-questionnaire-field-label">Food & dietary preferences</p>
              <p>Select all that apply.</p>
              <div className="ai-questionnaire-interests">
                {DIETARY_OPTIONS.map((choice) => (
                  <button
                    key={choice}
                    type="button"
                    className={
                      "ai-questionnaire-interest " +
                      (preferences.diningPreferences.includes(choice)
                        ? "is-selected"
                        : "")
                    }
                    aria-pressed={preferences.diningPreferences.includes(choice)}
                    onClick={() => toggleDietary(choice)}
                  >
                    {choice}
                  </button>
                ))}
              </div>
            </div>

            <label>
              Special requests (optional)
              <textarea
                rows={4}
                maxLength={1000}
                placeholder="Must-see places, accessibility needs, activities to avoid..."
                value={preferences.specialRequests}
                onChange={(event) => updateField("specialRequests", event.target.value)}
              />
            </label>
          </div>
        )}

        {currencyError && <p className="ai-questionnaire-error" role="alert">{currencyError}</p>}
        {error && <p className="ai-questionnaire-error" role="alert">{error}</p>}
        {saved && (
          <p className="ai-questionnaire-saved" role="status">
            Preferences saved for this session.
          </p>
        )}

        <div className="ai-questionnaire-actions">
          <button
            type="button"
            className="ai-questionnaire-back"
            onClick={() => (page === 0 ? onBack?.() : goToPage(page - 1))}
          >
            {page === 0 ? "← Planning Methods" : "← Back"}
          </button>
          <button type="submit" className="ai-questionnaire-submit">
            {page === 3 ? "Save Preferences" : "Continue →"}
          </button>
        </div>
      </form>
    </section>
  );
}
