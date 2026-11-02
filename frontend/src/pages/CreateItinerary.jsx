
import Sidebar from "../components/Sidebar";
import "../styles/Dashboard.css";

function CreateItinerary() {
  return (
    <div className="dashboard-page">
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      <main className="dashboard-main">
        <section className="dashboard-banner">
          <div>
            <h1>Create Itinerary</h1>
            <p>Select a trip to start planning your itinerary.</p>
          </div>
        </section>
      </main>
    </div>
  );
}

export default CreateItinerary;
