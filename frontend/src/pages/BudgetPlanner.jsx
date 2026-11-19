
import Sidebar from "../components/Sidebar";

function BudgetPlanner() {
  return (
    <div className="dashboard-page">
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      <main className="dashboard-main">
        <h1>Budget Planner</h1>
        <p>Plan and manage your trip expenses.</p>
      </main>
    </div>
  );
}

export default BudgetPlanner;
