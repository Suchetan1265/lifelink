import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { auth } from '../api/endpoints';
import { setSessionExpiredHandler, tokenStore } from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  // Distinguishes "not logged in" from "we have a token but have not asked who
  // it belongs to yet", so guards do not bounce a returning user to the login page.
  const [loading, setLoading] = useState(Boolean(tokenStore.access()));

  const signOut = useCallback(() => {
    tokenStore.clear();
    setUser(null);
  }, []);

  useEffect(() => {
    setSessionExpiredHandler(signOut);
  }, [signOut]);

  useEffect(() => {
    if (!tokenStore.access()) {
      setLoading(false);
      return;
    }
    auth
      .me()
      .then(setUser)
      .catch(() => tokenStore.clear())
      .finally(() => setLoading(false));
  }, []);

  const startSession = useCallback(async (tokens) => {
    tokenStore.save(tokens);
    const profile = await auth.me();
    setUser(profile);
    return profile;
  }, []);

  const signIn = useCallback(
    async (email, password) => startSession(await auth.login({ email, password })),
    [startSession],
  );

  /** Re-reads the profile, e.g. after a Google user fills in their donor details. */
  const refresh = useCallback(async () => {
    const profile = await auth.me();
    setUser(profile);
    return profile;
  }, []);

  const signOutRemotely = useCallback(async () => {
    const refreshToken = tokenStore.refresh();
    if (refreshToken) {
      // Revoking server-side is best effort; the local session goes either way.
      await auth.logout(refreshToken).catch(() => {});
    }
    signOut();
  }, [signOut]);

  const value = useMemo(
    () => ({ user, loading, signIn, signOut: signOutRemotely, startSession, refresh }),
    [user, loading, signIn, signOutRemotely, startSession, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider');
  }
  return context;
}

/**
 * Where a signed-in user belongs. A Google account exists before its donor
 * details do, so those users go and finish setting up first.
 */
export function homePathFor(role, profileComplete = true) {
  if (role === 'DONOR' && !profileComplete) return '/donor/complete-profile';
  return homePathForRole(role);
}

function homePathForRole(role) {
  switch (role) {
    case 'DONOR':
      return '/donor';
    case 'HOSPITAL':
      return '/hospital/requests';
    case 'BLOOD_BANK':
      return '/bank/escalations';
    case 'ADMIN':
      return '/admin/verifications';
    default:
      return '/';
  }
}
