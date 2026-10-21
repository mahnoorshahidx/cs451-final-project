
import "../styles/Login.css";
import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { login } from "../api/api";
import { clearSession, saveSession } from "../api/client";

function Login() {
    const navigate = useNavigate();
    const location = useLocation();
    const justRegistered = location.state?.registered;

    // Inputs are now controlled so we can read what the user typed
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    // Now logs in through the backend and saves the token + user's name.
    // Before, it just navigated to /dashboard, so later API calls got 401s (SCRUM-30).
    const handleLogin = async (e) => {
        e.preventDefault();
        setError("");
        setLoading(true);
        clearSession();

        try {
            const data = await login(email, password); // { token, name, email }
            saveSession(data);
            navigate("/dashboard");
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="login-page">

            {/* Left side - Travel image */}
            <section className="login-left">
            </section>

            {/* Right side - Login form */}
            <section className="login-right">
                <div className="login-container">

                    <p className="welcome-text">WELCOME BACK</p>
                    <h1>Sign in to your account</h1>
                    <p className="login-subtitle">
                        Continue planning your next unforgettable adventure.
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
                            placeholder="example@example.com"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            required
                        />

                        <div className="password-header">
                            <label htmlFor="password">Password</label>
                            <a href="/forgot-password">Forgot password?</a>
                        </div>

                        <input
                            id="password"
                            type="password"
                            placeholder="Enter password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            required
                        />

                        {/* Login errors from the backend */}
                        {error && <p className="error-text">{error}</p>}

                        <button type="submit" className="login-button" disabled={loading}>
                            {loading ? "Signing in..." : "Sign In"}
                        </button>

                    </form>

                    <div className="login-divider">
                        <span>or</span>
                    </div>

                    <button type="button" className="google-button">
                        Continue with Google
                    </button>

                    <p className="signup-text">
                        New to TripBudget? <Link to="/signup">Create an account</Link>
                    </p>

                </div>
            </section>
        </div>
    );
}

export default Login;
