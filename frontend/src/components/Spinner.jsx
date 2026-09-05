export function Loading({ label = 'Loading…' }) {
  return <p className="muted center">{label}</p>;
}

export function ErrorNote({ children }) {
  return <p className="error">{children}</p>;
}

export function Empty({ children }) {
  return <p className="muted center">{children}</p>;
}
