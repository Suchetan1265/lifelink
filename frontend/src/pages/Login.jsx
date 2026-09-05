import { useCallback, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { homePathFor, useAuth } from '../auth/AuthContext';
import AuthLayout from '../components/AuthLayout';
import GoogleButton from '../components/GoogleButton';
import { auth } from '../api/endpoints';

export default function Login() {
  const { user, loading, signIn, startSession } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [revealed, setRevealed] = useState(false);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  if (loading) return <p className="muted center">Loading…</p>;
  if (user) return <Navigate to={homePathFor(user.role, user.profileComplete)} replace />;

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const profile = await signIn(email, password);
      navigate(location.state?.from?.pathname || homePathFor(profile.role, profile.profileComplete), { replace: true });
    } catch (loginError) {
      setError(errorMessage(loginError, 'Could not sign in'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleGoogle = useCallback(
    async (credential) => {
      setError(null);
      try {
        const profile = await startSession(await auth.google(credential));
        navigate(homePathFor(profile.role, profile.profileComplete), { replace: true });
      } catch (googleError) {
        setError(errorMessage(googleError, 'Could not sign in with Google'));
      }
    },
    [navigate, startSession],
  );

  return (
    <AuthLayout>
      <h2>Welcome back</h2>
      <p className="sub">Sign in to keep making a difference.</p>

      <form onSubmit={handleSubmit}>
        <div className="field">
          <label className="field-label" htmlFor="email">
            Email
          </label>
          <span className="input-wrap">
            <svg
              className="lead-icon"
              width="17"
              height="17"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <rect x="2" y="4" width="20" height="16" rx="2" />
              <path d="m2 7 10 6 10-6" />
            </svg>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@example.com"
              required
              autoComplete="username"
              autoFocus
            />
          </span>
        </div>

        <div className="field">
          <div className="label-row">
            <label className="field-label" htmlFor="password">
              Password
            </label>
            <Link className="forgot" to="/forgot-password">
              Forgot password?
            </Link>
          </div>
          <span className="input-wrap">
            <svg
              className="lead-icon"
              width="17"
              height="17"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <rect x="4" y="10" width="16" height="11" rx="2" />
              <path d="M8 10V7a4 4 0 0 1 8 0v3" />
            </svg>
            <input
              id="password"
              type={revealed ? 'text' : 'password'}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="••••••••"
              required
              autoComplete="current-password"
            />
            <button
              type="button"
              className="reveal"
              onClick={() => setRevealed((shown) => !shown)}
              aria-label={revealed ? 'Hide password' : 'Show password'}
            >
              <svg
                width="17"
                height="17"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7-10-7-10-7Z" />
                <circle cx="12" cy="12" r="3" />
                {!revealed && <path d="m3 3 18 18" />}
              </svg>
            </button>
          </span>
        </div>

        {error && <p className="error">{error}</p>}

        <button type="submit" className="button pill block" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Log in'}
          {!submitting && <span aria-hidden="true">&rarr;</span>}
        </button>
      </form>

      <GoogleButton onCredential={handleGoogle} disabled={submitting} />

      <p className="auth-alt">
        Don&rsquo;t have an account? <Link to="/register">Sign up</Link>
      </p>

      <p className="demo-hint">
        <strong>Try it without registering.</strong> Sign in as{' '}
        <code>donor1@lifelink.local</code>, <code>hospital@lifelink.local</code> or{' '}
        <code>bloodbank@lifelink.local</code>, password <code>password123</code>.
      </p>
    </AuthLayout>
  );
}
