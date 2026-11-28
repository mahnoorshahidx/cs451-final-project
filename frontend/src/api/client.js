// Shared fetch helper: adds the base URL, JSON headers and the JWT on every request.
const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

// ---- Session (token + logged-in user) ----

export function getToken() {
  return localStorage.getItem("token");
}

export function getCurrentUser() {
  try {
    return JSON.parse(localStorage.getItem("user")) ?? null;
  } catch {
    return null;
  }
}

export function saveSession({ token, name, email }) {
  localStorage.setItem("token", token);
  localStorage.setItem("user", JSON.stringify({ name, email }));
}

// Updates the stored user information after editing the profile
export function updateCurrentUser({ name, email }) {
  localStorage.setItem("user", JSON.stringify({ name, email }));
}

export function clearSession() {
  localStorage.removeItem("token");
  localStorage.removeItem("user");
}

// "Ahmed Hafsa" -> "AH"
export function initials(name = "") {
  return name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join("");
}

// ---- Errors ----

// Backend errors look like { message } or { message, errors: { field: msg } }
export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || `Request failed (${status})`);
    this.status = status;
    this.fieldErrors = body?.errors || {};
  }
}

// ---- Fetch ----

export async function apiFetch(path, { method = "GET", body } = {}) {
  const token = getToken();
  const headers = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (token) headers.Authorization = `Bearer ${token}`;

  let res;
  try {
    res = await fetch(`${API_URL}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, { message: "Can't reach the server. Is the backend running?" });
  }


  // Expired or invalid token: preserve invitation links
  if (res.status === 401 && token) {
    const currentPath = window.location.pathname;

    clearSession();

    if (/^\/invite\/[A-Za-z0-9_-]+$/.test(currentPath)) {
      window.location.href =
        `/login?returnTo=${encodeURIComponent(currentPath)}`;
    } else {
      window.location.href = "/login";
    }

    throw new ApiError(401, {
      message: "Your session expired. Please sign in again.",
    });
  }


  const text = await res.text();
  const data = text ? JSON.parse(text) : null;

  if (!res.ok) throw new ApiError(res.status, data);
  return data;
}
