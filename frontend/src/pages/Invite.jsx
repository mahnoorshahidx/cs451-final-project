
import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  Mail,
  MapPin,
  Users,
  Clock,
  AlertCircle,
} from "lucide-react";

import {
  previewInvitation,
  acceptInvitation,
  declineInvitationByToken,
} from "../api/api";

import { getToken, getCurrentUser } from "../api/client";
import TripbudgetMark from "../components/TripbudgetMark";
import "../styles/Invite.css";

function Invite() {
  const { token } = useParams();
  const navigate = useNavigate();

  const [invitation, setInvitation] = useState(null);
  const [declining, setDeclining] = useState(false);
  const [loading, setLoading] = useState(true);
  const [accepting, setAccepting] = useState(false);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");

  const signedIn = Boolean(getToken());
  const currentUser = getCurrentUser();
  const invitePath = `/invite/${encodeURIComponent(token)}`;

  // Retrieve invitation details when the page opens
  useEffect(() => {
    let cancelled = false;

    async function loadInvitation() {
      setLoading(true);
      setError("");
      setActionError("");
      setInvitation(null);

      try {
        const data = await previewInvitation(token);

        if (!cancelled) {
          setInvitation(data);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err.message || "Unable to load invitation.");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    loadInvitation();

    return () => {
      cancelled = true;
    };
  }, [token]);

  // Accept the invitation
  async function handleAccept() {
    if (accepting || declining) return;
    if (!signedIn) {
      navigate("/login", {
        state: { returnTo: invitePath },
      });
      return;
    }

    setAccepting(true);
    setActionError("");

    try {
      const result = await acceptInvitation(token);

      navigate(`/shared-trips?tripId=${result.tripId}`, {
        replace: true,
      });
    } catch (err) {
      setActionError(
        err.message || "Unable to accept invitation."
      );
    } finally {
      setAccepting(false);
    }
  }


  // Decline the invitation
  async function handleDecline() {
    if (!signedIn || accepting || declining) return;

    const confirmed = window.confirm(
      "Are you sure you want to decline this invitation? " +
      "This invitation link will no longer be usable."
    );

    if (!confirmed) return;

    setDeclining(true);
    setActionError("");

    try {
      await declineInvitationByToken(token);
      navigate("/dashboard", { replace: true });
    } catch (err) {
      setActionError(
        err.message || "Unable to decline invitation."
      );
    } finally {
      setDeclining(false);
    }
  }


  return (
    <main className="invite-page">

      <Link to="/" className="invite-brand">
        <TripbudgetMark />
        <span className="tb-wide">Tripbudget</span>
      </Link>

      <section className="tb-card invite-card">

        <header className="invite-header">
          <Mail size={42} aria-hidden="true" />
          <span className="tb-kicker">Plan together</span>
          <h1 className="tb-wide">You're invited!</h1>
        </header>

        <div className="invite-content">

          {loading ? (
            <p role="status">Loading invitation...</p>
          ) : error ? (
            <div className="invite-error" role="alert">
              <AlertCircle size={32} />

              <h2>Invitation unavailable</h2>

              <p>{error}</p>

              <Link
                to={signedIn ? "/dashboard" : "/"}
                className="tb-btn tb-btn-primary"
              >
                {signedIn ? "Go to dashboard" : "Back to home"}
              </Link>
            </div>
          ) : invitation && (
            <>
              <p className="invite-intro">
                <strong>{invitation.invitedBy}</strong>{" "}
                invited you to plan a trip together.
              </p>

              <div className="invite-trip">
                <span className="tb-kicker">
                  Your next adventure
                </span>

                <h2 className="tb-wide">
                  {invitation.tripName}
                </h2>

                <p>
                  <MapPin size={18} />
                  {invitation.destination}
                </p>
              </div>

              <div className="invite-detail">
                <Users size={22} />

                <div>
                  <strong>
                    {invitation.role === "EDITOR"
                      ? "Editor access"
                      : "Viewer access"}
                  </strong>

                  <p>
                    {invitation.role === "EDITOR"
                      ? "View and help plan the trip."
                      : "View trip details and plans."}
                  </p>
                </div>
              </div>

              <div className="invite-detail">
                <Clock size={22} />

                <div>
                  <strong>Invitation expires</strong>

                  <p>
                    {new Date(
                      invitation.expiresAt
                    ).toLocaleDateString()}
                  </p>
                </div>
              </div>

              {signedIn && currentUser && (
                <p className="invite-account">
                  Signed in as {currentUser.email}
                </p>
              )}

              {actionError && (
                <p className="invite-error-message" role="alert">
                  {actionError}
                </p>
              )}

              <button
                type="button"
                className="tb-btn tb-btn-accent"
                onClick={handleAccept}
                disabled={accepting || declining}
              >
                {accepting
                  ? "Joining trip..."
                  : signedIn
                    ? "Accept invitation"
                    : "Sign in to accept"}
              </button>

              {signedIn && (
                <button
                  type="button"
                  className="tb-btn invite-decline-btn"
                  onClick={handleDecline}
                  disabled={accepting || declining}
                >
                  {declining
                    ? "Declining invitation..."
                    : "Decline invitation"}
                </button>
              )}

              {!signedIn && (
                <p className="invite-signup">
                  Don't have an account?{" "}
                  <Link
                    to="/signup"
                    state={{ returnTo: invitePath }}
                  >
                    Create an account
                  </Link>
                </p>
              )}

              {signedIn && (
                <Link
                  to="/login"
                  state={{ returnTo: invitePath }}
                  className="invite-switch-account"
                >
                  Use a different account
                </Link>
              )}
            </>
          )}

        </div>
      </section>
    </main>
  );
}

export default Invite;
