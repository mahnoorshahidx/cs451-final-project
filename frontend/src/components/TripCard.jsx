
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getProfile } from "../api/api";

// Formats dates using the user's saved preference
export function formatDate(iso, dateFormat = "MM/DD/YYYY") {
  if (!iso) return "";

  const [year, month, day] = iso.split("-");

  if (dateFormat === "DD/MM/YYYY") {
    return `${day}/${month}/${year}`;
  }

  return `${month}/${day}/${year}`;
}

// Calculates how many days remain until a trip
function daysUntil(iso) {
  const [y, m, d] = iso.split("-").map(Number);
  const start = new Date(y, m - 1, d);
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  return Math.round((start - today) / 86400000);
}

function TripCard({ trip, onDelete }) {
  const [dateFormat, setDateFormat] = useState("MM/DD/YYYY");

  // Load the user's saved date format
  useEffect(() => {
    getProfile()
      .then((user) => {
        setDateFormat(user.dateFormat || "MM/DD/YYYY");
      })
      .catch((err) => console.error(err));
  }, []);

  const days = daysUntil(trip.startDate);

  const status =
    days > 0
      ? `In ${days} day${days === 1 ? "" : "s"}`
      : days === 0
      ? "Today"
      : "Past trip";

  return (
    <article className="trip-card">
      <div className="trip-card-top">
        <div>
          <h3>{trip.name}</h3>
          <p className="trip-card-destination">{trip.destination}</p>
        </div>

        <span className={`trip-card-badge ${days < 0 ? "past" : ""}`}>
          {status}
        </span>
      </div>

      <p className="trip-card-meta">
        {formatDate(trip.startDate, dateFormat)} –{" "}
        {formatDate(trip.endDate, dateFormat)} ·{" "}
        {trip.numberOfTravelers} traveler
        {trip.numberOfTravelers === 1 ? "" : "s"}
      </p>

      <div className="trip-card-actions">
      <div className="trip-card-links">
        <Link to={`/budget/${trip.id}`} className="trip-card-link">Budget</Link>
        <Link to={`/trips/${trip.id}/edit?tab=details`} className="trip-card-link">Edit details</Link>
      </div>

      {onDelete && (
        <button
          type="button"
          className="trip-card-delete"
          onClick={() => onDelete(trip)}
        >
          Delete
        </button>
      )}
    </div>
    </article>
  );
}

export default TripCard;
