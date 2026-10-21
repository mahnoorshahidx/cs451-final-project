import "../styles/Login.css";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { register } from "../api/api";

// New page (the file was empty). Reuses Login.css so it matches the login page.

// Field names must match the backend's RegisterRequest exactly
const fields = [
  { name: "name", label: "Full name", type: "text", placeholder: "Jane Doe" },
  { name: "email", label: "Email address", type: "email", placeholder: "example@example.com" },
  { name: "password", label: "Password", type: "password", placeholder: "8+ characters, a letter and a number" },
  { name: "confirmPassword", label: "Confirm password", type: "password", placeholder: "Re-enter password" },
  { name: "phoneNumber", label: "Phone number", type: "tel", placeholder: "555-123-4567" },
  { name: "dateOfBirth", label: "Date of birth", type: "date" },
  { name: "address", label: "Address", type: "text", placeholder: "123 Main St" },
];

function Signup() {
  const navigate = useNavigate();
  const [form, setForm] = useState(
    Object.fromEntries(fields.map((f) => [f.name, ""]))
  );
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  function handleChange(e) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setFieldErrors({});

    if (form.password !== form.confirmPassword) {
      setFieldErrors({ confirmPassword: "Passwords do not match" });
      return;
    }

    setLoading(true);
    // On success, goes to login with a "registered" flag; on failure, shows
    // the backend's per-field errors (e.g. weak password, email already used)
    try {
      // Everything except confirmPassword, which the backend doesn't take
      const body = {
        name: form.name,
        email: form.email,
        password: form.password,
        phoneNumber: form.phoneNumber,
        dateOfBirth: form.dateOfBirth,
        address: form.address,
      };
      await register(body);
      navigate("/", { state: { registered: true } });
    } catch (err) {
      setFieldErrors(err.fieldErrors);
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-page">
      <section className="login-left"></section>

      <section className="login-right">
        <div className="login-container">
          <p className="welcome-text">GET STARTED</p>
          <h1>Create your account</h1>
          <p className="login-subtitle">
            Start planning and saving for your next trip.
          </p>

          <form className="login-form" onSubmit={handleSubmit} noValidate>
            {fields.map((f) => (
              <div key={f.name} className="signup-field">
                <label htmlFor={f.name}>{f.label}</label>
                <input
                  id={f.name}
                  name={f.name}
                  type={f.type}
                  placeholder={f.placeholder}
                  value={form[f.name]}
                  onChange={handleChange}
                />
                {fieldErrors[f.name] && (
                  <p className="error-text">{fieldErrors[f.name]}</p>
                )}
              </div>
            ))}

            {error && Object.keys(fieldErrors).length === 0 && (
              <p className="error-text">{error}</p>
            )}

            <button type="submit" className="login-button" disabled={loading}>
              {loading ? "Creating account..." : "Create Account"}
            </button>
          </form>

          <p className="signup-text">
            Already have an account? <Link to="/">Sign in</Link>
          </p>
        </div>
      </section>
    </div>
  );
}

export default Signup;