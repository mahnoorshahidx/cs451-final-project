import { useEffect, useState } from "react";
import {
  getNotifications,
  markNotificationAsRead,
} from "../api/api";
import "../styles/NotificationBell.css";

export default function NotificationBell() {
  const [notifications, setNotifications] = useState([]);
  const [isOpen, setIsOpen] = useState(false);
  const [error, setError] = useState("");

  // Load notifications when the Dashboard opens
  // and automatically refresh every 30 seconds
  useEffect(() => {
    let active = true;

    const loadNotifications = async () => {
      try {
        const data = await getNotifications();

        if (active) {
          setNotifications(data);
          setError("");
        }
      } catch {
        if (active) {
          setError("Failed to load notifications.");
        }
      }
    };

    loadNotifications();

    const interval = setInterval(loadNotifications, 30000);

    return () => {
      active = false;
      clearInterval(interval);
    };
  }, []);

  // Count unread notifications
  const unreadCount = notifications.filter(
    (notification) => !notification.isRead
  ).length;

  // Mark a notification as read
  const handleMarkAsRead = async (id) => {
    try {
      await markNotificationAsRead(id);

      setNotifications((current) =>
        current.map((notification) =>
          notification.id === id
            ? { ...notification, isRead: true }
            : notification
        )
      );

      setError("");
    } catch {
      setError("Failed to mark notification as read.");
    }
  };

  return (
    <div className="notification-container">
      {/* Notification bell */}
      <button
        type="button"
        className="notification-bell"
        onClick={() => setIsOpen(!isOpen)}
        aria-label="Notifications"
      >
        🔔

        {unreadCount > 0 && (
          <span className="notification-badge">
            {unreadCount}
          </span>
        )}
      </button>

      {/* Notification dropdown */}
      {isOpen && (
        <div className="notification-dropdown">
          <h3>Notifications</h3>

          {error && (
            <p className="notification-error">{error}</p>
          )}

          {!error && notifications.length === 0 && (
            <p className="notification-empty">
              No notifications yet
            </p>
          )}

          {notifications.map((notification) => (
            <div
              key={notification.id}
              className={`notification-item ${
                notification.isRead ? "read" : "unread"
              }`}
            >
              <p>{notification.message}</p>

              {!notification.isRead && (
                <button
                  type="button"
                  onClick={() =>
                    handleMarkAsRead(notification.id)
                  }
                >
                  Mark as Read
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
