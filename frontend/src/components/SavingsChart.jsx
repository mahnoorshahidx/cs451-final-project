import { addDays, savingsForecast, shortDate, toUtc } from "../lib/tripMoney";

// Savings over time: a solid line of what's been saved, a dashed line at the goal, and a dotted
// "at this pace" line from today to the last target date. Built as plain SVG (no chart library).
//
// entries: [{ date: "YYYY-MM-DD", amount }], goal: number, endDate: "YYYY-MM-DD", today, money(n)

const W = 640;
const H = 220;
const PAD = { top: 18, right: 16, bottom: 30, left: 16 };

export default function SavingsChart({ entries, goal, endDate, today, money }) {
  const { points, saved, rate, atEnd } = savingsForecast(entries, goal, endDate, today);
  if (points.length === 0) {
    return (
      <div className="sv-chart-empty">
        <span>Your savings over time will show up here once you add money to a trip.</span>
      </div>
    );
  }

  const start = points[0].date < today ? points[0].date : addDays(today, -14);
  const end = endDate > today ? endDate : addDays(today, 7);
  const t0 = toUtc(start);
  const t1 = toUtc(end);
  const maxY = Math.max(goal, saved, atEnd ?? 0) * 1.12 || 1;

  const x = (iso) => PAD.left + ((toUtc(iso) - t0) / (t1 - t0 || 1)) * (W - PAD.left - PAD.right);
  const y = (v) => H - PAD.bottom - (v / maxY) * (H - PAD.top - PAD.bottom);

  // Step line: each contribution lifts the line on its date; it stays flat until today
  let d = `M ${x(start)} ${y(0)}`;
  let last = 0;
  for (const p of points) {
    d += ` L ${x(p.date)} ${y(last)} L ${x(p.date)} ${y(p.value)}`;
    last = p.value;
  }
  d += ` L ${x(today)} ${y(last)}`;
  const area = `${d} L ${x(today)} ${y(0)} Z`;

  const label = `Saved ${money(saved)} of ${money(goal)}.` + (atEnd ? ` At this pace, about ${money(atEnd)} by ${shortDate(end)}.` : "");

  return (
    <svg className="sv-chart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label={label}>
      {/* goal */}
      {goal > 0 && (
        <>
          <line className="sv-chart-goal" x1={PAD.left} x2={W - PAD.right} y1={y(goal)} y2={y(goal)} />
          <text className="sv-chart-label" x={W - PAD.right} y={y(goal) - 6} textAnchor="end">Goal {money(goal)}</text>
        </>
      )}
      {/* saved so far */}
      <path className="sv-chart-area" d={area} />
      <path className="sv-chart-line" d={d} />
      {/* at this pace */}
      {rate && atEnd != null && (
        <line className="sv-chart-pace" x1={x(today)} y1={y(saved)} x2={x(end)} y2={y(atEnd)} />
      )}
      <circle className="sv-chart-dot" cx={x(today)} cy={y(saved)} r="5" />
      {/* axis */}
      <line className="sv-chart-axis" x1={PAD.left} x2={W - PAD.right} y1={H - PAD.bottom} y2={H - PAD.bottom} />
      <text className="sv-chart-label" x={PAD.left} y={H - 8}>{shortDate(start)}</text>
      {x(today) - PAD.left > 70 && W - PAD.right - x(today) > 70 && (
        <text className="sv-chart-label" x={x(today)} y={H - 8} textAnchor="middle">Today</text>
      )}
      <text className="sv-chart-label" x={W - PAD.right} y={H - 8} textAnchor="end">{shortDate(end)}</text>
    </svg>
  );
}
