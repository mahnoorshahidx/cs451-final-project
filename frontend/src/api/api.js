// Every backend endpoint the frontend uses, in one place.
import { apiFetch, ApiError } from "./client";

// ---- Auth ----
// Step 1: email + password. Returns { twoFactorRequired, challengeId, sentTo, ... } or { token, name, email }
export const login = (email, password) =>
  apiFetch("/api/auth/login", { method: "POST", body: { email, password } });

// Step 2 (two-factor): the 6-digit code. Returns { token, name, email }
export const verifyLoginCode = (challengeId, code) =>
  apiFetch("/api/auth/verify", { method: "POST", body: { challengeId, code } });

export const resendLoginCode = (challengeId) =>
  apiFetch("/api/auth/resend", { method: "POST", body: { challengeId } });

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

// ---- Recommendations (Create Trip wizard) ----
export const recommendTiming = (body) =>
  apiFetch("/api/recommendations/timing", { method: "POST", body });

export const recommendBudget = (body) =>
  apiFetch("/api/recommendations/budget", { method: "POST", body });

// ---- Budget page & savings ----
// Every trip with its budget total and savings progress
export const getBudgetOverview = () => apiFetch("/api/budget-overview");

// Returns null when the trip has no savings goal yet (404)
export async function getSavings(tripId) {
  try {
    return await apiFetch(`/api/trips/${tripId}/savings`);
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) return null;
    throw err;
  }
}

export const saveSavingsGoal = (tripId, goal) =>
  apiFetch(`/api/trips/${tripId}/savings`, { method: "PUT", body: goal });

export const addContribution = (tripId, contribution) =>
  apiFetch(`/api/trips/${tripId}/savings/contributions`, { method: "POST", body: contribution });

export const deleteContribution = (tripId, contributionId) =>
  apiFetch(`/api/trips/${tripId}/savings/contributions/${contributionId}`, { method: "DELETE" });


 // ---- Itinerary activities ----

// Retrieve all activities for a trip
export const listItineraryActivities = (tripId) =>
  apiFetch(`/api/trips/${tripId}/activities`);

// Save a new activity to MySQL
export const createItineraryActivity = (tripId, activity) =>
  apiFetch(`/api/trips/${tripId}/activities`, {
    method: "POST",
    body: activity,
  });

// Update an existing activity
export const updateItineraryActivity = (tripId, activityId, activity) =>
  apiFetch(`/api/trips/${tripId}/activities/${activityId}`, {
    method: "PUT",
    body: activity,
  });

// Delete an activity
export const deleteItineraryActivity = (tripId, activityId) =>
  apiFetch(`/api/trips/${tripId}/activities/${activityId}`, {
    method: "DELETE",
  });

// Delete the whole itinerary (every activity in the trip). The trip itself is kept.
export const deleteItinerary = (tripId) =>
  apiFetch(`/api/trips/${tripId}/activities`, { method: "DELETE" });

// AI: generate and save suggested activities from the questionnaire answers.
// Optional startDate / endDate ("YYYY-MM-DD", at most 14 days) plan only those days;
// with replaceExisting: true only those days are cleared first. avoidTitles: places not to repeat.
// preferences: { pace: "relaxed" | "balanced" | "packed", foodPreferences: [], interests: [],
//                specialRequests: "", replaceExisting: false }
// Returns the new activities. Throws ApiError with status 503 if the AI isn't available.
export const generateItinerary = (tripId, preferences) =>
  apiFetch(`/api/trips/${tripId}/activities/generate`, {
    method: "POST",
    body: preferences,
  });
  
 // ---- Destination photographs ----
export const getDestinationPhoto = (destination) =>
  apiFetch(
    `/api/destination-photo?destination=${encodeURIComponent(destination)}`
  );

// ---- UC5: AI itinerary options to compare ----
// Makes a budget, balanced and luxury option as drafts (replaces older drafts)
export const generateItineraryOptions = (tripId, preferences) =>
  apiFetch(`/api/trips/${tripId}/itinerary-options`, { method: "POST", body: preferences });

export const listItineraryOptions = (tripId) => apiFetch(`/api/trips/${tripId}/itinerary-options`);

// Copies the chosen option into the itinerary; returns the new activities
export const applyItineraryOption = (tripId, optionId, replaceExisting) =>
  apiFetch(`/api/trips/${tripId}/itinerary-options/${optionId}/apply?replaceExisting=${replaceExisting ? "true" : "false"}`, {
    method: "POST",
  });

export const discardItineraryOptions = (tripId) =>
  apiFetch(`/api/trips/${tripId}/itinerary-options`, { method: "DELETE" });
