
import { useState } from "react";
import "../styles/CreateTrip.css";
import Sidebar from "../components/Sidebar";

const budgetCategories = [
  { name: "Flights", icon: "✈", description: "Round-trip tickets for all travelers" },
  { name: "Accommodation", icon: "▣", description: "Hotels, rentals and other stays" },
  { name: "Local transport", icon: "▤", description: "Transfers, public transport and car rentals" },
  { name: "Food", icon: "♜", description: "Meals, drinks and snacks" }
];

function CreateTrip() {
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

  function updateAllocation(category, value) {
    setAllocations((previous) => ({
      ...previous,
      [category]: value,
    }));
  }

  function handleSave() {
    if (total <= 0 || allocated > total) {
      alert("Please enter a valid budget and check your allocations.");
      return;
    }

    // Temporary save until backend integration
    localStorage.setItem(
      "tripBudget",
      JSON.stringify({ totalBudget, currency, allocations })
    );

    alert("Budget saved successfully!");
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
            <h1>Create your trip budget</h1>

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
                onChange={(e) =>
                  setTripDetails({
                    ...tripDetails,
                    name: e.target.value,
                  })
                }
              />
            </div>

            <div className="trip-field">
              <label htmlFor="destination">Destination</label>
              <input
                id="destination"
                type="text"
                placeholder="e.g. Marrakech, Morocco"
                value={tripDetails.destination}
                onChange={(e) =>
                  setTripDetails({
                    ...tripDetails,
                    destination: e.target.value,
                  })
                }
              />
            </div>

            <div className="trip-field">
              <label htmlFor="startDate">Start Date</label>
              <input
                id="startDate"
                type="date"
                value={tripDetails.startDate}
                onChange={(e) =>
                  setTripDetails({
                    ...tripDetails,
                    startDate: e.target.value,
                  })
                }
              />
            </div>

            <div className="trip-field">
              <label htmlFor="endDate">End Date</label>
              <input
                id="endDate"
                type="date"
                min={tripDetails.startDate}
                value={tripDetails.endDate}
                onChange={(e) =>
                  setTripDetails({
                    ...tripDetails,
                    endDate: e.target.value,
                  })
                }
              />
            </div>

            <div className="trip-field">
              <label htmlFor="travelers">Number of Travelers</label>

              <input
                id="travelers"
                type="number"
                min="1"
                placeholder="E.g 2"
                value={tripDetails.numberOfTravelers}
                onChange={(e) =>
                  setTripDetails({
                    ...tripDetails,
                    numberOfTravelers:
                      e.target.value === "" ? "" : Number(e.target.value),
                  })
                }
              />
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
              {Number(tripDetails.numberOfTravelers) >= 1 &&
                ` All amounts are for ${tripDetails.numberOfTravelers} ${
                  Number(tripDetails.numberOfTravelers) === 1
                    ? "traveler"
                    : "travelers"
                }.`}
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

            <button className="save-budget-btn" onClick={handleSave}>
              Save Budget →
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
