import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { CalendarDays, Trash2, Users } from "lucide-react";
import { getProfile } from "../api/api";
import useDestinationPhoto from "../lib/useDestinationPhoto";
import { formatDate } from "../lib/formatDate";

// Calculates how many days remain until a date
function daysUntil(iso) {
  const [y, m, d] = iso.split("-").map(Number);
  const start = new Date(y, m - 1, d);
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  return Math.round((start - today) / 86400000);
}

// Label and color for the status tag on a trip
function tripStatus(trip) {
  const days = daysUntil(trip.startDate);
  const daysToEnd = daysUntil(trip.endDate);

  if (daysToEnd < 0) return { label: "Past trip", tone: "past" };
  if (days <= 0) return { label: "In progress", tone: "now" };
  if (days === 1) return { label: "Tomorrow", tone: "now" };
  if (days <= 30) return { label: `In ${days} days`, tone: "soon" };
  return { label: `In ${days} days`, tone: "later" };
}

// Photo card for the My Trips page
function TripCard({ trip, onDelete }) {
  const [dateFormat, setDateFormat] = useState("MM/DD/YYYY");
  const [photo, clearPhoto] = useDestinationPhoto(trip.destination);

  // Load the user's saved date format
  useEffect(() => {
    getProfile()
      .then((user) => {
        setDateFormat(user.dateFormat || "MM/DD/YYYY");
      })
      .catch((err) => console.error(err));
  }, []);

  const status = tripStatus(trip);
  const place = trip.destination?.split(",")[0]?.trim() || trip.name;

  return (
    <article className={`mytrip ${status.tone === "past" ? "is-past" : ""}`}>
      <div className="mytrip-photo">
        {photo?.imageUrl && (
          <img src={photo.imageUrl} alt="" loading="lazy" onError={clearPhoto} />
        )}

        <span className={`mytrip-status is-${status.tone}`}>{status.label}</span>
        <span className="tb-wide mytrip-place">{place}</span>

        {photo?.photographerUrl && photo?.unsplashUrl && (
          <span className="mytrip-credit">
            Photo by{" "}
            <a href={photo.photographerUrl} target="_blank" rel="noopener noreferrer">
              {photo.photographer}
            </a>{" "}
            on{" "}
            <a href={photo.unsplashUrl} target="_blank" rel="noopener noreferrer">
              Unsplash
            </a>
          </span>
        )}
      </div>

      <div className="mytrip-body">
        <div>
          <h3>{trip.name}</h3>
          <p className="mytrip-destination">{trip.destination}</p>
        </div>

        <div className="mytrip-details">
          <span>
            <CalendarDays size={15} aria-hidden="true" />
            {formatDate(trip.startDate, dateFormat)} – {formatDate(trip.endDate, dateFormat)}
          </span>
          <span>
            <Users size={15} aria-hidden="true" />
            {trip.numberOfTravelers} traveler{trip.numberOfTravelers === 1 ? "" : "s"}
          </span>
        </div>

        <div className="mytrip-actions">
          <Link to={`/budget-planner/${trip.id}`} className="mytrip-link is-main">Budget</Link>
          <Link to={`/trips/${trip.id}/edit?tab=details`} className="mytrip-link">Edit details</Link>

          {onDelete && (
            <button
              type="button"
              className="mytrip-delete"
              onClick={() => onDelete(trip)}
              aria-label={`Delete ${trip.name}`}
              title="Delete trip"
            >
              <Trash2 size={17} aria-hidden="true" />
            </button>
          )}
        </div>
      </div>
    </article>
  );
}

export default TripCard;
