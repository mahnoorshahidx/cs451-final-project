// Demo banks for the class project. Each routing number is real and passes the backend's checksum.
// The account numbers are made up. Balances are display-only (the backend doesn't track them).
export const DEMO_BANKS = [
  { name: "Chase", routingNumber: "021000021", accountNumber: "000004821", balance: 5284.2, initial: "C" },
  { name: "Bank of America", routingNumber: "026009593", accountNumber: "000001934", balance: 3420.75, initial: "B" },
  { name: "Wells Fargo", routingNumber: "121000248", accountNumber: "000006205", balance: 6750.0, initial: "W" },
];

// Match a saved account back to its demo bank for the name, logo and balance
export function bankFor(account) {
  return (
    DEMO_BANKS.find((b) => b.routingNumber === account.routingNumber) ?? {
      name: "Bank",
      initial: "B",
      balance: null,
    }
  );
}
