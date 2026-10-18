
import { useState } from "react";
import "../styles/CreateTrip.css";
import Sidebar from "../components/Sidebar";

const budgetCategories = [
  { name: "Flights", icon: "✈", description: "Round-trip tickets for all travelers" },
  { name: "Accommodation", icon: "▣", description: "Hotels, rentals and other stays" },
  { name: "Local transport", icon: "▤", description: "Transfers, public transport and car rentals" },
  { name: "Food", icon: "♜", description: "Meals, drinks and snacks" },
  { name: "Activities", icon: "◇", description: "Tours, attractions and experiences" },
  { name: "Contingency", icon: "⬡", description: "A little extra for unexpected expenses" },
];

function CreateTrip() {
  const [totalBudget, setTotalBudget] = useState("");
  const [currency, setCurrency] = useState("USD");
  const [allocations, setAllocations] = useState({});

  const total = Number(totalBudget) || 0;

  const allocated = Object.values(allocations).reduce(
    (sum, value) => sum + (Number(value) || 0), 0
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
          <div className="trip-profile">
            <div className="trip-avatar">OI</div>
            <span>OhMay Ishar</span>
            <span>⌄</span>
          </div>
        </header>

        {/* Banner */}
        <section className="trip-banner">
          <div>
            <p className="trip-breadcrumb">
              My Trips / Plan a New Trip / Budget
            </p>

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
            <p>New trip · Details not completed</p>
          </div>

          <div className="trip-detail-item">
            <span>⌖ Destination</span>
            <p>Not selected</p>
          </div>

          <div className="trip-detail-item">
            <span>▦ Travel Dates</span>
            <p>Not selected</p>
          </div>

          <div className="trip-detail-item">
            <span>♧ Travelers</span>
            <p>2 travelers</p>
          </div>

          <button className="edit-details">Edit details</button>

        </section>

        {/* Budget area */}
        <div className="trip-budget-layout">

          {/* Left: Set your budget */}
          <section className="trip-budget-form">

            <h2>Set your budget</h2>
            <p className="budget-description">
              Start with a total, then divide it across your trip.
              All amounts are for 2 travelers.
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

            <h3>Allocate by category</h3>
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
              <span>{percentage.toFixed(0)}% allocated</span>
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
              <span>Total allocated</span>
              <strong>{formatter.format(allocated)}</strong>
            </div>

            <div className="remaining-budget">
              <div>
                <span>Remaining to allocate</span>
                <h2>{formatter.format(remaining)}</h2>
              </div>

              <p>
                {remaining < 0
                  ? "Your allocations exceed your total budget."
                  : remaining === 0 && total > 0
                  ? "Your entire budget has been allocated."
                  : "Add a total budget to start allocating your spending."}
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
