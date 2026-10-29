
import { useState } from "react";
import { NavLink } from "react-router-dom";
import "../styles/Sidebar.css";

function Sidebar() {
  const [collapsed, setCollapsed] = useState(false);

  const menuItems = [
    { name: "Dashboard", path: "/dashboard", icon: "⌂" },
    { name: "My Trips", path: "/trips", icon: "✈" },
    { name: "Savings Planner", path: "/savings", icon: "$" },
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

