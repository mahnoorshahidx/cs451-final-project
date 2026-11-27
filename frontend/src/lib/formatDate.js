// Formats a "YYYY-MM-DD" date using the user's saved preference
export function formatDate(iso, dateFormat = "MM/DD/YYYY") {
  if (!iso) return "";

  const [year, month, day] = iso.split("-");

  if (dateFormat === "DD/MM/YYYY") {
    return `${day}/${month}/${year}`;
  }

  return `${month}/${day}/${year}`;
}

// Today's date as "YYYY-MM-DD" in the user's own time zone
export function todayIso() {
  const now = new Date();
  return [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, "0"),
    String(now.getDate()).padStart(2, "0"),
  ].join("-");
}
