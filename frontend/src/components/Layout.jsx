import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications as notificationsApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import Mark from './Mark';

const NAV_BY_ROLE = {
  DONOR: [
    { to: '/donor', label: 'Dashboard', end: true },
    { to: '/donor/opportunities', label: 'Where I can donate' },
    { to: '/donor/history', label: 'History' },
  ],
  HOSPITAL: [
    { to: '/hospital/requests', label: 'Requests', end: true },
    { to: '/hospital/requests/new', label: 'Raise request' },
  ],
  BLOOD_BANK: [
    { to: '/bank/escalations', label: 'Escalations' },
    { to: '/bank/inventory', label: 'Inventory' },
  ],
  ADMIN: [
    { to: '/admin/verifications', label: 'Verifications' },
    { to: '/admin/stats', label: 'Stats' },
  ],
};

const ROLE_LABEL = {
  DONOR: 'Donor',
  HOSPITAL: 'Hospital',
  BLOOD_BANK: 'Blood bank',
  ADMIN: 'Administrator',
};

/** Opens on hover; the panel stays reachable because it is a child of .bell. */
function NotificationBell() {
  const queryClient = useQueryClient();

  const { data = [] } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => notificationsApi.list(false),
    refetchInterval: 30_000,
  });

  const markRead = useMutation({
    mutationFn: notificationsApi.markRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });

  const unreadCount = data.filter((notification) => !notification.read).length;

  return (
    <div className="bell">
      <button type="button" className="bell-button" aria-haspopup="true">
        <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" aria-hidden="true">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M13.7 21a2 2 0 0 1-3.4 0" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        Notifications
        {unreadCount > 0 && <span className="badge">{unreadCount}</span>}
      </button>

      <div className="bell-panel">
        {data.length === 0 && <p className="muted center" style={{ padding: '1.5rem 0' }}>Nothing yet.</p>}
        {data.map((notification) => (
          <article key={notification.id} className={notification.read ? 'note' : 'note unread'}>
            <header>
              <strong>{notification.title}</strong>
              {!notification.read && (
                <button type="button" className="link" onClick={() => markRead.mutate(notification.id)}>
                  Mark read
                </button>
              )}
            </header>
            <p>{notification.body}</p>
            <time>{new Date(notification.createdAt).toLocaleString()}</time>
          </article>
        ))}
      </div>
    </div>
  );
}

export default function Layout() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const links = NAV_BY_ROLE[user?.role] ?? [];

  const handleSignOut = async () => {
    await signOut();
    navigate('/login', { replace: true });
  };

  return (
    <div className="shell">
      <header className="topbar">
        <span className="brand">
          <Mark />
          LifeLink
        </span>

        <nav>
          {links.map((link) => (
            <NavLink key={link.to} to={link.to} end={link.end}>
              {link.label}
            </NavLink>
          ))}
        </nav>

        <div className="topbar-right">
          <NotificationBell />
          <span className="who">
            <strong>{user?.name || user?.email}</strong>
            <span>{ROLE_LABEL[user?.role] ?? ''}</span>
          </span>
          <button type="button" className="button ghost small" onClick={handleSignOut}>
            Sign out
          </button>
        </div>
      </header>

      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
