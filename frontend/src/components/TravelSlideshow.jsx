
import { useEffect, useState } from "react";
import "../styles/TravelSlideshow.css";

const images = [
  "/images/travel-1.jpg",
  "/images/travel-2.jpg",
  "/images/travel-3.jpg",
  "/images/travel-4.jpg",
  "/images/travel-5.jpg",
];

export default function TravelSlideshow() {
  const [current, setCurrent] = useState(0);

  useEffect(() => {
    const interval = setInterval(() => {
      setCurrent((prev) => (prev + 1) % images.length);
    }, 5000);

    return () => clearInterval(interval);
  }, []);

  return (
    <div className="travel-slideshow">
      {images.map((image, index) => (
        <div
          key={image}
          className={`travel-slide ${
            index === current ? "active" : ""
          }`}
          style={{ backgroundImage: `url("${image}")` }}
        />
      ))}

      <div className="travel-slide-dots">
        {images.map((_, index) => (
          <button
            key={index}
            type="button"
            className={index === current ? "active" : ""}
            aria-label={`Show travel photo ${index + 1}`}
            aria-current={index === current ? "true" : undefined}
            onClick={() => setCurrent(index)}
          />
        ))}
      </div>
    </div>
  );
}
