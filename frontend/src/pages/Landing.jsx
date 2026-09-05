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
      <p className="sub">
        Whether you give blood, need it, or hold it, LifeLink connects the three in one place.
      </p>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.7rem' }}>
        <Link className="button block" to="/register">
          Create an account
        </Link>
        <Link className="button ghost block" to="/login">
          I already have one
        </Link>
      </div>

      <p className="demo-hint">
        <strong>Just looking?</strong> Sign in as <code>donor1@lifelink.local</code> with the
        password <code>password123</code> to see the donor side, or{' '}
        <code>hospital@lifelink.local</code> to raise a request.
      </p>
    </AuthLayout>
  );
}
