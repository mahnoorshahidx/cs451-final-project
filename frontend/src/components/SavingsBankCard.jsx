import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Landmark, Plus, Search, X } from "lucide-react";
import { connectBankAccount, disconnectBankAccount, listBankAccounts } from "../api/api";
import { bankFor, DEMO_BANKS } from "../lib/demoBanks";

// Bank connection for the Savings page: connect a demo bank or enter details by hand.
// onAccountChange(account | null) tells the page which account savings can come from.

function BankDialog({ mode, busy, error, onClose, onConnect }) {
  const [query, setQuery] = useState("");
  const [manual, setManual] = useState({ accountType: "CHECKING", routingNumber: "", accountNumber: "" });
  const firstField = useRef(null);

  useEffect(() => {
    firstField.current?.focus();
    const onKey = (e) => e.key === "Escape" && !busy && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [busy, onClose]);

  const matches = DEMO_BANKS.filter((b) => b.name.toLowerCase().includes(query.trim().toLowerCase()));
  // Number fields keep digits only
  const set = (field) => (e) => {
    const value = field === "accountType" ? e.target.value : e.target.value.replace(/\D/g, "");
    setManual((m) => ({ ...m, [field]: value }));
  };

  return (
    <div className="tb-dialog-backdrop" onMouseDown={() => !busy && onClose()}>
      <div
        className="tb-dialog sv-bank-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="sv-bank-title"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="sv-dialog-head">
          <h2 id="sv-bank-title" className="tb-wide">{mode === "search" ? "Find your bank" : "Add account details"}</h2>
          <button type="button" className="sv-icon-btn" onClick={onClose} disabled={busy} aria-label="Close">
            <X size={18} aria-hidden="true" />
          </button>
        </div>

        {mode === "search" ? (
          <>
            <label className="sv-search">
              <Search size={18} aria-hidden="true" />
              <span className="bp-sr-only">Search banks</span>
              <input
                ref={firstField}
                className="tb-input"
                placeholder="Search for your bank"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
            </label>
            <ul className="sv-bank-list">
              {matches.map((b) => (
                <li key={b.name}>
                  <button type="button" className="sv-bank-option" disabled={busy} onClick={() => onConnect({
                    accountType: "CHECKING",
                    routingNumber: b.routingNumber,
                    accountNumber: b.accountNumber,
                  })}>
                    <span className="sv-bank-logo" aria-hidden="true">{b.initial}</span>
                    <span>
                      <strong>{b.name}</strong>
                      <small>Demo checking account</small>
                    </span>
                  </button>
                </li>
              ))}
              {matches.length === 0 && <li className="sv-muted">No demo bank matches “{query}”. Try adding it manually.</li>}
            </ul>
          </>
        ) : (
          <form
            className="sv-manual"
            onSubmit={(e) => {
              e.preventDefault();
              onConnect(manual);
            }}
          >
            <label className="tb-field">
              Account type
              <select ref={firstField} className="tb-input" value={manual.accountType} onChange={set("accountType")}>
                <option value="CHECKING">Checking</option>
                <option value="SAVINGS">Savings</option>
              </select>
            </label>
            <label className="tb-field">
              Routing number
              <input className="tb-input" inputMode="numeric" maxLength={9} placeholder="9 digits" value={manual.routingNumber} onChange={set("routingNumber")} />
            </label>
            <label className="tb-field">
              Account number
              <input className="tb-input" inputMode="numeric" maxLength={17} placeholder="4 to 17 digits" value={manual.accountNumber} onChange={set("accountNumber")} />
            </label>
            <button type="submit" className="tb-btn tb-btn-primary" disabled={busy}>
              {busy ? "Connecting..." : "Add account"}
            </button>
          </form>
        )}

        {error && <p role="alert" className="error-text">{error}</p>}
        <p className="sv-muted sv-demo-note">Demo mode: nothing connects to a real bank.</p>
      </div>
    </div>
  );
}

export default function SavingsBankCard({ onAccountChange, onLogSavings }) {
  const [account, setAccount] = useState(undefined); // undefined while loading
  const [dialog, setDialog] = useState(""); // "" | "search" | "manual"
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    listBankAccounts()
      .then((accounts) => {
        setAccount(accounts[0] ?? null);
        onAccountChange?.(accounts[0] ?? null);
      })
      .catch((err) => {
        setAccount(null);
        setError(err.message);
      });
  }, [onAccountChange]);

  async function connect(details) {
    setBusy(true);
    setError("");
    try {
      const saved = await connectBankAccount(details);
      setAccount(saved);
      onAccountChange?.(saved);
      setDialog("");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function disconnect() {
    setBusy(true);
    setError("");
    try {
      await disconnectBankAccount(account.id);
      setAccount(null);
      onAccountChange?.(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  const bank = useMemo(() => (account ? bankFor(account) : null), [account]);
  const closeDialog = useCallback(() => {
    setDialog("");
    setError("");
  }, []);

  // A slim navy strip: who you bank with and the balance on the left, actions on the right
  return (
    <section className="sv-bank-strip" aria-label="Bank account">
      <span className="sv-bank-icon" aria-hidden="true"><Landmark size={20} /></span>

      {account === undefined ? (
        <p className="sv-strip-text" role="status">Loading your bank account...</p>
      ) : account ? (
        <>
          <div className="sv-strip-text">
            <strong>
              {bank.name} {account.accountType === "SAVINGS" ? "Savings" : "Checking"} ••{account.accountLast4}
              <span className="sv-demo-chip">Demo</span>
            </strong>
            <span>
              {bank.balance != null
                ? <><span className="tb-num">{bank.balance.toLocaleString("en-US", { style: "currency", currency: "USD" })}</span> available</>
                : "Connected"}
            </span>
          </div>
          <div className="sv-strip-actions">
            <button type="button" className="tb-btn tb-btn-accent" onClick={onLogSavings}>Move money to a trip</button>
            <button type="button" className="tb-btn sv-btn-on-navy" onClick={disconnect} disabled={busy}>
              {busy ? "Disconnecting..." : "Disconnect"}
            </button>
          </div>
        </>
      ) : (
        <>
          <div className="sv-strip-text">
            <strong id="sv-bank-heading">Connect a bank account</strong>
            <span>See your balance and move money to your trips. Demo banks only.</span>
          </div>
          <div className="sv-strip-actions">
            <button type="button" className="tb-btn tb-btn-accent" onClick={() => setDialog("search")}>
              <Plus size={16} aria-hidden="true" /> Connect bank
            </button>
            <button type="button" className="tb-btn sv-btn-on-navy" onClick={() => setDialog("manual")}>Add manually</button>
          </div>
        </>
      )}
      {error && !dialog && <p role="alert" className="sv-bank-error">{error}</p>}

      {dialog && (
        <BankDialog mode={dialog} busy={busy} error={error} onClose={closeDialog} onConnect={connect} />
      )}
    </section>
  );
}
