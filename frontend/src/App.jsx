import { useEffect } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { getProfile } from "./api/api";
import Landing from "./pages/Landing";
import Login from "./pages/Login";
import Signup from "./pages/Signup";
import Dashboard from "./pages/Dashboard";
import CreateTrip from "./pages/CreateTrip";
import MyTrips from "./pages/MyTrips";
import CreateItinerary from "./pages/CreateItinerary";
import Settings from "./pages/Settings";
import ProtectedRoute from "./components/ProtectedRoute";
import EditTrip from "./pages/EditTrip";
import BudgetDashboard from "./pages/BudgetDashboard";
import MyItineraries from "./pages/MyItineraries";
import ViewItinerary from "./pages/ViewItinerary";
import SharedTrips from "./pages/SharedTrips";
import BudgetPlanner from "./pages/BudgetPlanner";
import SavingsPlanner from "./pages/SavingsPlanner";
import BankAccounts from "./pages/BankAccounts";

function App() {

  // Load the user's saved appearance
  useEffect(() => {
    getProfile()
      .then((user) => {
        document.documentElement.setAttribute(
          "data-theme",
          user.appearance
        );
      })
      .catch(() => {
        // Use light mode if the user is not logged in
        document.documentElement.setAttribute("data-theme", "light");
      });
  }, []);

  return (
    <BrowserRouter>
      <Routes>

        {/* Landing page (public) */}
        <Route path="/" element={<Landing />} />

        {/* Login */}
        <Route path="/login" element={<Login />} />

        {/* Sign Up */}
        <Route path="/signup" element={<Signup />} />

        {/* Dashboard */}
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <Dashboard />
            </ProtectedRoute>
          }
        />

        {/* Create Trip */}
        <Route
          path="/create-trip"
          element={
            <ProtectedRoute>
              <CreateTrip />
            </ProtectedRoute>
          }
        />

        {/* My Trips */}
        <Route
          path="/trips"
          element={
            <ProtectedRoute>
              <MyTrips />
            </ProtectedRoute>
          }
        />

        <Route
          path="/shared-trips"
          element={
            <ProtectedRoute>
              <SharedTrips />
            </ProtectedRoute>
          }
        />

        {/* Create Itinerary */}
        <Route
          path="/create-itinerary"
          element={
            <ProtectedRoute>
              <CreateItinerary />
            </ProtectedRoute>
          }
        />

        <Route
          path="/my-trips"
          element={<Navigate to="/trips" replace />}
        />

        {/* Edit Trip */}
        <Route
          path="/trips/:tripId/edit"
          element={
            <ProtectedRoute>
              <EditTrip />
            </ProtectedRoute>
          }
        />

        {/* Settings */}
        <Route
          path="/settings"
          element={
            <ProtectedRoute>
              <Settings />
            </ProtectedRoute>
          }
        />

        <Route path="/budget" element={<ProtectedRoute><BudgetDashboard /></ProtectedRoute>} />
        <Route path="/budget/:tripId" element={<ProtectedRoute><BudgetDashboard /></ProtectedRoute>} />
        
        {/* Savings Planner */}
        <Route
          path="/savings"
          element={
            <ProtectedRoute>
              <BudgetDashboard />
            </ProtectedRoute>
          }
        />

        <Route
        path="/budget-planner"
        element={
          <ProtectedRoute>
            <BudgetPlanner />
          </ProtectedRoute>
        }
      />

      <Route
        path="/budget-planner/:tripId"
        element={
          <ProtectedRoute>
            <BudgetPlanner />
          </ProtectedRoute>
        }
      />

      <Route
        path="/savings-planner"
        element={
          <ProtectedRoute>
            <SavingsPlanner />
          </ProtectedRoute>
        }
      />

        <Route
          path="*"
          element={<Navigate to="/dashboard" replace />}
        />

        {/* Saved itinerary listing */}
        <Route
          path="/itineraries"
          element={
            <ProtectedRoute>
              <MyItineraries />
            </ProtectedRoute>
          }
        />

        {/* View a saved itinerary */}
        <Route
          path="/itineraries/:tripId"
          element={
            <ProtectedRoute>
              <ViewItinerary />
            </ProtectedRoute>
          }
        />

        <Route
          path="/bank-connection"
          element={
            <ProtectedRoute>
              <BankAccounts />
            </ProtectedRoute>
          }
        />

      </Routes>
    </BrowserRouter>
  );
}

export default App;
