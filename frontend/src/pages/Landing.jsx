import { Link } from "react-router-dom";
import { CalendarDays, PieChart, TrendingUp, Users } from "lucide-react";
import LandingBudgetPreview from "../components/LandingBudgetPreview";
import TravelSlideshow from "../components/TravelSlideshow";
import TripbudgetMark from "../components/TripbudgetMark";
import "../styles/Landing.css";

const features = [
  {
    Icon: PieChart,
    tint: "butter",
    title: "Budget envelopes",
    text: "Split each trip into flights, stays, food, transport and fun, with a buffer for surprises.",
  },
  {
    Icon: TrendingUp,
    tint: "sage",
    title: "Savings goals",
    text: "Pick a date and we'll work out the monthly amount. Connect a demo bank to track it.",
  },
  {
    Icon: CalendarDays,
    tint: "blue",
    title: "Day-by-day itinerary",
    text: "Plan every day and attach costs to plans, so the itinerary and budget never drift apart.",
  },
  {
    Icon: Users,
    tint: "coral",
    title: "Shared trips",
    text: "Invite friends, see the cost per person, and plan the same trip together.",
  },
];

const steps = [
  { title: "Add a trip", text: "Where, when and who's coming. That's enough to start." },
  { title: "Set the budget", text: "Use the suggested split or set your own caps for each envelope." },
  { title: "Save toward it", text: "Follow the monthly target and watch the trip go from planned to fully funded." },
];

function Landing() {
  return (
    <div className="landing">
      <header className="landing-nav">
        <Link to="/" className="landing-brand">
          <TripbudgetMark size={26} />
          <span className="tb-wide">Tripbudget</span>
        </Link>

        <nav className="landing-links" aria-label="Page sections">
          <a href="#features">Features</a>
          <a href="#how-it-works">How it works</a>
        </nav>

        <div className="landing-auth">
          <Link to="/login" className="tb-btn tb-btn-ghost">Sign in</Link>
          <Link to="/signup" className="tb-btn tb-btn-primary">Sign up</Link>
        </div>
      </header>

      <main>
        <section className="landing-hero">
          <div className="landing-hero-copy">
            <p className="landing-eyebrow">
              <span aria-hidden="true" />
              <span className="tb-kicker">Travel budget planner</span>
            </p>

            <h1 className="landing-title">
              <span className="tb-wide">Dream it.</span>
              <span className="tb-serif">budget it,</span>
              <span className="tb-wide landing-title-go">
                Go. <TripbudgetMark size={44} />
              </span>
            </h1>

            <p className="landing-lead">
              Give every trip a budget, split it into flights, stays and food, and know
              exactly how much to save each month before you take off.
            </p>

            <div className="landing-cta">
              <Link to="/signup" className="tb-btn tb-btn-accent landing-cta-main">
                Start planning, it's free
              </Link>
              <a href="#how-it-works" className="tb-btn tb-btn-outline">How it works</a>
            </div>
          </div>

          <LandingBudgetPreview />
        </section>

        {/* Wide photo banner */}
        <section className="landing-section landing-carousel-wrap" aria-label="Destinations">
          <div className="landing-carousel">
            <TravelSlideshow showArrows />
          </div>
        </section>

        <section id="features" className="landing-section">
          <h2 className="landing-h2">
            <span className="tb-wide">Everything a trip costs,</span>{" "}
            <span className="tb-serif">before you book it</span>
          </h2>

          <div className="landing-features">
            {features.map(({ Icon, tint, title, text }) => (
              <article key={title} className="tb-card landing-feature">
                <span className={`landing-feature-icon tint-${tint}`}>
                  <Icon size={22} strokeWidth={2} aria-hidden="true" />
                </span>
                <h3>{title}</h3>
                <p>{text}</p>
              </article>
            ))}
          </div>
        </section>

        <section id="how-it-works" className="landing-how">
          <div className="landing-section">
            <h2 className="landing-h2">
              <span className="tb-wide">From idea to</span>{" "}
              <span className="tb-serif">boarding pass</span>
            </h2>

            <ol className="landing-steps">
              {steps.map((step, index) => (
                <li key={step.title}>
                  <span className="tb-wide landing-step-number">0{index + 1}</span>
                  <h3>{step.title}</h3>
                  <p>{step.text}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section className="landing-section">
          <div className="landing-banner">
            <h2 className="landing-h2">
              <span className="tb-wide">Where are you</span>{" "}
              <span className="tb-serif">headed next?</span>
            </h2>
            <Link to="/signup" className="tb-btn landing-banner-btn">Sign up free</Link>
          </div>
        </section>
      </main>

      <footer className="landing-footer">
        <span>Tripbudget · CS 451 final project</span>
        <div>
          <Link to="/login">Sign in</Link>
          <Link to="/signup">Create account</Link>
        </div>
      </footer>
    </div>
  );
}

export default Landing;
