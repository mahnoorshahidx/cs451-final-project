
import "../styles/Login.css";
import { useEffect, useRef, useState } from "react";
import { ArrowLeft, MailCheck } from "lucide-react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { login, resendLoginCode, verifyLoginCode } from "../api/api";
import { clearSession, saveSession } from "../api/client";
import TravelSlideshow from "../components/TravelSlideshow";
import AuthTopBar from "../components/AuthTopBar";
import PasswordInput from "../components/PasswordInput";

function Login() {
    const navigate = useNavigate();
    const location = useLocation();
    const justRegistered = location.state?.registered;

    // Return to the invitation after successful authentication
    const requestedPath =
    location.state?.returnTo ??
    new URLSearchParams(location.search).get("returnTo");

    const returnTo =
        typeof requestedPath === "string" &&
            /^\/invite\/[A-Za-z0-9_-]+$/.test(requestedPath)
            ? requestedPath
            : "/dashboard";

    // Inputs are now controlled so we can read what the user typed
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    // Two-factor step: set once the password is right and a code was sent
    const [challenge, setChallenge] = useState(null); // { challengeId, sentTo, devCode }
    const [code, setCode] = useState("");
    const [resendAt, setResendAt] = useState(0); // ms timestamp when "Resend" unlocks
    const [now, setNow] = useState(() => Date.now());
    const [notice, setNotice] = useState("");
    const codeRef = useRef(null);

    // Tick once a second while waiting to resend
    useEffect(() => {
        if (!challenge) return undefined;
        const timer = setInterval(() => setNow(Date.now()), 1000);
        return () => clearInterval(timer);
    }, [challenge]);

    useEffect(() => {
        if (challenge) codeRef.current?.focus();
    }, [challenge]);

    const resendIn = Math.max(0, Math.ceil((resendAt - now) / 1000));

    function startChallenge(data) {
        setChallenge({ challengeId: data.challengeId, sentTo: data.sentTo, devCode: data.devCode || "" });
        setResendAt(Date.now() + (data.resendAvailableInSeconds ?? 30) * 1000);
        setNow(Date.now());
        setCode("");
    }

    // Back to email + password (expired code, too many tries, or a different account)
    function backToPassword(message = "") {
        setChallenge(null);
        setCode("");
        setNotice("");
        setError(message);
    }

    // Now logs in through the backend and saves the token + user's name.
    // Before, it just navigated to /dashboard, so later API calls got 401s (SCRUM-30).
    const handleLogin = async (e) => {
        e.preventDefault();
        setError("");
        setLoading(true);
        clearSession();

        try {
            const data = await login(email, password);
            if (data.twoFactorRequired) {
                startChallenge(data);
            } else {
                saveSession(data); // { token, name, email }
                navigate(returnTo, { replace: true });
            }
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    const handleVerify = async (e) => {
        e.preventDefault();
        setError("");
        setNotice("");
        if (!/^\d{6}$/.test(code)) {
            setError("Enter the 6-digit code.");
            return;
        }
        setLoading(true);
        try {
            const data = await verifyLoginCode(challenge.challengeId, code);
            saveSession(data);
            navigate(returnTo, { replace: true });
        } catch (err) {
            if (err.status === 410) {
                backToPassword(err.message);
            } else {
                setError(err.message);
                setCode("");
                codeRef.current?.focus();
            }
        } finally {
            setLoading(false);
        }
    };

    const handleResend = async () => {
        setError("");
        setNotice("");
        setLoading(true);
        try {
            const data = await resendLoginCode(challenge.challengeId);
            startChallenge(data);
            setNotice(`New code sent to ${data.sentTo}.`);
        } catch (err) {
            if (err.status === 410) backToPassword(err.message);
            else setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="login-page">

            {/* Left side - Rotating travel photos */}
            <section className="login-left">
                <TravelSlideshow />
            </section>

            {/* Right side - Login form */}
            <section className="login-right">
                <AuthTopBar />

                <div className="login-container auth-card">

                    {challenge ? (
                        <>
                            <span className="login-code-icon" aria-hidden="true"><MailCheck size={24} /></span>
                            <p className="welcome-text tb-kicker">Two-step sign-in</p>
                            <h1 className="tb-wide">Check your email</h1>
                            <p className="login-subtitle">
                                We sent a 6-digit code to <strong>{challenge.sentTo}</strong>. It works for 10 minutes.
                            </p>

                            {challenge.devCode && (
                                <p className="login-dev-code">
                                    Demo mode: your code is <strong>{challenge.devCode}</strong>
                                </p>
                            )}

                            <form className="login-form" onSubmit={handleVerify} noValidate>
                                <label htmlFor="login-code">Verification code</label>
                                <input
                                    id="login-code"
                                    ref={codeRef}
                                    className="login-code-input"
                                    inputMode="numeric"
                                    autoComplete="one-time-code"
                                    maxLength={6}
                                    placeholder="000000"
                                    value={code}
                                    onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                                    aria-describedby="login-code-help"
                                />
                                <p id="login-code-help" className="login-code-help">
                                    Didn't get it? Check spam, or{" "}
                                    <button type="button" className="login-link-button" onClick={handleResend} disabled={loading || resendIn > 0}>
                                        {resendIn > 0 ? `resend in ${resendIn}s` : "send a new code"}
                                    </button>
                                    .
                                </p>

                                {notice && <p className="success-text" role="status">{notice}</p>}
                                {error && <p className="error-text" role="alert">{error}</p>}

                                <button type="submit" className="login-button" disabled={loading || code.length !== 6}>
                                    {loading ? "Checking..." : "Verify and sign in"}
                                </button>
                            </form>

                            <button type="button" className="login-back-button" onClick={() => backToPassword()}>
                                <ArrowLeft size={16} aria-hidden="true" /> Use a different account
                            </button>
                        </>
                    ) : (
                        <>
                        <p className="welcome-text tb-kicker">Welcome back</p>
                        <h1 className="tb-wide">Sign in</h1>
                        <p className="login-subtitle">
                            Pick up where you left off with your trips.
                        </p>

                        {/* Shown after signing up */}
                        {justRegistered && (
                            <p className="success-text">Account created. Sign in to continue.</p>
                        )}

                        <form className="login-form" onSubmit={handleLogin}>

                            <label htmlFor="email">Email address</label>
                            <input
                                id="email"
                                type="email"
                                autoComplete="email"
                                placeholder="example@example.com"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                required
                            />

                            <div className="password-header">
                                <label htmlFor="password">Password</label>
                                <a href="/forgot-password">Forgot password?</a>
                            </div>

                            <PasswordInput
                                id="password"
                                autoComplete="current-password"
                                placeholder="Enter password"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                required
                            />

                            {/* Login errors from the backend */}
                            {error && <p className="error-text">{error}</p>}

                            <button type="submit" className="login-button" disabled={loading}>
                                {loading ? "Signing in..." : "Sign in"}
                            </button>

                            </form>

                            <p className="signup-text">
                                New to Tripbudget? <Link
                                    to="/signup"
                                    state={{ returnTo }}
                                >
                                    Create an account
                                </Link>
                            </p>

                        </>
                    )}

                </div>
            </section>
        </div>
    );
}

export default Login;
