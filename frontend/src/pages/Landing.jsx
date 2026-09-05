import { Link, Navigate } from 'react-router-dom';
import { homePathFor, useAuth } from '../auth/AuthContext';
import AuthLayout from '../components/AuthLayout';

export default function Landing() {
  const { user, loading } = useAuth();

  if (loading) return <p className="muted center">Loading…</p>;
  if (user) return <Navigate to={homePathFor(user.role)} replace />;

  return (
    <AuthLayout>
      <h2>Get started</h2>
      <p className="sub">Give blood, ask for it, or hold it. One place for all three.</p>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.7rem' }}>
        <Link className="button pill block" to="/register">
          Create an account
        </Link>
        <Link className="button ghost pill block" to="/login">
          I already have one
        </Link>
      </div>

      <p className="demo-hint">
        <strong>Just looking?</strong> Sign in as <code>donor1@lifelink.local</code> to see the
        donor side, or <code>hospital@lifelink.local</code> to raise a request. Password is{' '}
        <code>password123</code>.
      </p>
    </AuthLayout>
  );
}
