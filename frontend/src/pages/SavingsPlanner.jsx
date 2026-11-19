
import Sidebar from "../components/Sidebar";

function SavingsPlanner() {
  return (
    <div className="dashboard-page">
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      <main className="dashboard-main">
        <h1>Savings Planner</h1>
        <p>Track your savings goals for upcoming trips.</p>
      </main>
    </div>
  );
}

export default SavingsPlanner;
