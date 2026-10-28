
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
    streetAddress: "",
    city: "",
    state: "",
    zipCode: "",
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
        address: [
          formData.streetAddress.trim(),
          formData.city.trim(),
          [formData.state.trim(), formData.zipCode.trim()]
            .filter(Boolean)
            .join(" "),
        ]
          .filter(Boolean)
          .join(", "),
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

            <div className="signup-grid">

              <div className="signup-field">
                <label htmlFor="firstName">First name</label>
                <input
                  id="firstName"
                  name="firstName"
                  type="text"
                  placeholder="First name"
                  autoComplete="given-name"
                  value={formData.firstName}
                  onChange={handleChange}
                  required
                />
              </div>

              <div className="signup-field">
                <label htmlFor="lastName">Last name</label>
                <input
                  id="lastName"
                  name="lastName"
                  type="text"
                  placeholder="Last name"
                  autoComplete="family-name"
                  value={formData.lastName}
                  onChange={handleChange}
                  required
                />
              </div>

              {fieldErrors.name && (
                <p className="error-text signup-full">{fieldErrors.name}</p>
              )}

              <div className="signup-field signup-full">
                <label htmlFor="email">Email address</label>
                <input
                  id="email"
                  name="email"
                  type="email"
                  placeholder="example@example.com"
                  autoComplete="email"
                  value={formData.email}
                  onChange={handleChange}
                  required
                />
                {fieldErrors.email && <p className="error-text">{fieldErrors.email}</p>}
              </div>

              <div className="signup-field">
                <label htmlFor="password">Password</label>
                <input
                  id="password"
                  name="password"
                  type="password"
                  placeholder="Create a password"
                  autoComplete="new-password"
                  value={formData.password}
                  onChange={handleChange}
                  minLength={8}
                  required
                />
                {fieldErrors.password && <p className="error-text">{fieldErrors.password}</p>}
              </div>

              <div className="signup-field">
                <label htmlFor="confirmPassword">Confirm password</label>
                <input
                  id="confirmPassword"
                  name="confirmPassword"
                  type="password"
                  placeholder="Confirm password"
                  autoComplete="new-password"
                  value={formData.confirmPassword}
                  onChange={handleChange}
                  minLength={8}
                  required
                />
              </div>

              <div className="signup-field">
                <label htmlFor="phoneNumber">Phone number</label>
                <input
                  id="phoneNumber"
                  name="phoneNumber"
                  type="tel"
                  placeholder="555-123-4567"
                  autoComplete="tel"
                  value={formData.phoneNumber}
                  onChange={handleChange}
                />
                {fieldErrors.phoneNumber && <p className="error-text">{fieldErrors.phoneNumber}</p>}
              </div>

              <div className="signup-field">
                <label htmlFor="dateOfBirth">Date of birth</label>
                <input
                  id="dateOfBirth"
                  name="dateOfBirth"
                  type="date"
                  value={formData.dateOfBirth}
                  onChange={handleChange}
                />
                {fieldErrors.dateOfBirth && <p className="error-text">{fieldErrors.dateOfBirth}</p>}
              </div>

              <div className="signup-field signup-full">
                <label htmlFor="streetAddress">Street address</label>
                <input
                  id="streetAddress"
                  name="streetAddress"
                  type="text"
                  placeholder="123 Main Street"
                  autoComplete="street-address"
                  value={formData.streetAddress}
                  onChange={handleChange}
                />
              </div>

              <div className="signup-field">
                <label htmlFor="city">City</label>
                <input
                  id="city"
                  name="city"
                  type="text"
                  placeholder="Kansas City"
                  autoComplete="address-level2"
                  value={formData.city}
                  onChange={handleChange}
                />
              </div>

              <div className="signup-field">
                <label htmlFor="state">State</label>
                <input
                  id="state"
                  name="state"
                  type="text"
                  placeholder="MO"
                  autoComplete="address-level1"
                  value={formData.state}
                  onChange={handleChange}
                />
              </div>

              <div className="signup-field signup-full">
                <label htmlFor="zipCode">ZIP code</label>
                <input
                  id="zipCode"
                  name="zipCode"
                  type="text"
                  inputMode="numeric"
                  placeholder="64108"
                  autoComplete="postal-code"
                  value={formData.zipCode}
                  onChange={handleChange}
                />
                {fieldErrors.address && <p className="error-text">{fieldErrors.address}</p>}
              </div>

            </div>

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
