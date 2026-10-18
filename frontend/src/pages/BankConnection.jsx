
import { useState } from "react";
import "../styles/BankConnection.css";

function BankConnection() {

  // Demo bank accounts
  const banks = [
    { name: "Chase", balance: 5284.20, lastFour: "4821", initial: "C" },
    { name: "Bank of America", balance: 3420.75, lastFour: "1934", initial: "B" },
    { name: "Wells Fargo", balance: 6750.00, lastFour: "6205", initial: "W" }
  ];

  // Remember selected bank after refreshing
  const [connectedBank, setConnectedBank] = useState(() => {
    const savedBank = localStorage.getItem("demoBank");
    return banks.find((bank) => bank.name === savedBank) || null;
  });

  // Show or hide bank options
  const [showBankOptions, setShowBankOptions] = useState(false);

  // Connect a demo bank
  function connectBank(bank) {
    setConnectedBank(bank);
    localStorage.setItem("demoBank", bank.name);
    setShowBankOptions(false);
  }

  // Disconnect bank
  function disconnectBank() {
    setConnectedBank(null);
    localStorage.removeItem("demoBank");
  }

  return (
    <section className="bank-card">

      {/* Card Header */}
      <div className="bank-card-header">
        <h2>Connected account</h2>

        <span className={`bank-status ${connectedBank ? "connected" : ""}`}>
          {connectedBank ? "Connected · Demo" : "Not connected"}
        </span>
      </div>

      {/* Connected account */}
      {connectedBank ? (
        <>
          <div className="bank-account-info">

            <div className="bank-logo">
              {connectedBank.initial}
            </div>

            <div className="bank-name">
              <h3>{connectedBank.name} Checking</h3>
              <p>•••• {connectedBank.lastFour}</p>
            </div>

            <div className="bank-balance">
              <p>Available</p>
              <h3>
                ${connectedBank.balance.toLocaleString("en-US", {
                  minimumFractionDigits: 2,
                  maximumFractionDigits: 2
                })}
              </h3>
            </div>

          </div>

          <div className="smart-saving">
            <h4>✦ Smart saving is active</h4>
            <p>$125 moves to travel savings every Friday (simulated)</p>
          </div>

          <button
            className="bank-action-btn"
            onClick={disconnectBank}
          >
            Disconnect demo account
          </button>
        </>
      ) : showBankOptions ? (

        /* Select demo bank */
        <div className="bank-selection">
          <p>Choose a bank to connect for your demo.</p>

          <div className="bank-options">
            {banks.map((bank) => (
              <button
                key={bank.name}
                className="bank-option"
                onClick={() => connectBank(bank)}
              >
                <span className="bank-option-logo">
                  {bank.initial}
                </span>

                <span className="bank-option-name">
                  <strong>{bank.name}</strong>
                  <small>Demo checking account</small>
                </span>

                <span className="bank-option-arrow">›</span>
              </button>
            ))}
          </div>

          <button
            className="bank-cancel-btn"
            onClick={() => setShowBankOptions(false)}
          >
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

          <button
            className="bank-connect-btn"
            onClick={() => setShowBankOptions(true)}
          >
            + Connect Bank
          </button>

        </div>
      )}

      <p className="bank-demo-note">
        Demo only — no real banking information is accessed.
      </p>

    </section>
  );
}

export default BankConnection;
