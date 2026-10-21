import { Link } from "react-router-dom";

// "2027-01-10" -> "Jan 10, 2027" (parsed as a local date, so no timezone shift)
export function formatDate(iso) {
  if (!iso) return "";
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function daysUntil(iso) {
  const [y, m, d] = iso.split("-").map(Number);
  const start = new Date(y, m - 1, d);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return Math.round((start - today) / 86400000);
}

function TripCard({ trip, onDelete }) {
  const days = daysUntil(trip.startDate);
  const status =
    days > 0 ? `In ${days} day${days === 1 ? "" : "s"}` : days === 0 ? "Today" : "Past trip";

  return (
    <article className="trip-card">
      <div className="trip-card-top">
        <div>
          <h3>{trip.name}</h3>
          <p className="trip-card-destination">{trip.destination}</p>
        </div>
        <span className={`trip-card-badge ${days < 0 ? "past" : ""}`}>{status}</span>
      </div>

      <p className="trip-card-meta">
        {formatDate(trip.startDate)} – {formatDate(trip.endDate)} ·{" "}
        {trip.numberOfTravelers} traveler{trip.numberOfTravelers === 1 ? "" : "s"}
      </p>

      <div className="trip-card-actions">
        <Link to={`/trips/${trip.id}/edit`} className="trip-card-link">
          Edit trip &amp; budget
        </Link>
        {onDelete && (
          <button type="button" className="trip-card-delete" onClick={() => onDelete(trip)}>
            Delete
          </button>
        )}
      </div>
    </article>
  );
}

export default TripCard;
