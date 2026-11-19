import { useState } from "react";
import { NavLink } from "react-router-dom";
import "../styles/Sidebar.css";
import { CalendarDays } from "lucide-react";

// Classic two-person silhouette, similar to older social-network group icons.
function SharedTripsIcon() {
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
      style={{ display: "block", margin: "auto" }}
    >
      <circle cx="9" cy="7.6" r="3.1" />
      <circle cx="16.8" cy="8.5" r="2.65" />
      <path d="M2.4 18.4c0-3.45 2.75-5.45 6.6-5.45s6.6 2 6.6 5.45V20H2.4v-1.6Z" />
      <path d="M16.5 13.2c3.2 0 5.1 1.85 5.1 5.05V20h-4.55v-1.6c0-1.94-.59-3.56-1.63-4.76.36-.24.72-.38 1.08-.44Z" />
    </svg>
  );
}

function Sidebar() {
  const [collapsed, setCollapsed] = useState(false);

  const menuItems = [
    { name: "Dashboard", path: "/dashboard", icon: "⌂" },
    { name: "My Trips", path: "/trips", icon: "✈" },
    { name: "Itinerary", path: "/itineraries", icon: <CalendarDays size={21} strokeWidth={2} /> },
    { name: "Savings Planner (Old)", path: "/savings", icon: "$" },
    { name: "Budget Planner", path: "/budget-planner", icon: "$" },
    { name: "Savings Planner", path: "/savings-planner", icon: "◈" },
    { name: "Shared Trips", path: "/shared-trips", icon: <SharedTripsIcon /> },
    { name: "Settings", path: "/settings", icon: "⚙" },
  ];



  return (
    <aside className={`sidebar ${collapsed ? "collapsed" : ""}`}>
      <div className="sidebar-header">
        {!collapsed && <h2>✈ Trip Budget Planner</h2>}

        <button
          type="button"
          className="sidebar-toggle"
          onClick={() => setCollapsed(!collapsed)}
          aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
        >
          {collapsed ? "☰" : "‹"}
        </button>
      </div>

      <nav className="sidebar-nav">
        {menuItems.map((item) => (
          <NavLink
            key={item.name}
            to={item.path}
            title={collapsed ? item.name : undefined}
            className={({ isActive }) =>
              `sidebar-link ${isActive ? "active" : ""}`
            }
          >
            <span className="sidebar-icon">{item.icon}</span>
            {!collapsed && <span>{item.name}</span>}
          </NavLink>
        ))}

        {/* Logout Button */}
        <NavLink to="/" className="sidebar-logout">
          <span className="sidebar-icon">↪</span>
          {!collapsed && <span>Log Out</span>}
        </NavLink>
      </nav>
    </aside>
  );
}

export default Sidebar;
