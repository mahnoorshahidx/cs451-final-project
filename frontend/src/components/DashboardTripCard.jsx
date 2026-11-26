import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { CalendarDays, Users } from "lucide-react";
import { getProfile } from "../api/api";
import useDestinationPhoto from "../lib/useDestinationPhoto";
import { formatDate } from "./TripCard";

function daysUntil(iso) {
  if (!iso) return null;

  const [year, month, day] = iso.split("-").map(Number);
  const departure = new Date(year, month - 1, day);
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  return Math.round((departure - today) / 86400000);
}

function countdownLabel(days) {
  if (days === null) return "Upcoming";
  if (days <= 0) return "In progress";
  if (days === 1) return "Tomorrow";
  return `In ${days} days`;
}

// Photo tile for the home page's upcoming trips
export default function DashboardTripCard({ trip }) {
  const [photo, clearPhoto] = useDestinationPhoto(trip.destination);
  const [dateFormat, setDateFormat] = useState("MM/DD/YYYY");

  useEffect(() => {
    let cancelled = false;

    getProfile()
      .then((user) => {
        if (!cancelled) setDateFormat(user.dateFormat || "MM/DD/YYYY");
      })
      .catch(() => {});

    return () => {
      cancelled = true;
    };
  }, []);

  const days = daysUntil(trip.startDate);
  const soon = days !== null && days <= 1;
  const city = trip.destination?.split(",")[0]?.trim() || trip.name;

  return (
    <article className="home-trip">
      <div className="home-trip-photo">
        {photo?.imageUrl && (
          <img src={photo.imageUrl} alt="" loading="lazy" onError={clearPhoto} />
        )}

        <span className={`home-trip-status${soon ? " soon" : ""}`}>
          {countdownLabel(days)}
        </span>

        <span className="tb-wide home-trip-city">{city}</span>

        {photo?.photographerUrl && photo?.unsplashUrl && (
          <span className="home-trip-credit">
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

      <div className="home-trip-body">
        <h3>{trip.name}</h3>

        <div className="home-trip-details">
          <span>
            <CalendarDays size={15} aria-hidden="true" />
            {formatDate(trip.startDate, dateFormat)} – {formatDate(trip.endDate, dateFormat)}
          </span>
          <span>
            <Users size={15} aria-hidden="true" />
            {trip.numberOfTravelers} {trip.numberOfTravelers === 1 ? "traveler" : "travelers"}
          </span>
        </div>

        <div className="home-trip-actions">
          <Link to={`/budget/${trip.id}`}>View budget</Link>
          <Link to={`/trips/${trip.id}/edit?tab=details`}>Edit details</Link>
        </div>
      </div>
    </article>
  );
}
