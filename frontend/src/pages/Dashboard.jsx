
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import "../styles/Dashboard.css";
import "../styles/Trips.css";
import Sidebar from "../components/Sidebar";
import TripCard from "../components/TripCard";
import NotificationBell from "../components/NotificationBell";
import BankConnection from "./BankConnection";
import { listTrips } from "../api/api";
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

  // Load the user's trips
  useEffect(() => {
    listTrips()
      .then(setTrips)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
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

          {/* Create Trip Card */}
          <section className="dashboard-plan">

            <div className="plan-icon">✈</div>

            <div className="plan-info">
              <h2>Create a New Trip</h2>
              <p>
                Plan your next adventure and set your travel budget.
              </p>
            </div>

            <Link to="/create-trip" className="plan-link">
              Create Trip <span aria-hidden="true">→</span>
            </Link>

          </section>

          {/* Bank Connection Card */}
          <aside className="dashboard-card">
            <BankConnection />
          </aside>

          {/* Upcoming Trips */}
          <section className="dashboard-trips">

            <div className="trips-section-header">
              <h2>Upcoming Trips</h2>

              {trips.length > 0 && (
                <Link to="/trips">
                  View all ({trips.length})
                </Link>
              )}
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

        </div>
      </main>
    </div>
  );
}

export default Dashboard;
