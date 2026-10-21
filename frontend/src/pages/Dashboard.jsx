import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import "../styles/Dashboard.css";
import "../styles/Trips.css";
import Sidebar from "../components/Sidebar";
import TripCard from "../components/TripCard";
import BankConnection from "./BankConnection";
import { listTrips } from "../api/api";
import { getCurrentUser } from "../api/client";

function greeting() {
  const hour = new Date().getHours();
  if (hour < 12) return "Good morning";
  if (hour < 18) return "Good afternoon";
  return "Good evening";
}

function Dashboard() {
  // Real name from the login session, instead of "User" (SCRUM-30)
  const user = getCurrentUser();
  const firstName = user?.name?.split(" ")[0] || "there";

  const [trips, setTrips] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // Loads the user's trips from the backend for the "Upcoming trips" section
  useEffect(() => {
    listTrips()
      .then(setTrips)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  // Backend already sorts soonest first; show upcoming ones on the dashboard
  const now = new Date();
  const today = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, "0"),
    String(now.getDate()).padStart(2, "0"),
  ].join("-");
  const upcoming = trips.filter((t) => t.endDate >= today).slice(0, 3);

  return (
    <div className="dashboard-page">

      {/* Left Sidebar */}
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      {/* Main Content */}
      <main className="dashboard-main">

        {/* Top Profile Header */}
        <header className="dashboard-header">
        </header>

        {/* Welcome Banner */}
        <section className="dashboard-banner">
          <div>
            <h1>{greeting()}, {firstName}!</h1>
            <p>
              Where to next? Let's turn your travel dreams
              into a plan.
            </p>
          </div>
        </section>

        {/* Dashboard Content */}
        <div className="dashboard-content">

          {/* Left Column */}
          <div className="dashboard-left">

            {/* Plan New Trip */}
            <section className="dashboard-plan">
              <Link to="/create-trip">
                <button>Create Trip</button>
              </Link>
            </section>

            {/* My Trips */}
            <section className="dashboard-trips">
              <div className="trips-section-header">
                <h2>Upcoming trips</h2>
                {trips.length > 0 && <Link to="/trips">View all ({trips.length})</Link>}
              </div>

              {loading && <p className="trips-message">Loading your trips...</p>}
              {error && <p className="error-text">{error}</p>}

              {!loading && !error && upcoming.length === 0 && (
                <p className="trips-message">
                  No upcoming trips yet. <Link to="/create-trip">Plan one</Link>.
                </p>
              )}

              <div className="trip-list">
                {upcoming.map((trip) => (
                  <TripCard key={trip.id} trip={trip} />
                ))}
              </div>
            </section>

          </div>

          {/* Right Column */}
          <aside className="dashboard-card">
            <BankConnection />
          </aside>

        </div>

      </main>

    </div>
  );
}

export default Dashboard;