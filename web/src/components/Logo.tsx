/** Знак клиники: улыбка-дуга в скруглённом квадрате. */
export function Logo({ size = 40 }: { size?: number }) {
  return (
    <svg className="logo" width={size} height={size} viewBox="0 0 64 64" aria-hidden="true">
      <rect width="64" height="64" rx="18" fill="var(--teal-700)" />
      <path d="M16 30c4 12 28 12 32 0" fill="none" stroke="#fff" strokeWidth="6" strokeLinecap="round" />
      <circle cx="22" cy="21" r="3.5" fill="var(--mint-200)" />
      <circle cx="42" cy="21" r="3.5" fill="var(--mint-200)" />
    </svg>
  );
}
