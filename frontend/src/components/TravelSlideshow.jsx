import { useEffect, useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import "../styles/TravelSlideshow.css";

// The five photos already in frontend/public/images.
// Each caption is written to match its photo.
const slides = [
  {
    image: "/images/travel-1.jpg",
    city: "Bangkok",
    country: "Thailand",
    tagline: "explore the neon streets",
  },
  {
    image: "/images/travel-2.jpg",
    city: "Istanbul",
    country: "Türkiye",
    tagline: "discover its timeless beauty",
  },
  {
    image: "/images/travel-3.jpg",
    city: "Chefchaouen",
    country: "Morocco",
    tagline: "wander the blue alleys",
  },
  {
    image: "/images/travel-4.jpg",
    city: "Reine",
    country: "Norway",
    tagline: "chase the northern lights",
  },
  {
    image: "/images/travel-5.jpg",
    city: "Fujiyoshida",
    country: "Japan",
    tagline: "admire the mountain views",
  },
];

const prefersReducedMotion = () =>
  typeof window !== "undefined" &&
  window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;

// showArrows adds previous/next buttons (used on the landing page).
export default function TravelSlideshow({ showArrows = false }) {
  const [current, setCurrent] = useState(0);
  const [paused, setPaused] = useState(false);
  const slide = slides[current];

  // Move on every 5 seconds, unless the user picked a slide or prefers less motion
  useEffect(() => {
    if (paused || prefersReducedMotion()) return undefined;

    const interval = window.setInterval(() => {
      setCurrent((previous) => (previous + 1) % slides.length);
    }, 5000);

    return () => window.clearInterval(interval);
  }, [paused]);

  const goTo = (index) => {
    setPaused(true);
    setCurrent((index + slides.length) % slides.length);
  };

  return (
    <div className="travel-slideshow" aria-roledescription="carousel" aria-label="Travel destinations">
      {slides.map((item, index) => (
        <div
          key={item.image}
          className={`travel-slide${index === current ? " active" : ""}`}
          style={{ backgroundImage: `url("${item.image}")` }}
          role="img"
          aria-label={`${item.city}, ${item.country}`}
          aria-hidden={index !== current}
        />
      ))}

      <div className="travel-slide-scrim" aria-hidden="true" />

      <div className="travel-slide-caption" aria-live="polite">
        <span className="tb-kicker travel-slide-country">{slide.country}</span>
        <span className="tb-wide travel-slide-city">{slide.city}</span>
        <span className="tb-serif travel-slide-tagline">{slide.tagline}</span>
      </div>

      <div className="travel-slide-controls">
        <div className="travel-slide-dots" role="group" aria-label="Choose a destination">
          {slides.map((item, index) => (
            <button
              key={item.image}
              type="button"
              className={index === current ? "active" : ""}
              aria-label={`Show ${item.city}`}
              aria-current={index === current ? "true" : undefined}
              onClick={() => goTo(index)}
            />
          ))}
        </div>

        {showArrows && (
          <div className="travel-slide-arrows">
            <button type="button" aria-label="Previous destination" onClick={() => goTo(current - 1)}>
              <ChevronLeft size={18} strokeWidth={2.4} />
            </button>
            <button type="button" aria-label="Next destination" className="next" onClick={() => goTo(current + 1)}>
              <ChevronRight size={18} strokeWidth={2.4} />
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
