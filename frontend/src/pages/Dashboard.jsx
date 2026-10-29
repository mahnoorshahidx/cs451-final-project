
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import "../styles/Dashboard.css";
import "../styles/Trips.css";
import Sidebar from "../components/Sidebar";
import TripCard from "../components/TripCard";
import NotificationBell from "../components/NotificationBell";
import BankConnection from "./BankConnection";
import { listTrips, getBudgetOverview } from "../api/api";
import { getCurrentUser } from "../api/client";

// Returns a greeting based on the time
function greeting() {
  const hour = new Date().getHours();

  if (hour < 12) return "Good morning";
  if (hour < 18) return "Good afternoon";
  return "Good evening";
}

function Dashboard() {
  const user = getCurrentUser();
  const firstName = user?.name?.split(" ")[0] || "there";

  const [trips, setTrips] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [savingsOverview, setSavingsOverview] = useState([]);
  const [savingsLoading, setSavingsLoading] = useState(true);
  const [savingsError, setSavingsError] = useState("");

  // Load the user's trips
  useEffect(() => {
    listTrips()
      .then(setTrips)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
  getBudgetOverview()
    .then(setSavingsOverview)
    .catch((err) => setSavingsError(err.message))
    .finally(() => setSavingsLoading(false));
}, []);

  const now = new Date();

  const today = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, "0"),
    String(now.getDate()).padStart(2, "0"),
  ].join("-");

  // Show up to 3 upcoming trips
  const upcoming = trips
    .filter((trip) => trip.endDate >= today)
    .slice(0, 3);

      // Find the next trip that hasn't started yet
  const nextTrip = [...trips]
    .filter((trip) => trip.startDate >= today)
    .sort((a, b) => a.startDate.localeCompare(b.startDate))[0];

  // Calculate days until departure
  const daysUntilTrip = nextTrip
    ? Math.round(
        (new Date(`${nextTrip.startDate}T00:00:00`) -
          new Date(`${today}T00:00:00`)) /
          (1000 * 60 * 60 * 24)
      )
  : null;

  // Savings for upcoming trips only
const upcomingSavings = savingsOverview.filter(
  (trip) => trip.endDate >= today
);

// Match the Budget & Savings page's currency handling
const budgetedTrips = upcomingSavings.filter(
  (trip) => trip.budgetTotal != null
);

const currencies = [...new Set(budgetedTrips.map((trip) => trip.currency))];

const mainCurrency =
  currencies.sort(
    (a, b) =>
      budgetedTrips.filter((trip) => trip.currency === b).length -
      budgetedTrips.filter((trip) => trip.currency === a).length
  )[0] || "USD";

  const savingsTrips = budgetedTrips.filter(
    (trip) => trip.currency === mainCurrency
  );

  const totalSaved = savingsTrips.reduce(
    (sum, trip) => sum + Number(trip.savedAmount || 0),
    0
  );

  const totalGoal = savingsTrips.reduce(
    (sum, trip) => sum + Number(trip.savingsTarget || 0),
    0
  );

  const savingsPercent =
    totalGoal > 0
      ? Math.min(100, Math.floor((totalSaved / totalGoal) * 100))
      : 0;

  const formattedSaved = new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: mainCurrency,
    maximumFractionDigits: 0,
  }).format(totalSaved);

  return (
    <div className="dashboard-page">

      {/* Sidebar */}
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      {/* Main Content */}
      <main className="dashboard-main">

        {/* Welcome Banner */}
        <section className="dashboard-banner">
          <div className="dashboard-welcome">
            <h1>{greeting()}, {firstName}!</h1>
            <p>
              Where to next? Let's turn your travel dreams
              into a plan.
            </p>
          </div>

          {/* Notification bell */}
          <NotificationBell />
        </section>

        <div className="dashboard-content">

          <div className="dashboard-left">

          {/* Next Trip Countdown */}
        {!loading && !error && nextTrip && (
          <section className="dashboard-countdown">
            <div className="countdown-details">
              <span className="countdown-label">
                ✈ YOUR NEXT ADVENTURE
              </span>

              <h2>{nextTrip.destination}</h2>

              <p>
                Departure:{" "}
                {new Date(
                  `${nextTrip.startDate}T00:00:00`
                ).toLocaleDateString("en-US", {
                  month: "long",
                  day: "numeric",
                  year: "numeric",
                })}
              </p>
            </div>

            <div className="countdown-number">
              <strong>{daysUntilTrip}</strong>
              <span>
                {daysUntilTrip === 0
                  ? "TODAY!"
                  : daysUntilTrip === 1
                  ? "DAY TO GO"
                  : "DAYS TO GO"}
              </span>
            </div>
          </section>
        )}

        {/* Trip Savings Overview */}
          <section className="dashboard-savings">
            <div className="dashboard-savings-header">
              <span> Trip Savings</span>
              <Link to="/budget">View savings →</Link>
            </div>

            {savingsLoading ? (
              <p>Loading savings...</p>
            ) : savingsError ? (
              <p>Unable to load savings.</p>
            ) : (
              <>
                <div className="dashboard-savings-amount">
                  <strong>{formattedSaved}</strong>
                  <span>{savingsPercent}% saved</span>
                </div>

                <div className="dashboard-savings-progress">
                  <div style={{ width: `${savingsPercent}%` }} />
                </div>

                <p>
                  {totalGoal > 0
                    ? `${savingsPercent}% of your savings goals reached`
                    : "No savings goals set yet"}
                </p>
              </>
            )}
          </section>


        </div>



        {/* Bank Connection Card */}
          <aside className="dashboard-card">
            <BankConnection />
          </aside>




        </div> {/* End dashboard-content grid */}

        {/* Upcoming Trips */}
        <section className="dashboard-trips">

            <div className="trips-section-header">
            <h2>Upcoming Trips</h2>

            <div className="trips-header-actions">
              <Link to="/create-trip" className="dashboard-create-trip-btn">
                <span aria-hidden="true">+</span>
                Create Trip
              </Link>

              {trips.length > 0 && (
                <Link to="/trips" className="dashboard-view-all">
                  View all ({trips.length})
                </Link>
              )}
            </div>
          </div>

            {loading && (
              <p className="trips-message">
                Loading your trips...
              </p>
            )}

            {error && (
              <p className="error-text">{error}</p>
            )}

            {!loading && !error && upcoming.length === 0 && (
              <p className="trips-message">
                No upcoming trips yet.{" "}
                <Link to="/create-trip">Plan one</Link>.
              </p>
            )}

            {!loading && !error && (
              <div className="trip-list">
                {upcoming.map((trip) => (
                  <TripCard key={trip.id} trip={trip} />
                ))}
              </div>
            )}

          </section>

      </main>
    </div>
  );
}

export default Dashboard;
