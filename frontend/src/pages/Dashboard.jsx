import { Link } from "react-router-dom";
import "../styles/Dashboard.css";

function Dashboard() {
  return (
    <div className="dashboard-page">

      {/* Left Sidebar */}
      <aside className="dashboard-sidebar">
      </aside>

      {/* Main Content */}
      <main className="dashboard-main">

        {/* Top Profile Header */}
        <header className="dashboard-header">
        </header>

        {/* Welcome Banner */}
        <section className="dashboard-banner">
        <div>
            <h1>Good morning, User!</h1>
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
            </section>

          </div>

          {/* Right Column */}
          <aside className="dashboard-card">
          </aside>

        </div>

      </main>

    </div>
  );
}

export default Dashboard;