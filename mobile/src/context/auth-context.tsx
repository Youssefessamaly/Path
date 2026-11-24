import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

import {
  ApiError,
  getCurrentUser,
  login,
  registerSessionExpiredHandler,
  type CurrentUserResponse,
  type LoginPayload,
} from '@/lib/api';
import { getAccessToken, removeAccessToken, storeAccessToken } from '@/lib/auth-token';

export type AuthUser = CurrentUserResponse;

export type AuthContextValue = {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  isInitializing: boolean;
  /** True once a signed-in request's own token was rejected as invalid/expired — distinct from
   *  an explicit, user-initiated signOut(). The root navigator reacts to this to land the user
   *  on Login with an explanatory message, then clears it via clearSessionExpired(). */
  sessionExpired: boolean;
  signIn: (payload: LoginPayload) => Promise<AuthUser>;
  restoreSession: () => Promise<void>;
  signOut: () => Promise<void>;
  clearSessionExpired: () => void;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isInitializing, setIsInitializing] = useState(true);
  const [sessionExpired, setSessionExpired] = useState(false);
  // Debounces a burst of near-simultaneous requests that all fail against the same dead token
  // down to a single reaction, without needing to compare against possibly-stale closed-over
  // state. Reset whenever a fresh, valid session begins.
  const expiryHandledRef = useRef(false);

  const restoreSession = useCallback(async () => {
    try {
      let storedToken: string | null;
      try {
        storedToken = await getAccessToken();
      } catch {
        // SecureStore itself failed to read — nothing was actually retrieved,
        // so there is nothing to delete. Just treat this run as unauthenticated.
        setUser(null);
        setToken(null);
        return;
      }

      if (!storedToken) {
        setUser(null);
        setToken(null);
        return;
      }

      try {
        const currentUser = await getCurrentUser(storedToken);
        setToken(storedToken);
        setUser(currentUser);
      } catch (error) {
        if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
          // Token is no longer valid — clear it so we don't keep retrying with it.
          await removeAccessToken();
        }
        // Any other failure (network, server error) is treated as "not signed in for
        // now" without discarding a token that might still be valid once reachable.
        setUser(null);
        setToken(null);
      }
    } finally {
      setIsInitializing(false);
    }
  }, []);

  useEffect(() => {
    restoreSession();
  }, [restoreSession]);

  const signIn = useCallback(async (payload: LoginPayload): Promise<AuthUser> => {
    const session = await login(payload);
    await storeAccessToken(session.accessToken);

    const currentUser = await getCurrentUser(session.accessToken);
    expiryHandledRef.current = false;
    setToken(session.accessToken);
    setUser(currentUser);

    return currentUser;
  }, []);

  const clearLocalSession = useCallback(async () => {
    try {
      await removeAccessToken();
    } finally {
      setUser(null);
      setToken(null);
    }
  }, []);

  const signOut = useCallback(async () => {
    await clearLocalSession();
  }, [clearLocalSession]);

  const clearSessionExpired = useCallback(() => {
    setSessionExpired(false);
  }, []);

  const handleSessionExpired = useCallback(() => {
    if (expiryHandledRef.current) {
      return;
    }
    expiryHandledRef.current = true;
    void clearLocalSession();
    setSessionExpired(true);
  }, [clearLocalSession]);

  useEffect(() => {
    registerSessionExpiredHandler(handleSessionExpired);
    return () => registerSessionExpiredHandler(null);
  }, [handleSessionExpired]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      token,
      isAuthenticated: Boolean(user && token),
      isInitializing,
      sessionExpired,
      signIn,
      restoreSession,
      signOut,
      clearSessionExpired,
    }),
    [user, token, isInitializing, sessionExpired, signIn, restoreSession, signOut, clearSessionExpired],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
