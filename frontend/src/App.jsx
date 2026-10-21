import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";

import Login from "./pages/Login";
import Signup from "./pages/Signup";
import Dashboard from "./pages/Dashboard";
import CreateTrip from "./pages/CreateTrip";
import MyTrips from "./pages/MyTrips";
import ProtectedRoute from "./components/ProtectedRoute";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Login />} />
        <Route path="/login" element={<Navigate to="/" replace />} />
        {/* New: the "Create an account" link had no route */}
        <Route path="/signup" element={<Signup />} />

        {/* Wrapped in ProtectedRoute: logged-out users go back to login */}
        <Route path="/dashboard" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
        <Route path="/create-trip" element={<ProtectedRoute><CreateTrip /></ProtectedRoute>} />

        {/* New: My Trips had no route, so it rendered blank (SCRUM-32) */}
        <Route path="/trips" element={<ProtectedRoute><MyTrips /></ProtectedRoute>} />
        <Route path="/my-trips" element={<Navigate to="/trips" replace />} />

        {/* New: CreateTrip doubles as the edit page */}
        <Route path="/trips/:tripId/edit" element={<ProtectedRoute><CreateTrip /></ProtectedRoute>} />

        {/* Any unknown URL redirects instead of showing a blank page */}
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;