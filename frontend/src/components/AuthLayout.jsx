import { Link } from 'react-router-dom';
import Mark from './Mark';

const PILLARS = [
  {
    label: 'Find\ndonors',
    icon: (
      <>
        <path d="M16 20v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
        <circle cx="9" cy="7" r="4" />
        <path d="M22 20v-2a4 4 0 0 0-3-3.87M16 3.13A4 4 0 0 1 16 11" />
      </>
    ),
  },
  {
    label: 'Support\nhospitals',
    icon: (
      <>
        <path d="M4 21V8l8-5 8 5v13" />
        <path d="M10 21v-5h4v5" />
        <path d="M12 8v4M10 10h4" />
      </>
    ),
  },
  {
    label: 'Save\nlives',
    icon: <path d="M12 3s6 6.5 6 10a6 6 0 0 1-12 0c0-3.5 6-10 6-10Z" />,
  },
];

/**
 * The signed-out shell.
 *
 * The line is one continuous stroke that loops into a heart, drawn once on
 * load. Drawn rather than photographed so it themes with the panel, stays
 * crisp at any size, and costs nothing to download.
 */
export default function AuthLayout({ children, motto = true }) {
  return (
    <div className="auth">
      <div className="auth-card">
        <aside className="auth-pitch">
          <Link to="/" className="auth-logo" style={{ textDecoration: 'none', color: 'inherit' }}>
            <Mark size={38} />
            <span>
              <strong>LifeLink</strong>
              <span>People &middot; Blood &middot; Better tomorrows</span>
            </span>
          </Link>

          <svg className="auth-line" viewBox="0 0 440 400" fill="none" aria-hidden="true">
            <path
              d="M0 362 C 70 352, 140 337, 190 318 C 215 310, 235 305, 250 300
                 C 228 288, 205 275, 205 255 C 205 230, 233 227, 250 245
                 C 267 227, 295 230, 295 255 C 295 275, 272 288, 250 300
                 C 300 292, 342 250, 380 180 C 401 143, 416 96, 426 34"
            />
          </svg>

          <div className="auth-body">
            <h1>
              A healthier <em>tomorrow</em>, together
            </h1>
            <p className="lead">
              Connecting willing donors to the hospitals and blood banks that need them, at the
              moment they need them.
            </p>
          </div>

          <div>
            <div className="auth-pillars">
              {PILLARS.map((pillar) => (
                <div key={pillar.label}>
                  <svg
                    width="21"
                    height="21"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.7"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                  >
                    {pillar.icon}
                  </svg>
                  <span style={{ whiteSpace: 'pre-line' }}>{pillar.label}</span>
                </div>
              ))}
            </div>
            <p className="auth-quote" style={{ marginTop: '1.5rem' }}>
              &ldquo;Small acts, big impact.&rdquo;
            </p>
          </div>
        </aside>

        <main className="auth-form">
          {motto && (
            <p className="auth-motto">
              Give blood
              <br />
              Give hope
            </p>
          )}
          <div className="auth-form-inner">{children}</div>
        </main>
      </div>
    </div>
  );
}
