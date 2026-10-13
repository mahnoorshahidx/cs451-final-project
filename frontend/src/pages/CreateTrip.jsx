import "../styles/CreateTrip.css";

function CreateTrip() {
  return (
    <div className="create-trip-page">

      {/* Left sidebar */}
      <aside className="trip-sidebar">
      </aside>

      {/* Right main content */}
      <main className="trip-main">

        {/* Top navigation */}
        <header className="trip-header">
        </header>

        {/* Banner */}
        <section className="trip-banner">
        <div>
            <p className="trip-breadcrumb">
            My Trips / Plan a New Trip / Budget
            </p>

            <h1>Create your trip budget</h1>

            <p>
            Plan your spending now, so you can enjoy
            the journey later.
            </p>
        </div>
        </section>

        {/* Trip details */}
        <section className="trip-details">
        </section>

        {/* Budget area */}
        <div className="trip-budget-layout">

          {/* Left: Set your budget */}
          <section className="trip-budget-form">
          </section>

          {/* Right: Budget breakdown */}
          <section className="trip-budget-summary">
          </section>

        </div>

      </main>

    </div>
  );
}

export default CreateTrip;