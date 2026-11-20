
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { CalendarDays, Users } from "lucide-react";
import { getDestinationPhoto, getProfile } from "../api/api";
import { formatDate } from "./TripCard";

function daysUntil(iso) {
  if (!iso) return null;

  const [year, month, day] = iso.split("-").map(Number);
  const departure = new Date(year, month - 1, day);
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  return Math.round((departure - today) / 86400000);
}

export default function DashboardTripCard({ trip }) {
  const [photo, setPhoto] = useState(null);
  const [dateFormat, setDateFormat] = useState("MM/DD/YYYY");

  useEffect(() => {
    let cancelled = false;

    getProfile()
      .then((user) => {
        if (!cancelled) {
          setDateFormat(user.dateFormat || "MM/DD/YYYY");
        }
      })
      .catch(() => {});

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const destination = trip.destination?.trim();
    setPhoto(null);

    if (!destination) return;

    let cancelled = false;

    getDestinationPhoto(destination)
      .then((result) => {
        if (!cancelled && result?.imageUrl) {
          setPhoto(result);
        }
      })
      .catch(() => {
        if (!cancelled) setPhoto(null);
      });

    return () => {
      cancelled = true;
    };
  }, [trip.destination]);

  const days = daysUntil(trip.startDate);
  const status = days !== null && days > 0
    ? "Upcoming"
    : "In progress";

  return (
    <article className="dashboard-trip-card">
      <div className="dashboard-trip-photo">
        {photo?.imageUrl && (
          <img
            src={photo.imageUrl}
            alt=""
            loading="lazy"
            onError={() => setPhoto(null)}
          />
        )}

        <span className="dashboard-trip-status">
          {status}
        </span>

        {photo?.photographerUrl && photo?.unsplashUrl && (
          <span className="dashboard-trip-credit">
            Photo by{" "}
            <a
              href={photo.photographerUrl}
              target="_blank"
              rel="noopener noreferrer"
            >
              {photo.photographer}
            </a>
            {" "}on{" "}
            <a
              href={photo.unsplashUrl}
              target="_blank"
              rel="noopener noreferrer"
            >
              Unsplash
            </a>
          </span>
        )}
      </div>

      <div className="dashboard-trip-body">
        <p className="dashboard-trip-location">
          {trip.destination || "Destination not specified"}
        </p>

        <h3>{trip.name}</h3>

        <div className="dashboard-trip-details">
          <span>
            <CalendarDays size={15} />
            {formatDate(trip.startDate, dateFormat)}
            {" – "}
            {formatDate(trip.endDate, dateFormat)}
          </span>

          <span>
            <Users size={15} />
            {trip.numberOfTravelers}{" "}
            {trip.numberOfTravelers === 1
              ? "traveler"
              : "travelers"}
          </span>
        </div>

        <div className="dashboard-trip-actions">
          <Link to={`/budget/${trip.id}`}>
            View budget
          </Link>

          <Link to={`/trips/${trip.id}/edit?tab=details`}>
            Edit details
          </Link>
        </div>
      </div>
    </article>
  );
}