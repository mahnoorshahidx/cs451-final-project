import { useEffect, useState } from "react";
import { getDestinationPhoto } from "../api/api";

// Loads an Unsplash photo for a destination through the backend.
// Returns [photo, clearPhoto]. photo is null until one arrives, or if there isn't one.
export default function useDestinationPhoto(destination) {
  const place = destination?.trim() || "";
  const [result, setResult] = useState({ place: "", photo: null });

  useEffect(() => {
    if (!place) return undefined;

    let cancelled = false;

    getDestinationPhoto(place)
      .then((data) => {
        if (!cancelled) setResult({ place, photo: data?.imageUrl ? data : null });
      })
      .catch(() => {
        if (!cancelled) setResult({ place, photo: null });
      });

    return () => {
      cancelled = true;
    };
  }, [place]);

  // Only return a photo that belongs to the current destination
  const photo = result.place === place ? result.photo : null;
  const clearPhoto = () => setResult({ place, photo: null });

  return [photo, clearPhoto];
}
