import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { homePathFor, useAuth } from './AuthContext';

/**
 * Guards a branch of the router. Roles are enforced server-side too; this only
 * keeps people out of screens that would fail for them anyway.
 */
export default function RequireRole({ roles }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <p className="muted center">Loading…</p>;
  }
  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  if (roles && !roles.includes(user.role)) {
    return <Navigate to={homePathFor(user.role)} replace />;
  }
  return <Outlet />;
}
