// Ring chart of a trip's budget envelopes. Each segment takes its colour from data-category.
export default function BudgetDonut({ segments, size = 220, stroke = 26, label, children }) {
  const r = (size - stroke) / 2;
  const circumference = 2 * Math.PI * r;
  const sum = segments.reduce((s, seg) => s + seg.value, 0) || 1;
  const gap = segments.length > 1 ? 3 : 0;

  // Where each segment starts, worked out before rendering
  const starts = segments.reduce(
    (acc, seg) => [...acc, acc[acc.length - 1] + (seg.value / sum) * circumference],
    [0]
  );

  return (
    <div className="bp-donut" style={{ width: size, height: size }}>
      <svg viewBox={`0 0 ${size} ${size}`} role="img" aria-label={label}>
        <circle className="bp-donut-track" cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke} />
        {segments.map((seg, i) => {
          const length = (seg.value / sum) * circumference;
          return (
            <circle
              key={seg.key}
              data-category={seg.category}
              className="bp-donut-seg"
              cx={size / 2}
              cy={size / 2}
              r={r}
              fill="none"
              strokeWidth={stroke}
              strokeDasharray={`${Math.max(length - gap, 0.01)} ${circumference}`}
              strokeDashoffset={-starts[i]}
              transform={`rotate(-90 ${size / 2} ${size / 2})`}
            >
              <title>{seg.title}</title>
            </circle>
          );
        })}
      </svg>
      <div className="bp-donut-center">{children}</div>
    </div>
  );
}
