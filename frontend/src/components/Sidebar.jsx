import { useState } from "react";
import { NavLink } from "react-router-dom";
import {
  CalendarDays,
  ChevronsLeft,
  ChevronsRight,
  House,
  LogOut,
  PiggyBank,
  Plane,
  Settings,
  TrendingUp,
  Users,
  Wallet,
} from "lucide-react";
import { clearSession } from "../api/client";
import TripbudgetMark from "./TripbudgetMark";
import "../styles/Sidebar.css";

const menuItems = [
  { name: "Home", path: "/dashboard", Icon: House },
  { name: "My Trips", path: "/trips", Icon: Plane },
  { name: "Itinerary", path: "/itineraries", Icon: CalendarDays },
  { name: "Budget", path: "/budget-planner", Icon: Wallet },
  { name: "Savings", path: "/savings-planner", Icon: TrendingUp },
  { name: "Shared Trips", path: "/shared-trips", Icon: Users },
];

function Sidebar() {
  const [collapsed, setCollapsed] = useState(false);

  const linkClass = ({ isActive }) => `sidebar-link ${isActive ? "active" : ""}`;

  return (
    <aside className={`sidebar ${collapsed ? "collapsed" : ""}`}>
      <div className="sidebar-header">
        <NavLink to="/dashboard" className="sidebar-brand" title="Tripbudget home">
          <TripbudgetMark />
          {!collapsed && <span className="tb-wide">Tripbudget</span>}
        </NavLink>

        <button
          type="button"
          className="sidebar-toggle"
          onClick={() => setCollapsed(!collapsed)}
          aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
        >
          {collapsed ? <ChevronsRight size={18} /> : <ChevronsLeft size={18} />}
        </button>
      </div>

      <nav className="sidebar-nav" aria-label="Main">
        {menuItems.map(({ name, path, Icon }) => (
          <NavLink
            key={path}
            to={path}
            title={collapsed ? name : undefined}
            className={linkClass}
          >
            <Icon className="sidebar-icon" size={19} strokeWidth={2} aria-hidden="true" />
            {!collapsed && <span>{name}</span>}
          </NavLink>
        ))}
      </nav>

      <div className="sidebar-footer">
        <NavLink to="/settings" title={collapsed ? "Settings" : undefined} className={linkClass}>
          <Settings className="sidebar-icon" size={19} strokeWidth={2} aria-hidden="true" />
          {!collapsed && <span>Settings</span>}
        </NavLink>

        {/* Logging out clears the saved token, then goes back to the landing page */}
        <NavLink
          to="/"
          onClick={clearSession}
          title={collapsed ? "Log out" : undefined}
          className="sidebar-link sidebar-logout"
        >
          <LogOut className="sidebar-icon" size={19} strokeWidth={2} aria-hidden="true" />
          {!collapsed && <span>Log out</span>}
        </NavLink>
      </div>
    </aside>
  );
}

export default Sidebar;
