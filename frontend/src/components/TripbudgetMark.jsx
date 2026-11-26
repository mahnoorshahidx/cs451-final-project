// Yellow eight-point star used as the Tripbudget logo mark
export default function TripbudgetMark({ size = 22, color = "#E9AB42" }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <g stroke={color} strokeWidth="3.2" strokeLinecap="round">
        <path d="M12 2.5v19M2.5 12h19M5.3 5.3l13.4 13.4M18.7 5.3 5.3 18.7" />
      </g>
    </svg>
  );
}
