export function Loading({ label = 'Loading…' }) {
  return <p className="muted center" style={{ padding: '3rem 0' }}>{label}</p>;
}

export function ErrorNote({ children }) {
  return <p className="error">{children}</p>;
}

export function Empty({ children }) {
  return (
    <div className="empty">
      <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
        <circle cx="12" cy="12" r="9" />
        <path d="M8.5 13.5a4 4 0 0 0 7 0" strokeLinecap="round" />
        <path d="M9 9.5h.01M15 9.5h.01" strokeLinecap="round" />
      </svg>
      <p>{children}</p>
    </div>
  );
}
