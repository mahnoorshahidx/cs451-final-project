
import { Link } from "react-router-dom";
import "../styles/Dashboard.css";
import Sidebar from "../components/Sidebar";
import BankConnection from "./BankConnection";

function Dashboard() {
  const user = null; // Replace with logged-in user later
  const firstName = user?.firstName?.trim();

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
            <h1>
              Good morning{firstName ? `, ${firstName}` : ""}!
            </h1>
            <p>
              Where to next? Let's turn your travel dreams
              into a plan.
            </p>
          </div>
        </section>

        {/* Dashboard Content */}
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

        </div>

      </main>
    </div>
  );
}

export default Dashboard;
