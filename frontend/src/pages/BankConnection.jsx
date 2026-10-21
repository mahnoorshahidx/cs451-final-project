import { useEffect, useState } from "react";
import "../styles/BankConnection.css";
import {
  connectBankAccount,
  disconnectBankAccount,
  listBankAccounts,
} from "../api/api";

// Now connects through the backend instead of localStorage (SCRUM-34).
// Demo banks. Each routing number is real and passes the backend's checksum.
// The account numbers are made up. Balances are display-only (the backend doesn't track them).
const banks = [
  { name: "Chase", routingNumber: "021000021", accountNumber: "000004821", balance: 5284.2, initial: "C" },
  { name: "Bank of America", routingNumber: "026009593", accountNumber: "000001934", balance: 3420.75, initial: "B" },
  { name: "Wells Fargo", routingNumber: "121000248", accountNumber: "000006205", balance: 6750.0, initial: "W" },
];

// Match a saved account back to its demo bank for the name/logo
const bankFor = (account) =>
  banks.find((b) => b.routingNumber === account.routingNumber) ?? {
    name: "Bank",
    initial: "B",
    balance: null,
  };

function BankConnection() {
  const [account, setAccount] = useState(null); // the connected account from the backend
  const [showBankOptions, setShowBankOptions] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  // Load the user's connected account from the backend, so it survives refreshes and other devices
  useEffect(() => {
    listBankAccounts()
      .then((accounts) => setAccount(accounts[0] ?? null))
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  async function connectBank(bank) {
    setError("");
    setBusy(true);
    try {
      const saved = await connectBankAccount({
        accountType: "CHECKING",
        routingNumber: bank.routingNumber,
        accountNumber: bank.accountNumber,
      });
      setAccount(saved);
      setShowBankOptions(false);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function disconnectBank() {
    setError("");
    setBusy(true);
    try {
      await disconnectBankAccount(account.id);
      setAccount(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  const bank = account ? bankFor(account) : null;
  const typeLabel = account?.accountType === "SAVINGS" ? "Savings" : "Checking";

  return (
    <section className="bank-card">

      {/* Card Header */}
      <div className="bank-card-header">
        <h2>Connected account</h2>

        <span className={`bank-status ${account ? "connected" : ""}`}>
          {account ? "Connected · Demo" : "Not connected"}
        </span>
      </div>

      {loading ? (
        <p>Loading...</p>
      ) : account ? (
        <>
          <div className="bank-account-info">

            <div className="bank-logo">{bank.initial}</div>

            <div className="bank-name">
              <h3>{bank.name} {typeLabel}</h3>
              <p>•••• {account.accountLast4}</p>
            </div>

            {bank.balance !== null && (
              <div className="bank-balance">
                <p>Available</p>
                <h3>
                  ${bank.balance.toLocaleString("en-US", {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2,
                  })}
                </h3>
              </div>
            )}

          </div>

          <div className="smart-saving">
            <h4>✦ Smart saving is active</h4>
            <p>$125 moves to travel savings every Friday (simulated)</p>
          </div>

          <button className="bank-action-btn" onClick={disconnectBank} disabled={busy}>
            {busy ? "Disconnecting..." : "Disconnect demo account"}
          </button>
        </>
      ) : showBankOptions ? (

        /* Select demo bank */
        <div className="bank-selection">
          <p>Choose a bank to connect for your demo.</p>

          <div className="bank-options">
            {banks.map((b) => (
              <button
                key={b.name}
                className="bank-option"
                onClick={() => connectBank(b)}
                disabled={busy}
              >
                <span className="bank-option-logo">{b.initial}</span>

                <span className="bank-option-name">
                  <strong>{b.name}</strong>
                  <small>Demo checking account</small>
                </span>

                <span className="bank-option-arrow">›</span>
              </button>
            ))}
          </div>

          <button className="bank-cancel-btn" onClick={() => setShowBankOptions(false)}>
            Cancel
          </button>
        </div>

      ) : (

        /* No connected account */
        <div className="bank-empty-state">

          <div className="bank-empty-icon">🏦</div>

          <h3>No bank account connected</h3>

          <p>
            Connect a demo bank account to view your balance
            and manage your travel savings.
          </p>

          <button className="bank-connect-btn" onClick={() => setShowBankOptions(true)}>
            + Connect Bank
          </button>

        </div>
      )}

      {error && <p className="error-text">{error}</p>}

      <p className="bank-demo-note">
        Demo only — no real banking information is accessed.
      </p>

    </section>
  );
}

export default BankConnection;