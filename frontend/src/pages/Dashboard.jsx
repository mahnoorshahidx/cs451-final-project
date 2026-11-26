import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Plus } from "lucide-react";
import "../styles/Dashboard.css";
import "../styles/Home.css";
import Sidebar from "../components/Sidebar";
import DashboardTripCard from "../components/DashboardTripCard";
import NotificationBell from "../components/NotificationBell";
import BankConnection from "./BankConnection";
import { listTrips, getBudgetOverview } from "../api/api";
import { getCurrentUser } from "../api/client";
import useDestinationPhoto from "../lib/useDestinationPhoto";

// Returns a greeting based on the time
function greeting() {
  const hour = new Date().getHours();

  if (hour < 12) return "Good morning";
  if (hour < 18) return "Good afternoon";
  return "Good evening";
}

function formatMoney(amount, currency = "USD") {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(Number(amount || 0));
}

function longDate(iso) {
  return new Date(`${iso}T00:00:00`).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
  });
}

// Big photo card for the next trip, with countdown and savings progress
function NextTripCard({ trip, days, overview }) {
  const [photo, clearPhoto] = useDestinationPhoto(trip.destination);
  const [city, ...rest] = (trip.destination || trip.name).split(",");
  const region = rest.join(",").trim();

  const currency = overview?.currency || "USD";
  const target = Number(overview?.savingsTarget || 0);
  const saved = Number(overview?.savedAmount || 0);
  const percent = target > 0 ? Math.min(100, Math.floor((saved / target) * 100)) : 0;

  let savingsLine;
  let action;

  if (target > 0) {
    savingsLine = `${formatMoney(saved, currency)} of ${formatMoney(target, currency)} saved`;
    action = { to: `/budget/${trip.id}`, label: "View savings" };
  } else if (overview?.budgetTotal != null) {
    savingsLine = `No savings goal yet · ${formatMoney(overview.budgetTotal, currency)} budget`;
    action = { to: `/budget/${trip.id}`, label: "Set a goal" };
  } else {
    savingsLine = "No budget yet";
    action = { to: `/trips/${trip.id}/edit?tab=details`, label: "Add a budget" };
  }

  return (
    <section className="home-next" aria-label={`Next trip: ${trip.name}`}>
      {photo?.imageUrl && (
        <img className="home-next-photo" src={photo.imageUrl} alt="" onError={clearPhoto} />
      )}
      <div className="home-next-overlay" aria-hidden="true" />

      <div className="home-next-title">
        <span className="tb-kicker">Your next adventure</span>
        <span className="tb-wide home-next-city">{city.trim()}</span>
        <span className="tb-serif">
          {region ? `${region} · ` : ""}
          {longDate(trip.startDate)} – {longDate(trip.endDate)}
        </span>
      </div>

      <div className="home-next-strip">
        <div className="home-next-countdown">
          <span className="tb-num">{days}</span>
          <span>{days === 0 ? "leaves today" : days === 1 ? "day to go" : "days to go"}</span>
        </div>

        <div className="home-next-savings">
          <div className="home-next-savings-row">
            <span>{savingsLine}</span>
            {target > 0 && <span className="home-muted">{percent}%</span>}
          </div>
          <div className="tb-progress">
            <span style={{ width: `${percent}%` }} />
          </div>
        </div>

        <Link to={action.to} className="tb-btn tb-btn-accent home-next-action">
          {action.label}
        </Link>
      </div>

      {photo?.photographerUrl && photo?.unsplashUrl && (
        <span className="home-next-credit">
          Photo by{" "}
          <a href={photo.photographerUrl} target="_blank" rel="noopener noreferrer">
            {photo.photographer}
          </a>{" "}
          on{" "}
          <a href={photo.unsplashUrl} target="_blank" rel="noopener noreferrer">
            Unsplash
          </a>
        </span>
      )}
    </section>
  );
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

  const todayLabel = now.toLocaleDateString("en-US", {
    weekday: "long",
    month: "short",
    day: "numeric",
  });

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

  const nextTripOverview = nextTrip
    ? savingsOverview.find((item) => item.tripId === nextTrip.id)
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

  const totalBudgeted = savingsTrips.reduce(
    (sum, trip) => sum + Number(trip.budgetTotal || 0),
    0
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

  return (
    <div className="dashboard-page home-page">

      {/* Sidebar */}
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      {/* Main Content */}
      <main className="dashboard-main home-main">

        {/* Greeting */}
        <header className="home-header">
          <div className="home-greeting">
            <span className="tb-kicker">{todayLabel}</span>
            <h1>
              <span className="tb-wide">{greeting()},</span>{" "}
              <span className="tb-serif">{firstName}</span>
            </h1>
          </div>

          <div className="home-header-actions">
            <NotificationBell />
            <Link to="/create-trip" className="tb-btn tb-btn-primary">
              <Plus size={18} aria-hidden="true" />
              New trip
            </Link>
          </div>
        </header>

        <div className="home-top">

          {/* Next Trip */}
          {loading ? (
            <section className="home-next home-next-empty">
              <p>Loading your trips...</p>
            </section>
          ) : nextTrip ? (
            <NextTripCard trip={nextTrip} days={daysUntilTrip} overview={nextTripOverview} />
          ) : (
            <section className="home-next home-next-empty">
              <span className="tb-kicker">Your next adventure</span>
              <h2>
                <span className="tb-wide">Where to</span>{" "}
                <span className="tb-serif">next?</span>
              </h2>
              <p>Add a trip and we'll help you budget and save for it.</p>
              <Link to="/create-trip" className="tb-btn tb-btn-accent">Plan a trip</Link>
            </section>
          )}

          <div className="home-side">

            {/* Savings Overview */}
            <section className="tb-panel home-totals">
              <div className="home-totals-head">
                <span className="tb-kicker">Saved for upcoming trips</span>
                <Link to="/budget">View</Link>
              </div>

              {savingsLoading ? (
                <p>Loading savings...</p>
              ) : savingsError ? (
                <p>Unable to load savings.</p>
              ) : (
                <>
                  <div className="home-totals-amount">
                    <span className="tb-num">{formatMoney(totalSaved, mainCurrency)}</span>
                    <span>
                      {totalGoal > 0
                        ? `${savingsPercent}% of ${formatMoney(totalGoal, mainCurrency)} goal`
                        : "No savings goals set yet"}
                    </span>
                  </div>

                  <div className="tb-progress home-totals-bar">
                    <span style={{ width: `${savingsPercent}%` }} />
                  </div>

                  {totalBudgeted > 0 && (
                    <p className="home-totals-note">
                      {formatMoney(totalBudgeted, mainCurrency)} budgeted across{" "}
                      {savingsTrips.length} {savingsTrips.length === 1 ? "trip" : "trips"}
                    </p>
                  )}
                </>
              )}
            </section>

            {/* Bank Connection Card */}
            <div className="home-bank">
              <BankConnection />
            </div>
          </div>
        </div>

        {/* Upcoming Trips */}
        <section className="home-trips">
          <div className="home-section-head">
            <h2>
              <span className="tb-wide">Upcoming</span>{" "}
              <span className="tb-serif">trips</span>
            </h2>

            {trips.length > 0 && (
              <Link to="/trips" className="home-link">
                View all ({trips.length})
              </Link>
            )}
          </div>

          {error && <p className="error-text">{error}</p>}

          {!loading && !error && upcoming.length === 0 && (
            <p className="home-muted">
              No upcoming trips yet. <Link to="/create-trip" className="home-link">Plan one</Link>.
            </p>
          )}

          {!loading && !error && upcoming.length > 0 && (
            <div className="home-trip-grid">
              {upcoming.map((trip) => (
                <DashboardTripCard key={trip.id} trip={trip} />
              ))}
            </div>
          )}
        </section>

      </main>
    </div>
  );
}

export default Dashboard;
