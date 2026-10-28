
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

  return (
    <div className="dashboard-page">

      {/* Sidebar navigation */}
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      {/* Main settings content */}
      <main className="dashboard-main">
        <div className="settings-content">

          {/* Page heading */}
          <section className="settings-header">
            <h1>Settings</h1>
            <p>Manage your account settings and preferences.</p>
          </section>

          {/* Profile information */}
          <section className="settings-card">
            <h2>Profile Information</h2>

            {loading ? (
              <p>Loading profile...</p>
            ) : (
              <form onSubmit={handleSave} className="settings-form">

                {/* Name */}
                <label htmlFor="profile-name">Name</label>
                <input
                  id="profile-name"
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                />

                {/* Email */}
                <label htmlFor="profile-email">Email</label>
                <input
                  id="profile-email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />

                {/* Save profile */}
                <button type="submit" disabled={saving}>
                  {saving ? "Saving..." : "Save Changes"}
                </button>
              </form>
            )}

            {error && (
              <p role="alert" className="error-text">{error}</p>
            )}

            {success && (
              <p role="status" className="settings-success">
                {success}
              </p>
            )}
          </section>

          {/* Account security */}
          <section className="settings-card">
            <h2>Account Security</h2>
            <p>Change your account password.</p>

            <form onSubmit={handlePasswordChange} className="settings-form">

              {/* Current password */}
              <label htmlFor="current-password">Current Password</label>
              <input
                id="current-password"
                type="password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                autoComplete="off"
                required
              />

              {/* New password */}
              <label htmlFor="new-password">New Password</label>
              <input
                id="new-password"
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                autoComplete="new-password"
                minLength={8}
                required
              />

              {/* Confirm new password */}
              <label htmlFor="confirm-password">Confirm New Password</label>
              <input
                id="confirm-password"
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                autoComplete="new-password"
                minLength={8}
                required
              />

              {/* Change password button */}
              <button type="submit" disabled={changingPassword}>
                {changingPassword ? "Changing..." : "Change Password"}
              </button>
            </form>

            {passwordError && (
              <p role="alert" className="error-text">
                {passwordError}
              </p>
            )}

            {passwordSuccess && (
              <p role="status" className="settings-success">
                {passwordSuccess}
              </p>
            )}
          </section>

          {/* Notification preferences */}
          <section className="settings-card">
            <h2>Notification Preferences</h2>
            <p>Manage your trip reminders.</p>

            <div className="notification-preference">
              <div>
                <h3>Trip Reminders</h3>
                <p>Receive notifications 7 days before your trip.</p>
              </div>

              {/* Enable or disable trip reminders */}
              <label className="settings-switch">
                <input
                  type="checkbox"
                  checked={tripRemindersEnabled}
                  onChange={handleNotificationToggle}
                  disabled={loading || savingNotifications || !!error}
                  aria-label="Enable trip reminders"
                />
                <span className="settings-switch-slider"></span>
              </label>
            </div>

            {savingNotifications && (
              <p>Saving preference...</p>
            )}

            {notificationError && (
              <p role="alert" className="error-text">
                {notificationError}
              </p>
            )}

            {notificationSuccess && (
              <p role="status" className="settings-success">
                {notificationSuccess}
              </p>
            )}
          </section>

          {/* User preferences */}
          <section className="settings-card">
            <h2>Preferences</h2>
            <p>Customize your trip planning experience.</p>

            {loading ? (
              <p>Loading preferences...</p>
            ) : (
              <form onSubmit={handleSavePreferences} className="settings-form">

                {/* Currency */}
                <label htmlFor="currency">Currency</label>
                <select
                  id="currency"
                  value={currency}
                  onChange={(e) => setCurrency(e.target.value)}
                >
                  <option value="USD">USD - US Dollar</option>
                  <option value="EUR">EUR - Euro</option>
                  <option value="GBP">GBP - British Pound</option>
                  <option value="MAD">MAD - Moroccan Dirham</option>
                </select>

                {/* Date format */}
                <label htmlFor="date-format">Date Format</label>
                <select
                  id="date-format"
                  value={dateFormat}
                  onChange={(e) => setDateFormat(e.target.value)}
                >
                  <option value="MM/DD/YYYY">MM/DD/YYYY</option>
                  <option value="DD/MM/YYYY">DD/MM/YYYY</option>
                </select>

                {/* Appearance */}
                <label htmlFor="appearance">Appearance</label>
                <select
                  id="appearance"
                  value={appearance}
                  onChange={(e) => setAppearance(e.target.value)}
                >
                  <option value="light">Light Mode</option>
                  <option value="dark">Dark Mode</option>
                </select>

                {/* Save preferences */}
                <button type="submit" disabled={savingPreferences}>
                  {savingPreferences ? "Saving..." : "Save Preferences"}
                </button>
              </form>
            )}

            {preferencesError && (
              <p role="alert" className="error-text">
                {preferencesError}
              </p>
            )}

            {preferencesSuccess && (
              <p role="status" className="settings-success">
                {preferencesSuccess}
              </p>
            )}
          </section>

        </div>
      </main>
    </div>
  );
}

export default Settings;
