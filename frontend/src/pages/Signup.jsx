
import { useState } from "react";
import { Link } from "react-router-dom";
import "../styles/SignUp.css";

function SignUp() {
  const [formData, setFormData] = useState({
    firstName: "",
    lastName: "",
    email: "",
    password: "",
    confirmPassword: ""
  });

  const [error, setError] = useState("");

  const handleChange = (e) => {
    const { name, value } = e.target;

    setFormData((prev) => ({
      ...prev,
      [name]: value
    }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    setError("");

    if (formData.password !== formData.confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    // Backend connection will go here.
    // Do not save passwords in localStorage.

    console.log("Sign up form validated.");

    setError("Registration is not connected to the backend yet.");
  };

  return (
    <div className="signup-page">

      {/* Left side */}
      <section className="signup-left"></section>

      {/* Right side - Sign up form */}
      <section className="signup-right">
        <div className="signup-container">

          <p className="welcome-text">GET STARTED</p>
          <h1>Create your account</h1>

          <p className="signup-subtitle">
            Start planning your next unforgettable adventure.
          </p>

          <form className="signup-form" onSubmit={handleSubmit}>

            
{/* First Name and Last Name Side by Side */}
<div className="name-row">

            {/* First Name */}
            <div className="name-field">
                <label htmlFor="firstName">First name</label>
                <input
                id="firstName"
                name="firstName"
                type="text"
                placeholder="Enter your first name"
                value={formData.firstName}
                onChange={handleChange}
                autoComplete="given-name"
                required
                />
            </div>

            {/* Last Name */}
            <div className="name-field">
                <label htmlFor="lastName">Last name</label>
                <input
                id="lastName"
                name="lastName"
                type="text"
                placeholder="Enter your last name"
                value={formData.lastName}
                onChange={handleChange}
                autoComplete="family-name"
                required
                />
            </div>

            </div>


            {/* Email */}
            <label htmlFor="email">Email address</label>
            <input
              id="email"
              name="email"
              type="email"
              placeholder="example@example.com"
              value={formData.email}
              onChange={handleChange}
              autoComplete="email"
              required
            />

            {/* Password */}
            <label htmlFor="password">Password</label>
            <input
              id="password"
              name="password"
              type="password"
              placeholder="Create a password"
              value={formData.password}
              onChange={handleChange}
              autoComplete="new-password"
              minLength={8}
              required
            />

            {/* Confirm Password */}
            <label htmlFor="confirmPassword">
              Confirm password
            </label>
            <input
              id="confirmPassword"
              name="confirmPassword"
              type="password"
              placeholder="Confirm your password"
              value={formData.confirmPassword}
              onChange={handleChange}
              autoComplete="new-password"
              required
            />

            {error && <p role="alert">{error}</p>}

            <button type="submit" className="signup-button">
              Create Account
            </button>

          </form>

          <p className="signup-login-text">
            Already have an account?{" "}
            <Link to="/">Sign In</Link>
          </p>

        </div>
      </section>
    </div>
  );
}

export default SignUp;
