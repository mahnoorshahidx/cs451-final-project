// Every backend endpoint the frontend uses, in one place.
import { apiFetch, ApiError } from "./client";

// ---- Auth ----
export const login = (email, password) =>
  apiFetch("/api/auth/login", { method: "POST", body: { email, password } });

export const register = (data) =>
  apiFetch("/api/auth/register", { method: "POST", body: data });

// ---- Trips ----
export const listTrips = () => apiFetch("/api/trips");
export const getTrip = (id) => apiFetch(`/api/trips/${id}`);
export const createTrip = (trip) => apiFetch("/api/trips", { method: "POST", body: trip });
export const updateTrip = (id, trip) => apiFetch(`/api/trips/${id}`, { method: "PUT", body: trip });
export const deleteTrip = (id) => apiFetch(`/api/trips/${id}`, { method: "DELETE" });

// ---- Trip budget ----
// Returns null when the trip has no budget yet (404)
export async function getBudget(tripId) {
  try {
    return await apiFetch(`/api/trips/${tripId}/budget`);
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) return null;
    throw err;
  }
}

export const saveBudget = (tripId, budget) =>
  apiFetch(`/api/trips/${tripId}/budget`, { method: "PUT", body: budget });

// ---- Bank accounts ----
export const listBankAccounts = () => apiFetch("/api/bank-accounts");
export const connectBankAccount = (account) =>
  apiFetch("/api/bank-accounts", { method: "POST", body: account });
export const disconnectBankAccount = (id) =>
  apiFetch(`/api/bank-accounts/${id}`, { method: "DELETE" });


// ---- User profile ----
// Gets the logged-in user's profile
export const getProfile = () =>
  apiFetch("/api/users/me");

// Updates the logged-in user's name and email
export const updateProfile = (profile) =>
  apiFetch("/api/users/me", {
    method: "PUT",
    body: profile,
  });

// ---- Account security ----
// Changes the logged-in user's password
export const changePassword = (passwords) =>
  apiFetch("/api/users/me/password", {
    method: "PUT",
    body: passwords,
  });

// ---- Notifications ----
// Gets notifications for the logged-in user
export const getNotifications = () =>
  apiFetch("/api/notifications");

// Marks a notification as read
export const markNotificationAsRead = (id) =>
  apiFetch(`/api/notifications/${id}/read`, {
    method: "PATCH",
  });

 // Updates the user's trip reminder preference
 export const updateNotificationPreference = (enabled) =>
 apiFetch("/api/users/me/notifications", {
   method: "PUT",
   body: { tripRemindersEnabled: enabled },
 });

// ---- User preferences ----
// Saves currency, date format, and appearance
export const updatePreferences = (preferences) =>
apiFetch("/api/users/me/preferences", {
  method: "PUT",
  body: preferences,
});