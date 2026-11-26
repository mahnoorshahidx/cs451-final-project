import { Navigate } from "react-router-dom";
import { getToken } from "../api/client";

// Sends logged-out users back to the login page
function ProtectedRoute({ children }) {
  return getToken() ? children : <Navigate to="/login" replace />;
}

export default ProtectedRoute;
