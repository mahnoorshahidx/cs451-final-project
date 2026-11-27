
import { useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import {
  getProfile,
  updateProfile,
  changePassword,
  updateNotificationPreference,
  updatePreferences,
} from "../api/api";

import { updateCurrentUser } from "../api/client";
import "../styles/Dashboard.css";
import "../styles/Settings.css";

import { Bell, Check, LockKeyhole, Moon, SlidersHorizontal, Sun } from "lucide-react";

// Settings page component
function Settings() {

  // Profile information
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  // Password information
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [changingPassword, setChangingPassword] = useState(false);
  const [passwordError, setPasswordError] = useState("");
  const [passwordSuccess, setPasswordSuccess] = useState("");

  // Notification preferences
  const [tripRemindersEnabled, setTripRemindersEnabled] = useState(true);
  const [savingNotifications, setSavingNotifications] = useState(false);
  const [notificationError, setNotificationError] = useState("");
  const [notificationSuccess, setNotificationSuccess] = useState("");

  // User preferences
  const [currency, setCurrency] = useState("USD");
  const [dateFormat, setDateFormat] = useState("MM/DD/YYYY");
  const [appearance, setAppearance] = useState("light");
  const [savingPreferences, setSavingPreferences] = useState(false);
  const [preferencesError, setPreferencesError] = useState("");
  const [preferencesSuccess, setPreferencesSuccess] = useState("");

  // Load the user's profile and preferences
  useEffect(() => {
    getProfile()
      .then((user) => {
        setName(user.name);
        setEmail(user.email);
        setTripRemindersEnabled(user.tripRemindersEnabled);
        setCurrency(user.currency);
        setDateFormat(user.dateFormat);
        setAppearance(user.appearance);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  // Save profile changes
  async function handleSave(event) {
    event.preventDefault();
    setError("");
    setSuccess("");
    setSaving(true);

    try {
      const updatedUser = await updateProfile({ name, email });

      setName(updatedUser.name);
      setEmail(updatedUser.email);
      updateCurrentUser(updatedUser);

      setSuccess("Profile updated successfully!");
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  // Change the user's password
  async function handlePasswordChange(event) {
    event.preventDefault();
    setPasswordError("");
    setPasswordSuccess("");

    // Check that both new passwords match
    if (newPassword !== confirmPassword) {
      setPasswordError("New passwords do not match.");
      return;
    }

    // Check password length
    if (newPassword.length < 8) {
      setPasswordError("Password must be at least 8 characters.");
      return;
    }

    setChangingPassword(true);

    try {
      await changePassword({ currentPassword, newPassword });

      // Clear password fields after saving
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");

      setPasswordSuccess("Password changed successfully!");
    } catch (err) {
      setPasswordError(err.message);
    } finally {
      setChangingPassword(false);
    }
  }

  // Save the user's trip reminder preference
  async function handleNotificationToggle() {
    const newValue = !tripRemindersEnabled;

    setSavingNotifications(true);
    setNotificationError("");
    setNotificationSuccess("");

    try {
      await updateNotificationPreference(newValue);
      setTripRemindersEnabled(newValue);
      setNotificationSuccess("Notification preference saved!");
    } catch (err) {
      setNotificationError(err.message);
    } finally {
      setSavingNotifications(false);
    }
  }

  // Save currency, date format, and appearance
  async function handleSavePreferences(event) {
    event.preventDefault();
    setPreferencesError("");
    setPreferencesSuccess("");
    setSavingPreferences(true);

    try {
      const updatedUser = await updatePreferences({
        currency,
        dateFormat,
        appearance,
      });

      setCurrency(updatedUser.currency);
      setDateFormat(updatedUser.dateFormat);
      setAppearance(updatedUser.appearance);

      // Apply the saved appearance
      document.documentElement.setAttribute(
        "data-theme",
        updatedUser.appearance
      );

      setPreferencesSuccess("Preferences saved successfully!");
    } catch (err) {
      setPreferencesError(err.message);
    } finally {
      setSavingPreferences(false);
    }
  }

  const initials = name.split(" ").filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join("") || "?";

  return (
    <div className="dashboard-page set-page">
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      <main className="dashboard-main set-main">
        <header className="set-header">
          <span className="tb-kicker">Settings</span>
          <h1 className="tb-wide">Your account</h1>
          <p>Profile, password, reminders and how the app looks.</p>
        </header>

        <div className="set-grid">
          {/* Profile */}
          <section className="set-card set-profile" aria-labelledby="set-profile-title">
            <div className="set-profile-head">
              <span className="set-avatar" aria-hidden="true">{loading ? "" : initials}</span>
              <div>
                <h2 id="set-profile-title" className="tb-wide">Profile</h2>
                <p>{loading ? "Loading..." : email}</p>
              </div>
            </div>

            {!loading && (
              <form onSubmit={handleSave} className="set-form">
                <label className="tb-field">
                  Name
                  <input id="profile-name" className="tb-input" type="text" value={name} onChange={(e) => setName(e.target.value)} required />
                </label>
                <label className="tb-field">
                  Email
                  <input id="profile-email" className="tb-input" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
                </label>
                <div className="set-actions">
                  <button type="submit" className="tb-btn tb-btn-primary" disabled={saving}>
                    {saving ? "Saving..." : "Save profile"}
                  </button>
                  {error && <p role="alert" className="set-msg is-error">{error}</p>}
                  {success && <p role="status" className="set-msg is-ok"><Check size={16} aria-hidden="true" /> {success}</p>}
                </div>
              </form>
            )}
          </section>

          {/* Appearance and preferences */}
          <section className="set-card" aria-labelledby="set-prefs-title">
            <h2 id="set-prefs-title" className="tb-wide"><SlidersHorizontal size={18} aria-hidden="true" /> Preferences</h2>

            {loading ? (
              <p className="set-muted">Loading preferences...</p>
            ) : (
              <form onSubmit={handleSavePreferences} className="set-form">
                <fieldset className="set-appearance">
                  <legend>Appearance</legend>
                  {[
                    { value: "light", label: "Light", Icon: Sun },
                    { value: "dark", label: "Dark", Icon: Moon },
                  ].map(({ value, label, Icon }) => (
                    <label key={value} className={`set-theme-option is-${value}${appearance === value ? " is-selected" : ""}`}>
                      <input
                        type="radio"
                        name="appearance"
                        value={value}
                        checked={appearance === value}
                        onChange={() => setAppearance(value)}
                      />
                      <span className="set-theme-preview" aria-hidden="true">
                        <span /><span /><span />
                      </span>
                      <span className="set-theme-label"><Icon size={15} aria-hidden="true" /> {label}</span>
                    </label>
                  ))}
                </fieldset>

                <div className="set-two">
                  <label className="tb-field">
                    Currency
                    <select id="currency" className="tb-input" value={currency} onChange={(e) => setCurrency(e.target.value)}>
                      <option value="USD">USD · US Dollar</option>
                      <option value="EUR">EUR · Euro</option>
                      <option value="GBP">GBP · British Pound</option>
                      <option value="MAD">MAD · Moroccan Dirham</option>
                    </select>
                  </label>
                  <label className="tb-field">
                    Date format
                    <select id="date-format" className="tb-input" value={dateFormat} onChange={(e) => setDateFormat(e.target.value)}>
                      <option value="MM/DD/YYYY">MM/DD/YYYY</option>
                      <option value="DD/MM/YYYY">DD/MM/YYYY</option>
                    </select>
                  </label>
                </div>

                <div className="set-actions">
                  <button type="submit" className="tb-btn tb-btn-accent" disabled={savingPreferences}>
                    {savingPreferences ? "Saving..." : "Save preferences"}
                  </button>
                  {preferencesError && <p role="alert" className="set-msg is-error">{preferencesError}</p>}
                  {preferencesSuccess && <p role="status" className="set-msg is-ok"><Check size={16} aria-hidden="true" /> {preferencesSuccess}</p>}
                </div>
              </form>
            )}
          </section>

          {/* Password */}
          <section className="set-card" aria-labelledby="set-password-title">
            <h2 id="set-password-title" className="tb-wide"><LockKeyhole size={18} aria-hidden="true" /> Password</h2>
            <form onSubmit={handlePasswordChange} className="set-form">
              <label className="tb-field">
                Current password
                <input id="current-password" className="tb-input" type="password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} autoComplete="off" required />
              </label>
              <div className="set-two">
                <label className="tb-field">
                  New password
                  <input id="new-password" className="tb-input" type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} autoComplete="new-password" minLength={8} required />
                </label>
                <label className="tb-field">
                  Confirm new password
                  <input id="confirm-password" className="tb-input" type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} autoComplete="new-password" minLength={8} required />
                </label>
              </div>
              <p className="set-muted">At least 8 characters.</p>
              <div className="set-actions">
                <button type="submit" className="tb-btn tb-btn-outline" disabled={changingPassword}>
                  {changingPassword ? "Changing..." : "Change password"}
                </button>
                {passwordError && <p role="alert" className="set-msg is-error">{passwordError}</p>}
                {passwordSuccess && <p role="status" className="set-msg is-ok"><Check size={16} aria-hidden="true" /> {passwordSuccess}</p>}
              </div>
            </form>
          </section>

          {/* Notifications */}
          <section className="set-card" aria-labelledby="set-notify-title">
            <h2 id="set-notify-title" className="tb-wide"><Bell size={18} aria-hidden="true" /> Notifications</h2>
            <div className="set-toggle-row">
              <div>
                <strong>Trip reminders</strong>
                <span>A heads-up 7 days before each trip.</span>
              </div>
              <label className="set-switch">
                <input
                  type="checkbox"
                  role="switch"
                  checked={tripRemindersEnabled}
                  onChange={handleNotificationToggle}
                  disabled={loading || savingNotifications || !!error}
                  aria-label="Trip reminders"
                />
                <span aria-hidden="true" />
              </label>
            </div>
            {savingNotifications && <p className="set-muted">Saving...</p>}
            {notificationError && <p role="alert" className="set-msg is-error">{notificationError}</p>}
            {notificationSuccess && <p role="status" className="set-msg is-ok"><Check size={16} aria-hidden="true" /> {notificationSuccess}</p>}
          </section>
        </div>
      </main>
    </div>
  );
}

export default Settings;
