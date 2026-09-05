import { Link } from 'react-router-dom';
import Mark from './Mark';

/**
 * The signed-out shell: a crimson pitch panel beside the form.
 *
 * The pulse trace is drawn rather than an image so it stays crisp, themes with
 * the panel, and costs nothing to load.
 */
export default function AuthLayout({ children }) {
  return (
    <div className="auth">
      <aside className="auth-pitch">
        <Link to="/" className="auth-brand" style={{ color: 'inherit', textDecoration: 'none' }}>
          <Mark size={26} />
          LifeLink
        </Link>

        <svg className="auth-pulse" viewBox="0 0 600 140" preserveAspectRatio="none" aria-hidden="true">
          <path d="M0 70 H150 l14 -46 14 92 16 -70 12 24 h22 l10 -34 12 58 14 -30 h40 l12 -52 14 104 16 -78 12 26 h30 l10 -20 12 40 14 -20 H600" />
        </svg>

        <div>
          <h1>Blood, found in minutes.</h1>
          <p className="lead">
            Hospitals post what they need. Nearby donors with a compatible group hear about it in
            seconds. Anything nobody confirms in time goes to the blood banks.
          </p>
          <div className="auth-stats">
            <div>
              <strong>2 hrs</strong>
              <span>Critical escalation window</span>
            </div>
            <div>
              <strong>8</strong>
              <span>Blood groups matched</span>
            </div>
            <div>
              <strong>90 days</strong>
              <span>Donor recovery, tracked</span>
            </div>
          </div>
        </div>
      </aside>

      <main className="auth-form">
        <div className="auth-form-inner">{children}</div>
      </main>
    </div>
  );
}
