import { Link, Navigate } from 'react-router-dom';
import { homePathFor, useAuth } from '../auth/AuthContext';

export default function Landing() {
  const { user, loading } = useAuth();

  if (loading) return <p className="muted center">Loading…</p>;
  if (user) return <Navigate to={homePathFor(user.role)} replace />;

  return (
    <div className="landing">
      <h1>LifeLink</h1>
      <p className="lead">
        Hospitals post what they need. Nearby donors with a compatible blood group hear
        about it in seconds. Anything nobody confirms in time goes to the blood banks.
      </p>
      <div className="row">
        <Link className="button" to="/login">
          Sign in
        </Link>
        <Link className="button ghost" to="/register">
          Create an account
        </Link>
      </div>
    </div>
  );
}
