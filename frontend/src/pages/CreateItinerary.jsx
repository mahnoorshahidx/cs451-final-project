
import { Link } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import "../styles/Dashboard.css";
import "../styles/CreateItinerary.css";

// Temporary data for the UI layout.
// Feature 3 will replace this with saved trips.
const previewTrips = [
  {
    id: 1,
    name: "Washington Weekend",
    destination: "Washington, DC",
    dates: "Nov 12–14, 2026",
    travelers: 1,
  },
  {
    id: 2,
    name: "Chicago 2027",
    destination: "Chicago, Illinois",
    dates: "Feb 8–14, 2027",
    travelers: 1,
  },
];

function CreateItinerary() {
  return (
    <div className="dashboard-page itinerary-page">
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      <main className="dashboard-main itinerary-main">

        {/* Top bar */}
        <header className="itinerary-topbar">
          <Link to="/trips" className="itinerary-exit">
            × &nbsp; Exit
          </Link>
        </header>

        {/* Progress indicator */}
        <div className="itinerary-progress">
          <div className="itinerary-progress-header">
            <span>CREATE YOUR ITINERARY</span>
            <span>Step 1 of 4</span>
          </div>

          <div className="itinerary-progress-track">
            <div className="itinerary-progress-active" />
            <div />
            <div />
            <div />
          </div>
        </div>

        {/* Main questionnaire */}
        <div className="itinerary-body">
          <div className="itinerary-content">

            <div className="itinerary-heading">
              <h1>Which trip are we planning?</h1>
              <p>
                Select an existing trip to start building
                your itinerary.
              </p>
            </div>

            {/* Trip preview cards */}
            <section className="itinerary-trip-list">
              {previewTrips.map((trip) => (
                <div
                  key={trip.id}
                  className="itinerary-trip-card"
                >
                  <div className="itinerary-trip-info">
                    <h2>{trip.name}</h2>
                    <p>{trip.destination}</p>

                    <div className="itinerary-trip-meta">
                      <span>▦ {trip.dates}</span>
                      <span>
                        ♙ {trip.travelers} traveler
                        {trip.travelers !== 1 ? "s" : ""}
                      </span>
                    </div>
                  </div>

                  <span
                    className="itinerary-radio"
                    aria-hidden="true"
                  />
                </div>
              ))}
            </section>

            <Link
              to="/create-trip"
              className="itinerary-new-trip"
            >
              + Create a new trip
            </Link>

            {/* Bottom navigation */}
            <footer className="itinerary-footer">
              <Link to="/trips" className="itinerary-back">
                ← Back
              </Link>

              <button
                type="button"
                className="itinerary-continue"
                disabled
                title="Trip selection will be enabled in a later feature."
              >
                Continue →
              </button>
            </footer>

          </div>
        </div>

      </main>
    </div>
  );
}

export default CreateItinerary;
