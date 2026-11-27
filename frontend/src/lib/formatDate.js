// Formats a "YYYY-MM-DD" date using the user's saved preference
export function formatDate(iso, dateFormat = "MM/DD/YYYY") {
  if (!iso) return "";

  const [year, month, day] = iso.split("-");

  if (dateFormat === "DD/MM/YYYY") {
    return `${day}/${month}/${year}`;
  }

  return `${month}/${day}/${year}`;
}
