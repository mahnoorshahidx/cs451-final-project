import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import "../styles/CreateTrip.css";
import Sidebar from "../components/Sidebar";
import { createTrip, getBudget, getTrip, saveBudget, updateTrip } from "../api/api";
import { getCurrentUser, initials } from "../api/client";

const budgetCategories = [
  { name: "Flights", icon: "✈", description: "Round-trip tickets for all travelers" },
  { name: "Accommodation", icon: "▣", description: "Hotels, rentals and other stays" },
  { name: "Local transport", icon: "▤", description: "Transfers, public transport and car rentals" },
  { name: "Food", icon: "♜", description: "Meals, drinks and snacks" }
];

// Now used for both "create" (/create-trip) and "edit" (/trips/:tripId/edit)
function CreateTrip() {
  const navigate = useNavigate();
  const { tripId } = useParams();
  const isEditing = Boolean(tripId);
  const user = getCurrentUser();

  const [tripDetails, setTripDetails] = useState({
    name: "",
    destination: "",
    startDate: "",
    endDate: "",
    numberOfTravelers: "",
  });
  const [totalBudget, setTotalBudget] = useState("");
  const [currency, setCurrency] = useState("USD");
  const [allocations, setAllocations] = useState({});

  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(isEditing);

  // Edit mode: load the trip and its budget
  useEffect(() => {
    if (!isEditing) return;
    Promise.all([getTrip(tripId), getBudget(tripId)])
      .then(([trip, budget]) => {
        setTripDetails({
          name: trip.name,
          destination: trip.destination,
          startDate: trip.startDate,
          endDate: trip.endDate,
          numberOfTravelers: String(trip.numberOfTravelers),
        });
        if (budget) {
          setTotalBudget(String(budget.totalBudget));
          setCurrency(budget.currency);
          setAllocations(
            Object.fromEntries(
              Object.entries(budget.allocations).map(([k, v]) => [k, String(v)])
            )
          );
        }
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [isEditing, tripId]);

  const travelers = Number(tripDetails.numberOfTravelers) || 0;
  const total = Number(totalBudget) || 0;


  const allocated = budgetCategories.reduce(
    (sum, category) =>
      sum + (Number(allocations[category.name]) || 0),
    0
  );


  const remaining = total - allocated;
  const percentage = total > 0 ? (allocated / total) * 100 : 0;

  const formatter = new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
  });

  function updateField(field, value) {
    setTripDetails((prev) => ({ ...prev, [field]: value }));
  }

  function updateAllocation(category, value) {
    setAllocations((previous) => ({
      ...previous,
      [category]: value,
    }));
  }

  function validate() {
    const errs = {};
    if (!tripDetails.name.trim()) errs.name = "Trip name is required";
    if (!tripDetails.destination.trim()) errs.destination = "Destination is required";
    if (!tripDetails.startDate) errs.startDate = "Start date is required";
    if (!tripDetails.endDate) errs.endDate = "End date is required";
    else if (tripDetails.startDate && tripDetails.endDate < tripDetails.startDate)
      errs.endDate = "End date cannot be before start date";
    if (!Number.isInteger(travelers) || travelers < 1)
      errs.numberOfTravelers = "At least 1 traveler";
    if (total <= 0) errs.totalBudget = "Enter a total budget";
    else if (allocated > total) errs.totalBudget = "Your allocations exceed your total budget";
    return errs;
  }

  // Saves to the backend instead of localStorage (SCRUM-31, SCRUM-35):
  // first the trip, then its budget, then goes to My Trips
  async function handleSave() {
    setError("");
    const errs = validate();
    setFieldErrors(errs);
    if (Object.keys(errs).length > 0) return;

    setSaving(true);
    try {
      const tripBody = {
        name: tripDetails.name.trim(),
        destination: tripDetails.destination.trim(),
        startDate: tripDetails.startDate,
        endDate: tripDetails.endDate,
        numberOfTravelers: travelers,
      };

      // 1. Save the trip
      const trip = isEditing
        ? await updateTrip(tripId, tripBody)
        : await createTrip(tripBody);

      // 2. Save its budget (only categories with an amount)
      const cleanAllocations = Object.fromEntries(
        Object.entries(allocations)
          .filter(([, v]) => v !== "" && Number(v) > 0)
          .map(([k, v]) => [k, Number(v)])
      );
      await saveBudget(trip.id, {
        totalBudget: total,
        currency,
        allocations: cleanAllocations,
      });

      navigate("/trips");
    } catch (err) {
      setFieldErrors(err.fieldErrors || {});
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  const fieldError = (name) =>
    fieldErrors[name] ? <p className="error-text">{fieldErrors[name]}</p> : null;

  if (loading) {
    return <p className="trips-message" style={{ padding: 32 }}>Loading trip...</p>;
  }

  return (
    <div className="create-trip-page">

      {/* Left sidebar */}
      <aside className="trip-sidebar">
        <Sidebar />
      </aside>

      {/* Right main content */}
      <main className="trip-main">

        {/* Top navigation */}
        <header className="trip-header">
        </header>

        {/* Banner */}
        <section className="trip-banner">
          <div>
            <p className="trip-breadcrumb">
              My Trips / {isEditing ? "Edit Trip" : "Plan a New Trip"} / Budget
            </p>

            <h1>{isEditing ? "Edit your trip budget" : "Create your trip budget"}</h1>

            <p>
              Plan your spending now, so you can enjoy
              the journey later.
            </p>
          </div>
        </section>

        {/* Trip details */}
        <section className="trip-details">

          <div className="trip-details-title">
            <h2>Your trip details</h2>
            <p>Tell us a little about your upcoming trip.</p>
          </div>

          <div className="trip-details-form">

            <div className="trip-field">
              <label htmlFor="tripName">Trip Name</label>
              <input
                id="tripName"
                type="text"
                placeholder="e.g. Morocco Vacation"
                value={tripDetails.name}
                onChange={(e) => updateField("name", e.target.value)}
              />
              {fieldError("name")}
            </div>

            <div className="trip-field">
              <label htmlFor="destination">Destination</label>
              <input
                id="destination"
                type="text"
                placeholder="e.g. Marrakech, Morocco"
                value={tripDetails.destination}
                onChange={(e) => updateField("destination", e.target.value)}
              />
              {fieldError("destination")}
            </div>

            <div className="trip-field">
              <label htmlFor="startDate">Start Date</label>
              <input
                id="startDate"
                type="date"
                value={tripDetails.startDate}
                onChange={(e) => updateField("startDate", e.target.value)}
              />
              {fieldError("startDate")}
            </div>

            <div className="trip-field">
              <label htmlFor="endDate">End Date</label>
              <input
                id="endDate"
                type="date"
                min={tripDetails.startDate}
                value={tripDetails.endDate}
                onChange={(e) => updateField("endDate", e.target.value)}
              />
              {fieldError("endDate")}
            </div>

            <div className="trip-field">
              <label htmlFor="travelers">Number of Travelers</label>

              <input
                id="travelers"
                type="number"
                min="1"
                step="1"
                value={tripDetails.numberOfTravelers}
                onChange={(e) => updateField("numberOfTravelers", e.target.value)}
              />
              {fieldError("numberOfTravelers")}
            </div>

          </div>

        </section>

        {/* Budget area */}
        <div className="trip-budget-layout">

          {/* Left: Set your budget */}
          <section className="trip-budget-form">

            <h2>Set your budget</h2>

            <p className="budget-description">
              Start with a total, then divide it across your trip.
              {/* Was hard-coded to "2 travelers"; now follows the field (SCRUM-33) */}
              {travelers > 0 &&
                ` All amounts are for ${travelers} traveler${travelers === 1 ? "" : "s"}.`}
            </p>


            <div className="budget-input-row">

              <div className="budget-field">
                <label htmlFor="totalBudget">Total trip budget</label>
                <input
                  id="totalBudget"
                  type="number"
                  min="0"
                  placeholder="0.00"
                  value={totalBudget}
                  onChange={(e) => setTotalBudget(e.target.value)}
                />
                {fieldError("totalBudget")}
              </div>

              <div className="currency-field">
                <label htmlFor="currency">Currency</label>
                <select
                  id="currency"
                  value={currency}
                  onChange={(e) => setCurrency(e.target.value)}
                >
                  <option value="USD">USD — US Dollar</option>
                  <option value="EUR">EUR — Euro</option>
                  <option value="GBP">GBP — British Pound</option>
                  <option value="MAD">MAD — Moroccan Dirham</option>
                </select>
              </div>

            </div>

            <div className="budget-divider"></div>

            <h3>Plan your spending</h3>
            <p className="budget-description">
              Enter your planned spending. You can adjust these amounts later.
            </p>

            <div className="budget-category-list">
              {budgetCategories.map((category) => (
                <div className="budget-category" key={category.name}>

                  <div className="category-info">
                    <div className="category-icon">{category.icon}</div>

                    <div>
                      <h4>{category.name}</h4>
                      <p>{category.description}</p>
                    </div>
                  </div>

                  <input
                    type="number"
                    min="0"
                    placeholder="0.00"
                    aria-label={`${category.name} budget`}
                    value={allocations[category.name] ?? ""}
                    onChange={(e) =>
                      updateAllocation(category.name, e.target.value)
                    }
                  />

                </div>
              ))}
            </div>

          </section>

          {/* Right: Budget breakdown */}
          <section className="trip-budget-summary">

            <h2>Budget breakdown</h2>

            <p className="summary-label">Total trip budget</p>

            <div className="summary-total">
              <h1>{formatter.format(total)}</h1>
              <span>{currency}</span>
            </div>

            {travelers > 1 && total > 0 && (
              <p className="summary-label">
                {formatter.format(total / travelers)} per traveler
              </p>
            )}

            <div className="budget-progress">
              <div
                className="budget-progress-fill"
                style={{ width: `${Math.min(percentage, 100)}%` }}
              ></div>
            </div>

            <div className="progress-labels">
              <span>{percentage.toFixed(0)}% planned</span>
              <span>
                {formatter.format(allocated)} of {formatter.format(total)}
              </span>
            </div>

            <div className="summary-categories">
              {budgetCategories.map((category) => (
                <div className="summary-category" key={category.name}>
                  <span>• &nbsp;{category.name}</span>
                  <strong>
                    {formatter.format(Number(allocations[category.name]) || 0)}
                  </strong>
                </div>
              ))}
            </div>

            <div className="summary-allocated">
              <span>Total planned spending</span>
              <strong>{formatter.format(allocated)}</strong>
            </div>

            <div className="remaining-budget">
              <div>
                <span>Remaining budget</span>
                <h2>{formatter.format(remaining)}</h2>
              </div>

            <p>
              {remaining < 0
                ? "Your planned spending exceeds your total budget."
                : remaining === 0 && total > 0
                ? "Your entire budget has been planned."
                : total === 0
                ? "Enter your total budget to get started."
                : "You still have money available in your budget."}
            </p>
            </div>

            {error && <p className="error-text">{error}</p>}

            <button className="save-budget-btn" onClick={handleSave} disabled={saving}>
              {saving ? "Saving..." : isEditing ? "Save Changes →" : "Save Trip & Budget →"}
            </button>

            <p className="save-note">
              Your budget can be updated anytime.
            </p>

          </section>

        </div>

      </main>
    </div>
  );
}

export default CreateTrip;
