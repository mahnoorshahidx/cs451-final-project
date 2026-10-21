
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { register } from "../api/api";
import "../styles/SignUp.css";

function Signup() {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    firstName: "",
    lastName: "",
    email: "",
    password: "",
    confirmPassword: "",
    phoneNumber: "",
    dateOfBirth: "",
    address: "",
  });

  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const [loading, setLoading] = useState(false);

  const handleChange = (e) => {
    const { name, value } = e.target;

    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setFieldErrors({});

    if (formData.password !== formData.confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    setLoading(true);

    try {
      const body = {
        name: `${formData.firstName.trim()} ${formData.lastName.trim()}`,
        email: formData.email,
        password: formData.password,
        phoneNumber: formData.phoneNumber,
        dateOfBirth: formData.dateOfBirth,
        address: formData.address,
      };

      await register(body);

      navigate("/", { state: { registered: true } });
    } catch (err) {
      setFieldErrors(err.fieldErrors || {});
      setError(err.message || "Registration failed.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="signup-page">

      {/* Left side */}
      <section className="signup-left"></section>

      {/* Right side */}
      <section className="signup-right">
        <div className="signup-container">

          <p className="welcome-text">GET STARTED</p>
          <h1>Create your account</h1>

          <p className="signup-subtitle">
            Start planning your next unforgettable adventure.
          </p>

          <form className="signup-form" onSubmit={handleSubmit}>

            {/* First and Last Name */}
            <div className="name-row">

              <div className="name-field">
                <label htmlFor="firstName">First name</label>
                <input
                  id="firstName"
                  name="firstName"
                  type="text"
                  placeholder="Enter your first name"
                  value={formData.firstName}
                  onChange={handleChange}
                  required
                />
              </div>

              <div className="name-field">
                <label htmlFor="lastName">Last name</label>
                <input
                  id="lastName"
                  name="lastName"
                  type="text"
                  placeholder="Enter your last name"
                  value={formData.lastName}
                  onChange={handleChange}
                  required
                />
              </div>

            </div>

            {fieldErrors.name && (
              <p className="error-text">{fieldErrors.name}</p>
            )}

            <label htmlFor="email">Email address</label>
            <input
              id="email"
              name="email"
              type="email"
              placeholder="example@example.com"
              value={formData.email}
              onChange={handleChange}
              required
            />
            {fieldErrors.email && (
              <p className="error-text">{fieldErrors.email}</p>
            )}

            <label htmlFor="password">Password</label>
            <input
              id="password"
              name="password"
              type="password"
              placeholder="Create a password"
              value={formData.password}
              onChange={handleChange}
              minLength={8}
              required
            />
            {fieldErrors.password && (
              <p className="error-text">{fieldErrors.password}</p>
            )}

            <label htmlFor="confirmPassword">Confirm password</label>
            <input
              id="confirmPassword"
              name="confirmPassword"
              type="password"
              placeholder="Confirm your password"
              value={formData.confirmPassword}
              onChange={handleChange}
              required
            />

            <label htmlFor="phoneNumber">Phone number</label>
            <input
              id="phoneNumber"
              name="phoneNumber"
              type="tel"
              placeholder="555-123-4567"
              value={formData.phoneNumber}
              onChange={handleChange}
            />
            {fieldErrors.phoneNumber && (
              <p className="error-text">{fieldErrors.phoneNumber}</p>
            )}

            <label htmlFor="dateOfBirth">Date of birth</label>
            <input
              id="dateOfBirth"
              name="dateOfBirth"
              type="date"
              value={formData.dateOfBirth}
              onChange={handleChange}
            />
            {fieldErrors.dateOfBirth && (
              <p className="error-text">{fieldErrors.dateOfBirth}</p>
            )}

            <label htmlFor="address">Address</label>
            <input
              id="address"
              name="address"
              type="text"
              placeholder="Enter your address"
              value={formData.address}
              onChange={handleChange}
            />
            {fieldErrors.address && (
              <p className="error-text">{fieldErrors.address}</p>
            )}

            {error && <p className="error-text" role="alert">{error}</p>}

            <button
              type="submit"
              className="signup-button"
              disabled={loading}
            >
              {loading ? "Creating account..." : "Create Account"}
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

export default Signup;
