import { useEffect, useMemo, useState } from "react";

import {
  ArrowLeft, ArrowRight, CalendarDays, Check,
  CheckCircle2, ClipboardList, Copy, Eye, Link2, MapPin, MessageCircle,
  Plane, Share2, Users, UserPlus, Wallet,
} from "lucide-react";

import Sidebar from "../components/Sidebar";

import "../styles/Dashboard.css";

import "../styles/SharedTrips.css";

import { Link } from "react-router-dom";

import { getBudgetOverview, getDestinationPhoto, getProfile, getSavings } from "../api/api";

const ACTIVITY_META = {
  contribution: { label: "Contribution", Icon: Wallet },
  comment: { label: "Comment", Icon: MessageCircle },
  plan: { label: "Plan update", Icon: ClipboardList },
  member: { label: "Member update", Icon: UserPlus },
};

const money = (amount, currency = "USD") =>
  Number(amount ?? 0).toLocaleString("en-US", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  });

const day = (dateString) =>
  new Date(`${dateString}T12:00:00`).toLocaleDateString("en-US", { month: "short", day: "numeric" });

const dateRange = (trip) =>
  `${day(trip.startDate)} – ${day(trip.endDate)}, ${trip.endDate.slice(0, 4)}`;

const contributed = (trip) => Number(trip.savedAmount ?? 0);

const percentFunded = (trip) =>
  trip.budget > 0 ? Math.min(100, Math.round((contributed(trip) / trip.budget) * 100)) : 0;

function AvatarStack({ members }) {
  return (
    <div className="st-avatars" aria-label={`${members.length} trip members`}>
      {members.slice(0, 5).map((member, index) => (
        <span className={`st-avatar st-avatar-${index % 4}`} title={member.name} key={member.id}>
          {member.initials}
        </span>
      ))}
      {members.length > 5 && <span className="st-avatar st-avatar-extra">+{members.length - 5}</span>}
    </div>
  );
}

function Progress({ trip }) {
  const percent = percentFunded(trip);
  return (
    <div className="st-progress" role="progressbar" aria-label="Group budget contributed" aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100}>
      <span style={{ width: `${percent}%` }} />
    </div>
  );
}

function RouteDecoration() {
  return (
    <svg className="st-route-art" aria-hidden="true" fill="none" viewBox="0 0 410 260">
      <path d="M20 216C80 170 100 68 176 85c72 17 73 113 152 69 29-16 30-60 67-87" stroke="currentColor" strokeWidth="3" strokeDasharray="7 12" strokeLinecap="round" />
      <circle cx="20" cy="216" r="8" fill="currentColor" />
      <circle cx="176" cy="85" r="8" fill="currentColor" />
      <circle cx="328" cy="154" r="8" fill="currentColor" />
      <path d="m367 40 25 11-18 8-9 20-9-24Z" fill="currentColor" />
    </svg>
  );
}

function ScreenHeading({ eyebrow, title, description, onBack, action }) {
  return (
    <div className="st-screen-head">
      <div>
        <button className="st-back" type="button" onClick={onBack}><ArrowLeft size={17} /> Back</button>
        <p className="st-eyebrow">{eyebrow}</p>
        <h1 className="st-screen-title">{title}</h1>
        <p className="st-description">{description}</p>
      </div>
      {action}
    </div>
  );
}

function WelcomeScreen({ setView, sharedTripCount }) {
  return (
    <>
      <section className="st-hero">
        <RouteDecoration />

        <div className="st-hero-content">
          <h1>
            Trips are better with <span>your crew</span>
          </h1>
          <p>
            Bring your travel buddies into one shared space for
            budgets, plans, comments, and every update along the way.
          </p>
        </div>
      </section>

      <section className="st-choice-grid" aria-label="Shared trips options">

        <button
          className="st-choice st-panel"
          type="button"
          onClick={() => setView("share")}
        >
          <span className="st-choice-icon">
            <Share2 size={26} />
          </span>

          <span className="st-choice-title">
            Share an existing trip
          </span>

          <span className="st-choice-desc">
            Choose a trip and preview an invitation link for your travel crew.
          </span>

          <span className="st-choice-cta">
            Choose a trip <ArrowRight size={17} />
          </span>
        </button>

        {sharedTripCount > 0 && (
          <button
            className="st-choice st-panel"
            type="button"
            onClick={() => setView("trips")}
          >
            <span className="st-choice-icon st-choice-icon-alt">
              <Users size={27} />
            </span>

            <span className="st-choice-title">
              See shared trips
            </span>

            <span className="st-choice-desc">
              Explore members, contributions, conversations,
              and plan history in one place.
            </span>

            <span className="st-choice-cta">
              View {sharedTripCount} shared {sharedTripCount === 1 ? "trip" : "trips"}
              <ArrowRight size={17} />
            </span>
          </button>
        )}

      </section>
    </>
  );
}

function ShareScreen({ trips, selectedTrip, chooseTrip, onBack, copyPreview,
  copyState, onCreateSharedTrip, isShared, tripsLoading, tripsError }) {
  return (
    <section>
      <ScreenHeading
        eyebrow="INVITE YOUR CREW"
        title="Share an existing trip"
        description="Choose a trip you've already created, or create a new trip."
        onBack={onBack}
      />

      <div className="st-share-grid">
        <div className="st-panel st-select-panel">
          <h2>Choose a trip</h2>
          <p className="st-subtle">
            Select one of your saved trips to share.
          </p>

          <div className="st-pick-list">
            {tripsLoading && (
              <p className="st-subtle">Loading your trips...</p>
            )}

            {tripsError && (
              <p className="st-error" role="alert">
                Unable to load trips: {tripsError}
              </p>
            )}

            {!tripsLoading && !tripsError && trips.length === 0 && (
              <p className="st-subtle">
                You haven't created any trips yet.
              </p>
            )}

            {trips.map((trip) => {
              const isSelected =
                String(selectedTrip?.id) === String(trip.id);

              return (
                <button
                  key={trip.id}
                  className={`st-pick ${isSelected ? "is-selected" : ""}`}
                  type="button"
                  onClick={() => chooseTrip(trip.id)}
                  aria-pressed={isSelected}
                >
                  <span className="st-pick-icon">
                    <Plane size={19} />
                  </span>

                  <span className="st-pick-info">
                    <strong>{trip.name}</strong>
                    <small>
                      {trip.destination} · {trip.numberOfTravelers} travelers
                    </small>
                  </span>

                  {isSelected ? (
                    <CheckCircle2
                      className="st-pick-check"
                      size={22}
                    />
                  ) : (
                    <span className="st-radio-ring" />
                  )}
                </button>
              );
            })}

            {/* Always the last option */}
            <Link
              to="/create-trip"
              className="st-pick"
              style={{ textDecoration: "none" }}
            >
              <span className="st-pick-icon">+</span>

              <span className="st-pick-info">
                <strong>Create Trip</strong>
                <small>Start planning a new trip</small>
              </span>

              <ArrowRight size={20} />
            </Link>
          </div>
        </div>

        {selectedTrip && (
          <div className="st-panel st-invite-panel">
            <div className="st-invite-icon">
              <Link2 size={24} />
            </div>

            <p className="st-eyebrow">ONE LINK FOR YOUR CREW</p>
            <h2>Invitation preview</h2>

            <p className="st-subtle">
              This is a preview of a shareable link.
              Real invitations are not available yet.
            </p>

            <div
              className="st-link-preview"
              title={`${window.location.origin}/shared-trips?preview=${encodeURIComponent(selectedTrip.id)}`}
            >
              {window.location.host}/shared-trips?preview={selectedTrip.id}
            </div>

            <button
              className="st-button st-button-primary st-button-full"
              type="button"
              onClick={copyPreview}
            >
              {copyState === "copied" ? (
                <Check size={18} />
              ) : (
                <Copy size={18} />
              )}

              {copyState === "copied"
                ? "Preview link copied"
                : "Copy preview link"}
            </button>

            <button
              className="st-button st-button-outline st-button-full"
              type="button"
              onClick={onCreateSharedTrip}
              disabled={isShared}
            >
              <Users size={18} />
              {isShared
                ? "Already added to Shared Trips"
                : "Add to Shared Trips (preview)"}
            </button>

            {copyState === "error" && (
              <p className="st-error" role="status">
                Clipboard access is unavailable.
              </p>
            )}

            <p className="st-disclaimer">
              <Eye size={15} />
              Frontend preview only. Invitations and shared
              access are not implemented yet.
            </p>
          </div>
        )}
      </div>
    </section>
  );
}

function TripsScreen({ trips, onBack, onOpenTrip, onShare }) {
  return (
    <section>
      <ScreenHeading
        title="Shared trips"
        description="See saved trips, contributions, and planning updates in one place."
        onBack={onBack}
        action={<button type="button" className="st-button st-button-outline" onClick={onShare}><UserPlus size={18} /> Invite travelers</button>}
      />
      <div className="st-trip-list st-panel">
        {trips.map((trip) => (
          <button className="st-trip-row" type="button" key={trip.id} onClick={() => onOpenTrip(trip)}>
            <div className="st-trip-row-main">
              <div className="st-trip-name-row"><h2>{trip.title}</h2><span className={`st-status st-status-${trip.status}`}>{trip.status}</span></div>
              <p className="st-row-sub"><MapPin size={15} /> {trip.destination}<span aria-hidden="true">·</span><CalendarDays size={15} />{dateRange(trip)}</p>

              <div className="st-row-meta">
                <AvatarStack members={trip.members} />

                <span>{trip.members.length} members</span>
                <span className="st-dot" />

                {trip.budget != null ? (
                  <span>
                    <b>{money(contributed(trip), trip.currency)}</b>
                    {" "}of {money(trip.budget, trip.currency)} saved
                  </span>
                ) : (
                  <span>Budget not set</span>
                )}
              </div>
              <Progress trip={trip} />
            </div>
            <span className="st-open-link">Open trip <ArrowRight size={18} /></span>
          </button>
        ))}
      </div>
    </section>
  );
}

// Stable, readable contributor colors for the donut, legend, and activity feed.
const CONTRIBUTOR_COLORS = [
  "#218C83", "#527EC4", "#9269B5", "#C48A37", "#C36F81", "#5D8C55",
];

function ContributionDonut({ amount, budget, segments, label }) {
  const total = Number(budget) || 0;
  const percentage = total > 0
    ? Math.min(100, Math.round((amount / total) * 100))
    : 0;

  // Each segment fills its contributor's portion of the trip budget.
  // Any remaining circumference stays unfilled.
  let used = 0;
  const arcs = segments.map((segment) => {
    const available = Math.max(0, 100 - used);
    const length = total > 0
      ? Math.min(available, (segment.amount / total) * 100)
      : 0;
    const arc = { ...segment, length, offset: -used };
    used += length;
    return arc;
  });

  return (
    <div
      className="st-donut"
      role="img"
      aria-label={`${label}: ${percentage}% of the trip budget saved`}
    >
      <svg viewBox="0 0 42 42" aria-hidden="true">
        <circle
          cx="21" cy="21" r="16" fill="none"
          className="st-donut-track" strokeWidth="4.5"
        />
        {arcs.map((arc) => (
          <circle
            key={arc.id}
            cx="21" cy="21" r="16"
            fill="none"
            pathLength="100"
            stroke={arc.color}
            strokeWidth="4.5"
            strokeDasharray={`${arc.length} ${100 - arc.length}`}
            strokeDashoffset={arc.offset}
          />
        ))}
      </svg>
      <span className="st-donut-value">
        <strong>{percentage}%</strong>
        <small>SAVED</small>
      </span>
    </div>
  );
}

function DetailScreen({ trip, onBack, onShare }) {
  const [photo, setPhoto] = useState(null);
  const [savings, setSavings] = useState(null);
  const [savingsLoading, setSavingsLoading] = useState(true);
  const [savingsError, setSavingsError] = useState("");
  const [currentUser, setCurrentUser] = useState(null);
  const [selectedContributor, setSelectedContributor] = useState("all");

  useEffect(() => {
    let active = true;
    setSavingsLoading(true);
    setSavingsError("");
    setSavings(null);
    setSelectedContributor("all");

    getSavings(trip.id)
      .then((data) => {
        if (active) setSavings(data);
      })
      .catch((error) => {
        if (active) setSavingsError(error.message || "Could not load savings.");
      })
      .finally(() => {
        if (active) setSavingsLoading(false);
      });

    return () => { active = false; };
  }, [trip.id]);

  useEffect(() => {
    let active = true;
    getProfile()
      .then((profile) => {
        if (active) setCurrentUser(profile);
      })
      .catch(() => {});
    return () => { active = false; };
  }, []);

  const ownerId = "owner";
  const ownerName = currentUser?.name || "You";

  // Current savings records belong to the trip owner. The backend does
  // not yet expose per-member contribution attribution. If contributor
  // fields are added later, this UI will group those records separately.
  const savingsEntries = useMemo(() => {
    if (!savings) return [];

    const history = (savings.contributions ?? []).map((entry) => {
      const recordedId = entry.contributorId;
      const belongsToOwner = recordedId == null || (
        currentUser?.id != null && String(recordedId) === String(currentUser.id)
      );
      return {
        id: `savings-${entry.id}`,
        contributorId: belongsToOwner ? ownerId : String(recordedId),
        contributorName: belongsToOwner
          ? ownerName
          : (entry.contributorName || `Traveler ${recordedId}`),
        amount: Number(entry.amount) || 0,
        date: entry.date || null,
        description: entry.description || "Savings contribution",
        initial: false,
      };
    });

    const initial = Number(savings.initialSavedAmount) || 0;
    if (initial > 0) {
      history.push({
        id: "initial-savings",
        contributorId: ownerId,
        contributorName: ownerName,
        amount: initial,
        date: null,
        description: "Already saved when the savings goal was created",
        initial: true,
      });
    }
    return history;
  }, [savings, currentUser, ownerId, ownerName]);

  const contributors = useMemo(() => {
    const groups = new Map();
    for (const entry of savingsEntries) {
      const existing = groups.get(entry.contributorId);
      if (existing) {
        existing.amount += entry.amount;
      } else {
        groups.set(entry.contributorId, {
          id: entry.contributorId,
          name: entry.contributorName,
          amount: entry.amount,
        });
      }
    }
    return [...groups.values()]
      .sort((a, b) => a.name.localeCompare(b.name))
      .map((person, index) => ({
        ...person,
        color: CONTRIBUTOR_COLORS[index % CONTRIBUTOR_COLORS.length],
      }));
  }, [savingsEntries]);

  const selectedPerson = contributors.find((p) => p.id === selectedContributor);
  const filteredEntries = savingsEntries.filter((entry) =>
    selectedContributor === "all" || entry.contributorId === selectedContributor
  );
  const savedTotal = savings
    ? Number(savings.savedAmount ?? 0)
    : Number(trip.savedAmount ?? 0);
  const displayedAmount = selectedContributor === "all"
    ? savedTotal
    : (selectedPerson?.amount ?? 0);
  const chartSegments = selectedPerson
    ? [selectedPerson]
    : contributors.length > 0
      ? contributors
      : displayedAmount > 0
        ? [{ id: "unattributed", amount: displayedAmount, color: "#9BA9B5" }]
        : [];

  // Savings entries are real backend activity. Additional collaboration
  // activity can join this feed once the membership system supports it.
  const activityEvents = [
    ...savingsEntries.map((entry) => ({
      id: entry.id,
      type: "contribution",
      actorId: entry.contributorId,
      actor: entry.contributorName,
      summary: entry.initial ? "Starting savings" : "Savings added",
      details: entry.description,
      amount: entry.amount,
      date: entry.date,
    })),
    ...(trip.activities ?? []),
  ]
    .filter((event) =>
      selectedContributor === "all" ||
      String(event.actorId ?? event.contributorId ?? "") === selectedContributor
    )
    .sort((a, b) => (b.date || "").localeCompare(a.date || ""));

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
      .catch((error) => {
        if (!cancelled) {
          console.warn("Destination photo unavailable:", error);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [trip.destination]);

  return (
    <section className="st-detail-screen">
      <button className="st-back" type="button" onClick={onBack}><ArrowLeft size={17} /> All shared trips</button>

      <header className="st-detail-hero">
        {photo?.imageUrl && (
          <img
            className="st-detail-hero-photo"
            src={photo.imageUrl}
            alt=""
            onError={() => setPhoto(null)}
          />
        )}

        <div className="st-detail-hero-content">
          <div className="st-detail-labels"><span className="st-status-inverse">{trip.status}</span><span><CalendarDays size={15} /> {dateRange(trip)}</span></div>
          <h1>{trip.title}</h1>
          <p><MapPin size={17} />{trip.destination}</p>
          <div className="st-detail-crew"><AvatarStack members={trip.members} /><span>{trip.members.length} travelers planning together</span><button type="button" onClick={onShare}><UserPlus size={17} /> Invite more</button></div>
        </div>

        {photo?.photographerUrl && photo?.unsplashUrl && (
          <p className="st-detail-photo-credit">
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
          </p>
        )}

      </header>

      <div className="st-stat-grid">
        <article className="st-panel st-contribution-card">
          <ContributionDonut
            amount={displayedAmount}
            budget={trip.budget}
            segments={chartSegments}
            label={selectedPerson?.name || "All contributors"}
          />

          <div className="st-contribution-info">
            <div className="st-contribution-topline">
              <p className="st-eyebrow">GROUP CONTRIBUTION</p>
              <label className="st-contributor-filter">
                <span>Traveler</span>
                <select
                  value={selectedContributor}
                  onChange={(event) => setSelectedContributor(event.target.value)}
                  aria-label="Filter contributions by traveler"
                  disabled={savingsLoading || contributors.length === 0}
                >
                  <option value="all">All travelers</option>
                  {contributors.map((person) => (
                    <option key={person.id} value={person.id}>
                      {person.name}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            {trip.budget != null ? (
              <h2>
                {money(displayedAmount, trip.currency)}
                <span> of {money(trip.budget, trip.currency)}</span>
              </h2>
            ) : (
              <h2>Budget not set</h2>
            )}

            <p>
              {selectedPerson
                ? `Savings recorded for ${selectedPerson.name}.`
                : "Savings recorded in Budget & Savings."}
            </p>

            {savingsLoading && <p role="status">Loading contribution history...</p>}
            {savingsError && (
              <p className="st-error" role="alert">{savingsError}</p>
            )}

            {contributors.length > 0 && (
              <div className="st-contributor-legend" aria-label="Contribution totals by traveler">
                {contributors
                  .filter((person) => selectedContributor === "all" || person.id === selectedContributor)
                  .map((person) => (
                    <span className="st-contributor-legend-item" key={person.id}>
                      <span className="st-contributor-swatch" style={{ backgroundColor: person.color }} />
                      {person.name}: {money(person.amount, trip.currency)}
                    </span>
                  ))}
              </div>
            )}
          </div>
        </article>
      </div>

      <div className="st-detail-grid">
        <div className="st-timeline-column">
          <div className="st-timeline-header">
            <div>
              <p className="st-eyebrow">SAVINGS & ACTIVITY</p>
              <h2>What your crew is doing</h2>
            </div>
            <span>Newest first</span>
          </div>

          <div className="st-activity-list">
            {!savingsLoading && !savingsError && activityEvents.length === 0 && (
              <p className="st-subtle">No recorded contributions or activity for this selection.</p>
            )}
            {activityEvents.map((activity) => {
              const meta = ACTIVITY_META[activity.type] || ACTIVITY_META.contribution;
              const Icon = meta.Icon;
              const color = contributors.find((p) => p.id === String(activity.actorId))?.color;
              return (
                <article className={`st-activity st-activity-${activity.type}`} key={activity.id}>
                  <div
                    className="st-activity-symbol"
                    style={color ? { color, borderColor: color } : undefined}
                  >
                    <Icon size={20} />
                  </div>
                  <div
                    className="st-panel st-activity-body"
                    style={color ? { borderLeft: `3px solid ${color}` } : undefined}
                  >
                    <div className="st-activity-top">
                      <div>
                        <p className="st-eyebrow">{meta.label}</p>
                        <h3>{activity.summary}</h3>
                      </div>
                      <time dateTime={activity.date || undefined}>
                        {activity.date
                          ? new Date(`${activity.date}T12:00:00`).toLocaleDateString("en-US", { month: "short", day: "numeric" })
                          : "Starting balance"}
                      </time>
                    </div>
                    <p className="st-activity-details">{activity.details}</p>
                    <div className="st-activity-footer">
                      by {activity.actor}
                      {activity.amount != null && (
                        <strong>+{money(activity.amount, trip.currency)}</strong>
                      )}
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        </div>

        <aside className="st-detail-aside" aria-label="Shared trip information">
          <section className="st-panel st-aside-panel">
            <header><h2>Trip members</h2><span className="st-small-pill">{trip.members.length}</span></header>
            <div className="st-member-list">
              {trip.members.map((member, index) => (
                <div className="st-member" key={member.id}>
                  <span className={`st-avatar st-avatar-${index % 4}`}>{member.initials}</span>
                  <div className="st-member-info"><strong>{member.name}</strong><span>{member.role} · {member.rsvp}</span></div>
                  <strong className="st-member-money">{money(member.contribution)}</strong>
                </div>
              ))}
            </div>
            <button className="st-aside-action" type="button" onClick={onShare}><UserPlus size={17} /> Invite more members</button>
          </section>
        </aside>
      </div>
    </section>
  );
}

export default function SharedTrips() {
  const [view, setView] = useState("welcome");

  // Trips loaded from the existing backend
  const [savedTrips, setSavedTrips] = useState([]);
  const [tripsLoading, setTripsLoading] = useState(true);
  const [tripsError, setTripsError] = useState("");

  // Temporary frontend-only shared trip selections
  const [sharedTripIds, setSharedTripIds] = useState(() => {
    try {
      const stored = JSON.parse(
        localStorage.getItem("sharedTripPreviewIds") || "[]"
      );
      return Array.isArray(stored) ? stored : [];
    } catch {
      return [];
    }
  });

  const [selectedTripId, setSelectedTripId] = useState(null);
  const [copyState, setCopyState] = useState("idle");

  // Load trips already created by the logged-in user
  useEffect(() => {
    let active = true;

    getBudgetOverview()
      .then((data) => {
        if (active) {
          setSavedTrips(Array.isArray(data) ? data : []);
        }
      })
      .catch((error) => {
        if (active) {
          setTripsError(error.message);
        }
      })
      .finally(() => {
        if (active) {
          setTripsLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  // Save preview selections locally
  useEffect(() => {
    localStorage.setItem(
      "sharedTripPreviewIds",
      JSON.stringify(sharedTripIds)
    );
  }, [sharedTripIds]);

  // Adapt real trip fields to the existing UI
  const availableTrips = useMemo(
    () =>
      savedTrips.map((trip) => ({
        ...trip,
        id: trip.tripId,
        title: trip.name,
        status: "planning",

        // Real budget and savings data
        budget:
          trip.budgetTotal == null
            ? null
            : Number(trip.budgetTotal),

        savedAmount: Number(trip.savedAmount ?? 0),
        currency: trip.currency || "USD",

        // Collaboration backend not implemented yet
        members: [],
        plans: [],
        activities: []
      })),
    [savedTrips]
  );

  // Currently selected real trip
  const selectedTrip = useMemo(
    () =>
      availableTrips.find(
        (trip) => String(trip.id) === String(selectedTripId)
      ) || availableTrips[0] || null,
    [availableTrips, selectedTripId]
  );

  // Only trips the user has selected for sharing
  const sharedTrips = availableTrips.filter((trip) =>
    sharedTripIds.some(
      (id) => String(id) === String(trip.id)
    )
  );

  function navigate(nextView) {
    setView(nextView);
    setCopyState("idle");
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function openTrip(trip) {
    setSelectedTripId(trip.id);
    navigate("detail");
  }

  async function copyPreview() {
    if (!selectedTrip) return;

    const link =
      `${window.location.origin}/shared-trips?preview=` +
      encodeURIComponent(selectedTrip.id);

    try {
      if (!navigator.clipboard?.writeText) {
        throw new Error("Clipboard unavailable");
      }

      await navigator.clipboard.writeText(link);
      setCopyState("copied");
    } catch {
      setCopyState("error");
    }
  }

  function createSharedTrip() {
    if (!selectedTrip) return;

    const id = String(selectedTrip.id);

    setSharedTripIds((previous) =>
      previous.some((storedId) => String(storedId) === id)
        ? previous
        : [...previous, id]
    );

    navigate("welcome");
  }

  return (
    <div className="dashboard-page shared-trips-page">
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      <main className="dashboard-main shared-trips-main">

        {view === "welcome" && (
          <WelcomeScreen
            setView={navigate}
            sharedTripCount={sharedTrips.length}
          />
        )}

        {view === "share" && (
          <ShareScreen
            trips={availableTrips}
            selectedTrip={selectedTrip}
            chooseTrip={(id) => {
              setSelectedTripId(id);
              setCopyState("idle");
            }}
            onBack={() => navigate("welcome")}
            copyPreview={copyPreview}
            copyState={copyState}
            onCreateSharedTrip={createSharedTrip}
            isShared={
              selectedTrip
                ? sharedTripIds.some(
                  (id) =>
                    String(id) === String(selectedTrip.id)
                )
                : false
            }
            tripsLoading={tripsLoading}
            tripsError={tripsError}
          />
        )}

        {view === "trips" && (
          <TripsScreen
            trips={sharedTrips}
            onBack={() => navigate("welcome")}
            onOpenTrip={openTrip}
            onShare={() => navigate("share")}
          />
        )}

        {view === "detail" && selectedTrip && (
          <DetailScreen
            key={selectedTrip.id}
            trip={selectedTrip}
            onBack={() => navigate("trips")}
            onShare={() => navigate("share")}
          />
        )}

      </main>
    </div>
  );
}
