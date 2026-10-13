import "../styles/Login.css";
import { useNavigate } from "react-router-dom";

function Login() {
    const navigate = useNavigate();

    const handleLogin = (e) => {
    e.preventDefault();
    navigate("/dashboard");

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

            <form className="login-form" onSubmit={handleLogin}>

            <label htmlFor="email">Email address</label>
            <input
                id="email"
                type="email"
                placeholder="example@example.com"
            />

            <div className="password-header">
                <label htmlFor="password">Password</label>
                <a href="/forgot-password">Forgot password?</a>
            </div>

            <input
                id="password"
                type="password"
                placeholder="Enter password"
            />

            <button type="submit" className="login-button">
                Sign In
            </button>

            </form>

            <div className="login-divider">
            <span>or</span>
            </div>

            <button type="button" className="google-button">
            Continue with Google
            </button>

            <p className="signup-text">
            New to TripBudget?{" "}
            <a href="/signup">Create an account</a>
            </p>

        </div>
        </section>
        </div>

  );
}export default Login;