import { useEffect, useRef } from "react";

// Small in-page dialog (instead of window.confirm).
// actions: [{ label, onClick, tone: "primary" | "danger" | "plain" }]. Escape or the backdrop cancels.
export default function ConfirmDialog({ title, children, actions, onCancel, busy = false }) {
  const firstButton = useRef(null);

  useEffect(() => {
    firstButton.current?.focus();
    const onKey = (event) => {
      if (event.key === "Escape" && !busy) onCancel?.();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onCancel, busy]);

  return (
    <div className="tb-dialog-backdrop" onMouseDown={() => !busy && onCancel?.()}>
      <div
        className="tb-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="tb-dialog-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <h2 id="tb-dialog-title" className="tb-wide">{title}</h2>
        <div className="tb-dialog-body">{children}</div>
        <div className="tb-dialog-actions">
          <button type="button" className="tb-btn tb-btn-ghost" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
          {actions.map((action, index) => (
            <button
              key={action.label}
              ref={index === actions.length - 1 ? firstButton : undefined}
              type="button"
              className={`tb-btn ${
                action.tone === "danger" ? "tb-btn-danger" : action.tone === "plain" ? "tb-btn-outline" : "tb-btn-primary"
              }`}
              onClick={action.onClick}
              disabled={busy}
            >
              {action.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
