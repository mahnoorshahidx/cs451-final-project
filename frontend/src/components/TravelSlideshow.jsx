import { useEffect, useState } from "react";
import "../styles/TravelSlideshow.css";

// The original five photos already live in frontend/public/images/.
// Keep the file-to-destination mapping aligned with your teammate's photos.
// No external image service or additional image files are necessary.
const PHOTO_ROOT = `${import.meta.env.BASE_URL}images/`;

export const TRAVEL_SLIDES = [
  {
    id: "bangkok",
    image: "travel-1.jpg",
    category: "Nightlife & city life",
    verb: "EXPLORE",
    before: "",
    highlight: "Bangkok’s",
    after: " neon streets",
    location: "Bangkok, Thailand",
    position: "center 55%",
  },
  {
    id: "istanbul",
    image: "travel-2.jpg",
    category: "History & landmarks",
    verb: "DISCOVER",
    before: "",
    highlight: "Istanbul’s",
    after: " timeless beauty",
    location: "Istanbul, Türkiye",
    position: "center",
  },
  {
    id: "chefchaouen",
    image: "travel-3.jpg",
    category: "Culture & hidden gems",
    verb: "WANDER",
    before: "through ",
    highlight: "Chefchaouen’s",
    after: " blue alleys",
    location: "Chefchaouen, Morocco",
    position: "center",
  },
  {
    id: "lofoten",
    image: "travel-4.jpg",
    category: "Nature & adventure",
    verb: "CHASE",
    before: "",
    highlight: "Lofoten’s",
    after: " northern lights",
    location: "Reine, Norway",
    position: "center 45%",
  },
  {
    id: "fujiyoshida",
    image: "travel-5.jpg",
    category: "Scenic landscapes",
    verb: "ADMIRE",
    before: "",
    highlight: "Japan’s",
    after: " mountain views",
    location: "Fujiyoshida, Japan",
    position: "center",
  },
];

/**
 * Full-screen travel background and controls using existing public/images photos.
 *
 * For the authentication card to OVERLAY the photos, pass your EXISTING
 * login/signup component as children. This does not alter its functionality:
 *
 * <TravelSlideshow>
 *   <ExistingLoginSignupCard />
 * </TravelSlideshow>
 *
 * Without children the carousel still renders, but a separate old form column
 * cannot be repositioned automatically by this component.
 */
export default function TravelSlideshow({
  children,
  intervalMs = 5000,
  captionSide = "right", // "right" matches Figma; "left" matches your first brief.
}) {
  const [current, setCurrent] = useState(0);
  const [playing, setPlaying] = useState(true);
  const slide = TRAVEL_SLIDES[current];
  const delay = Number.isFinite(intervalMs) ? Math.max(intervalMs, 2000) : 5000;

  useEffect(() => {
    if (!playing) return undefined;
    const timer = window.setInterval(() => {
      if (!document.hidden) {
        setCurrent((index) => (index + 1) % TRAVEL_SLIDES.length);
      }
    }, delay);
    return () => window.clearInterval(timer);
  }, [delay, playing]);

  // Preload the upcoming photo for a clean crossfade on slow connections.
  useEffect(() => {
    const next = TRAVEL_SLIDES[(current + 1) % TRAVEL_SLIDES.length];
    const image = new Image();
    image.src = PHOTO_ROOT + next.image;
  }, [current]);

  function goTo(index) {
    setCurrent((index + TRAVEL_SLIDES.length) % TRAVEL_SLIDES.length);
  }

  return (
    <section
      className={`travel-auth-page travel-auth-page--caption-${captionSide === "left" ? "left" : "right"}`}
      aria-label="Travel destination slideshow"
      aria-roledescription="carousel"
    >
      <div className="travel-auth-carousel__photos" aria-hidden="true">
        {TRAVEL_SLIDES.map((item, index) => (
          <img
            key={item.id}
            className={`travel-auth-carousel__photo${index === current ? " travel-auth-carousel__photo--active" : ""}`}
            src={PHOTO_ROOT + item.image}
            alt=""
            style={{ objectPosition: item.position }}
            loading="eager"
            decoding="async"
          />
        ))}
        <div className="travel-auth-carousel__scrim" />
      </div>

      <div className="travel-auth-carousel__topbar">
        <span className="travel-auth-carousel__category">{slide.category}</span>
        <div className="travel-auth-carousel__playback">
          <span className="travel-auth-carousel__counter">
            {String(current + 1).padStart(2, "0")} / {String(TRAVEL_SLIDES.length).padStart(2, "0")}
          </span>
          <button
            className="travel-auth-carousel__round-button"
            type="button"
            onClick={() => setPlaying((value) => !value)}
            aria-label={playing ? "Pause slideshow" : "Play slideshow"}
            aria-pressed={!playing}
          >
            {playing ? (
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 5v14M16 5v14" /></svg>
            ) : (
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 5 10 7-10 7z" /></svg>
            )}
          </button>
        </div>
      </div>

      {children != null && (
        <div className="travel-auth-page__form-layer">{children}</div>
      )}

      <div className="travel-auth-carousel__copy" aria-live="off">
        <p className="travel-auth-carousel__location">{slide.location}</p>
        <p className="travel-auth-carousel__headline">
          <strong>{slide.verb}</strong>{" "}
          {slide.before}
          <span>{slide.highlight}</span>
          {slide.after}
        </p>
      </div>

      <div className="travel-auth-carousel__footer">
        <div className="travel-auth-carousel__dots" role="group" aria-label="Choose a destination">
          {TRAVEL_SLIDES.map((item, index) => (
            <button
              key={item.id}
              type="button"
              className={`travel-auth-carousel__dot${index === current ? " travel-auth-carousel__dot--active" : ""}`}
              onClick={() => goTo(index)}
              aria-label={`Show ${item.location}`}
              aria-current={index === current ? "true" : undefined}
            >
              <span />
            </button>
          ))}
        </div>
        <div className="travel-auth-carousel__arrows">
          <button type="button" className="travel-auth-carousel__round-button" aria-label="Previous photo" onClick={() => goTo(current - 1)}>
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m15 5-7 7 7 7" /></svg>
          </button>
          <button type="button" className="travel-auth-carousel__round-button" aria-label="Next photo" onClick={() => goTo(current + 1)}>
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 5 7 7-7 7" /></svg>
          </button>
        </div>
      </div>
    </section>
  );
}
