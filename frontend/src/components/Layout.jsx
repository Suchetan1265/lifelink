import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications as notificationsApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';

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

function NotificationBell() {
  const [open, setOpen] = useState(false);
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
      <button type="button" className="bell-button" onClick={() => setOpen((wasOpen) => !wasOpen)}>
        Notifications
        {unreadCount > 0 && <span className="badge">{unreadCount}</span>}
      </button>

      {open && (
        <div className="bell-panel">
          {data.length === 0 && <p className="muted center">Nothing yet.</p>}
          {data.map((notification) => (
            <article
              key={notification.id}
              className={notification.read ? 'note read' : 'note'}
            >
              <header>
                <strong>{notification.title}</strong>
                {!notification.read && (
                  <button
                    type="button"
                    className="link"
                    onClick={() => markRead.mutate(notification.id)}
                  >
                    Mark read
                  </button>
                )}
              </header>
              <p>{notification.body}</p>
              <time>{new Date(notification.createdAt).toLocaleString()}</time>
            </article>
          ))}
        </div>
      )}
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
        <span className="brand">LifeLink</span>
        <nav>
          {links.map((link) => (
            <NavLink key={link.to} to={link.to} end={link.end}>
              {link.label}
            </NavLink>
          ))}
        </nav>
        <div className="topbar-right">
          <NotificationBell />
          <span className="muted">{user?.name || user?.email}</span>
          <button type="button" className="link" onClick={handleSignOut}>
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
