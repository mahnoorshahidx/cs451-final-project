import { useEffect, useState } from "react";
import "../styles/TravelSlideshow.css";

// Keep using the five photos already in frontend/public/images.
// Each caption is written to match its photo; no API or new images required.
const slides = [
  {
    image: "/images/travel-1.jpg",
    location: "Bangkok, Thailand",
    lead: "EXPLORE",
    before: " ",
    highlight: "Bangkok’s",
    after: " neon streets",
  },
  {
    image: "/images/travel-2.jpg",
    location: "Istanbul, Türkiye",
    lead: "DISCOVER",
    before: " ",
    highlight: "Istanbul’s",
    after: " timeless beauty",
  },
  {
    image: "/images/travel-3.jpg",
    location: "Chefchaouen, Morocco",
    lead: "WANDER",
    before: " through ",
    highlight: "Chefchaouen’s",
    after: " blue alleys",
  },
  {
    image: "/images/travel-4.jpg",
    location: "Reine, Norway",
    lead: "CHASE",
    before: " ",
    highlight: "Lofoten’s",
    after: " northern lights",
  },
  {
    image: "/images/travel-5.jpg",
    location: "Fujiyoshida, Japan",
    lead: "ADMIRE",
    before: " ",
    highlight: "Japan’s",
    after: " mountain views",
  },
];

export default function TravelSlideshow() {
  const [current, setCurrent] = useState(0);
  const slide = slides[current];

  useEffect(() => {
    const interval = window.setInterval(() => {
      setCurrent((previous) => (previous + 1) % slides.length);
    }, 5000);

    return () => window.clearInterval(interval);
  }, []);

  return (
    <div className="travel-slideshow" aria-label="Travel destination slideshow">
      {slides.map((item, index) => (
        <div
          key={item.image}
          className={`travel-slide${index === current ? " active" : ""}`}
          style={{ backgroundImage: `url("${item.image}")` }}
          aria-hidden="true"
        />
      ))}

      {/* Figma-inspired dark gradient ON THE PHOTOS, not on the form. */}
      <div className="travel-slide-scrim" aria-hidden="true" />

      <div className="travel-slide-caption" aria-live="off">
        <p className="travel-slide-location">{slide.location}</p>
        <p className="travel-slide-headline">
          <strong className="travel-slide-verb">{slide.lead}</strong>
          {slide.before}
          <strong className="travel-slide-highlight">{slide.highlight}</strong>
          {slide.after}
        </p>
      </div>

      <div className="travel-slide-dots" role="group" aria-label="Choose a travel photo">
        {slides.map((item, index) => (
          <button
            key={item.image}
            type="button"
            className={index === current ? "active" : ""}
            aria-label={`Show ${item.location}`}
            aria-current={index === current ? "true" : undefined}
            onClick={() => setCurrent(index)}
          />
        ))}
      </div>
    </div>
  );
}
