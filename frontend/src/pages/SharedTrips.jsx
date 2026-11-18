import Sidebar from "../components/Sidebar";
import "../styles/Dashboard.css";

// Temporary page shell. Trip membership data and invitations will be added later.
function SharedTrips() {
  return (
    <div className="dashboard-page">
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      <main className="dashboard-main">
        <header className="dashboard-header" />

        <section className="dashboard-banner">
          <div>
            <h1>Shared Trips</h1>
            <p>Plan trips together with other members.</p>
          </div>
        </section>

        <section aria-label="Shared trips content">
          <p>Shared trips will appear here when the sharing feature is implemented.</p>
        </section>
      </main>
    </div>
  );
}

export default SharedTrips;