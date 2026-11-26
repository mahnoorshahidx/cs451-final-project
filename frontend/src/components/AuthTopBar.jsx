import { Link } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import TripbudgetMark from "./TripbudgetMark";

// Logo + "Back to home" row above the sign in and sign up cards
export default function AuthTopBar() {
  return (
    <div className="auth-top">
      <Link to="/" className="auth-brand">
        <TripbudgetMark size={22} />
        <span className="tb-wide">Tripbudget</span>
      </Link>

      <Link to="/" className="auth-back">
        <ArrowLeft size={16} aria-hidden="true" />
        Back to home
      </Link>
    </div>
  );
}
