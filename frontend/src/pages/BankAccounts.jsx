import Sidebar from "../components/Sidebar";
import "../styles/Dashboard.css";
import "../styles/BudgetPages.css";
import "../styles/BankAccounts.css";

import { useEffect, useState } from "react";
import {
  connectBankAccount,
  disconnectBankAccount,
  listBankAccounts,
} from "../api/api";
import { bankFor, DEMO_BANKS } from "../lib/demoBanks";

function BankAccounts() {
    const [accounts, setAccounts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [showBankOptions, setShowBankOptions] = useState(false);

    useEffect(() => {
        listBankAccounts()
        .then((data) => setAccounts(data))
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

        setAccounts((prev) => [...prev, saved]);
        setShowBankOptions(false);
        } catch (err) {
        setError(err.message);
        } finally {
        setBusy(false);
        }
    }

    async function disconnectBank(accountId) {
        setError("");
        setBusy(true);

        try {
        await disconnectBankAccount(accountId);

        setAccounts((prev) =>
            prev.filter((account) => account.id !== accountId)
        );
        } catch (err) {
        setError(err.message);
        } finally {
        setBusy(false);
        }
    }

      const totalBalance = accounts.reduce(
        (total, account) => total + (bankFor(account).balance ?? 0),
        0
    );

    const formatBalance = (amount) =>
        amount.toLocaleString("en-US", {
        style: "currency",
        currency: "USD",
        });
  return (
    <div className="dashboard-page bp-page">
      <aside className="dashboard-sidebar">
        <Sidebar />
      </aside>

      <main className="dashboard-main bp-main">
        <header className="bp-header">
          <div className="bp-title">
            <span className="tb-kicker">Bank Connection</span>
            <h1 className="tb-wide">Manage your bank accounts</h1>
            <p>
              Connect your accounts and save for your next adventure.
            </p>
          </div>
        </header>
                    {/* Total demo balance */}
        <section className="ba-overview">
          <span>Total available demo balance</span>

          <h2 className="tb-num">
            {loading ? "Loading..." : formatBalance(totalBalance)}
          </h2>

          <p>
            {accounts.length} connected {accounts.length === 1 ? "account" : "accounts"}
          </p>

          <button
            className="tb-btn tb-btn-accent"
            onClick={() => setShowBankOptions(!showBankOptions)}
            disabled={busy || loading}
          >
            + Connect bank
          </button>
        </section>
        {showBankOptions && (
        <section className="ba-bank-selection">
            <div className="ba-selection-header">
            <div>
                <h2 className="tb-wide">Choose your bank</h2>
                <p>Select a demo bank to connect.</p>
            </div>

            <button
                className="tb-btn tb-btn-outline"
                onClick={() => setShowBankOptions(false)}
                disabled={busy}
            >
                Cancel
            </button>
            </div>

            <div className="ba-bank-options">
            {DEMO_BANKS.map((bank) => (
                <button
                key={bank.name}
                className="ba-bank-option"
                onClick={() => connectBank(bank)}
                disabled={busy}
                >
                <span className="ba-logo">{bank.initial}</span>

                <span>
                    <strong>{bank.name}</strong>
                    <small>Demo checking account</small>
                </span>
                </button>
            ))}
            </div>

            <p className="ba-demo-note">
            Demo mode: no real bank accounts or money transfers.
            </p>
        </section>
)}
        {/* Connected accounts */}
        <section className="ba-accounts">
          <h2 className="tb-wide">Connected accounts</h2>

          {!loading && accounts.length === 0 && (
            <p>No bank accounts connected yet.</p>
          )}

          <div className="ba-grid">
            {accounts.map((account) => {
              const bank = bankFor(account);

              return (
                <article className="ba-card" key={account.id}>
                  <div className="ba-card-header">
                    <div className="ba-logo">{bank.initial}</div>

                    <div>
                      <h3>{bank.name}</h3>
                      <p>
                        {account.accountType === "SAVINGS"
                          ? "Savings"
                          : "Checking"} ••{account.accountLast4}
                      </p>
                    </div>
                  </div>

                  <p>Available demo balance</p>

                  <h2 className="tb-num">
                    {bank.balance != null
                      ? formatBalance(bank.balance)
                      : "Unavailable"}
                  </h2>

                  <button
                    className="tb-btn tb-btn-outline"
                    onClick={() => disconnectBank(account.id)}
                    disabled={busy}
                  >
                    Disconnect
                  </button>
                </article>
              );
            })}
          </div>
        </section>

        {error && <p role="alert" className="error-text">{error}</p>}
      </main>
    </div>
  );
}

export default BankAccounts;