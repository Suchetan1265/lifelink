import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { homePathFor, useAuth } from '../auth/AuthContext';
import AuthLayout from '../components/AuthLayout';

export default function Login() {
  const { user, loading, signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  if (loading) return <p className="muted center">Loading…</p>;
  if (user) return <Navigate to={homePathFor(user.role)} replace />;

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const profile = await signIn(email, password);
      navigate(location.state?.from?.pathname || homePathFor(profile.role), { replace: true });
    } catch (loginError) {
      setError(errorMessage(loginError, 'Could not sign in'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthLayout>
      <h2>Sign in</h2>
      <p className="sub">Donors, hospitals, blood banks and administrators, one door.</p>

      <form onSubmit={handleSubmit}>
        <label>
          Email
          <input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
            autoComplete="username"
            autoFocus
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
            autoComplete="current-password"
          />
        </label>

        {error && <p className="error">{error}</p>}

        <button type="submit" className="button block" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>

      <p className="auth-alt">
        No account yet? <Link to="/register">Register as a donor, hospital or blood bank</Link>
      </p>

      <p className="demo-hint">
        <strong>Demo accounts.</strong> Sign in as <code>donor1@lifelink.local</code>,{' '}
        <code>hospital@lifelink.local</code> or <code>bloodbank@lifelink.local</code> with the
        password <code>password123</code>.
      </p>
    </AuthLayout>
  );
}
