import { act, renderHook, waitFor } from '@testing-library/react-native';

import { AuthProvider, useAuth } from '@/context/auth-context';
import { ApiError, apiGet, getCurrentUser, login, NetworkError } from '@/lib/api';
import { getAccessToken, removeAccessToken, storeAccessToken } from '@/lib/auth-token';

jest.mock('@/lib/auth-token', () => ({
  getAccessToken: jest.fn(),
  storeAccessToken: jest.fn(),
  removeAccessToken: jest.fn(),
}));

jest.mock('@/lib/api', () => {
  const actual = jest.requireActual('@/lib/api');
  return {
    ...actual,
    login: jest.fn(),
    getCurrentUser: jest.fn(),
  };
});

const mockGetAccessToken = getAccessToken as jest.Mock;
const mockStoreAccessToken = storeAccessToken as jest.Mock;
const mockRemoveAccessToken = removeAccessToken as jest.Mock;
const mockLogin = login as jest.Mock;
const mockGetCurrentUser = getCurrentUser as jest.Mock;

const INTERN_USER = { userId: 'intern-1', email: 'intern@example.com', role: 'INTERN' as const };
const COMPANY_USER = { userId: 'company-1', email: 'company@example.com', role: 'COMPANY' as const };

function mockFetchOnce(status: number, body: unknown = {}) {
  (globalThis.fetch as jest.Mock).mockResolvedValueOnce({
    ok: status >= 200 && status < 300,
    status,
    text: async () => JSON.stringify(body),
  });
}

beforeEach(() => {
  jest.clearAllMocks();
  globalThis.fetch = jest.fn();
  mockGetAccessToken.mockResolvedValue(null);
});

describe('restoreSession (app restart / cold start)', () => {
  it('restores an authenticated session when the stored token is still valid', async () => {
    mockGetAccessToken.mockResolvedValue('valid-token');
    mockGetCurrentUser.mockResolvedValue(INTERN_USER);

    const { result } = await renderHook(() => useAuth(), { wrapper: AuthProvider });

    await waitFor(() => expect(result.current.isInitializing).toBe(false));

    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.user).toEqual(INTERN_USER);
    expect(result.current.sessionExpired).toBe(false);
    expect(mockRemoveAccessToken).not.toHaveBeenCalled();
  });

  it('clears a stored token that the backend rejects as invalid (401) and does not pretend it is still authenticated', async () => {
    mockGetAccessToken.mockResolvedValue('stale-token');
    mockGetCurrentUser.mockRejectedValue(new ApiError(401, 'Invalid email or password.'));

    const { result } = await renderHook(() => useAuth(), { wrapper: AuthProvider });

    await waitFor(() => expect(result.current.isInitializing).toBe(false));

    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.user).toBeNull();
    expect(mockRemoveAccessToken).toHaveBeenCalledTimes(1);
  });

  it('does not discard a possibly-valid token on a transient network failure', async () => {
    mockGetAccessToken.mockResolvedValue('maybe-still-valid-token');
    mockGetCurrentUser.mockRejectedValue(new NetworkError());

    const { result } = await renderHook(() => useAuth(), { wrapper: AuthProvider });

    await waitFor(() => expect(result.current.isInitializing).toBe(false));

    expect(result.current.isAuthenticated).toBe(false);
    expect(mockRemoveAccessToken).not.toHaveBeenCalled();
  });
});

describe('mid-session expiry vs. ordinary request failures', () => {
  async function signInAs(user: typeof INTERN_USER | typeof COMPANY_USER) {
    mockLogin.mockResolvedValue({ accessToken: 'live-token', tokenType: 'Bearer', ...user });
    mockGetCurrentUser.mockResolvedValue(user);

    const hook = await renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(hook.result.current.isInitializing).toBe(false));

    await act(async () => {
      await hook.result.current.signIn({ email: user.email, password: 'secret' });
    });

    return hook;
  }

  it('signs out and flags sessionExpired when an authenticated request\'s own token is rejected (401)', async () => {
    const { result } = await signInAs(INTERN_USER);
    expect(result.current.isAuthenticated).toBe(true);

    mockFetchOnce(401);
    await act(async () => {
      await expect(apiGet('/intern/some-protected-resource', { token: 'live-token' })).rejects.toThrow(ApiError);
    });

    await waitFor(() => expect(result.current.isAuthenticated).toBe(false));
    expect(result.current.sessionExpired).toBe(true);
    expect(mockRemoveAccessToken).toHaveBeenCalled();
  });

  it('does NOT sign out on a 403 (authenticated, just not permitted) response', async () => {
    const { result } = await signInAs(COMPANY_USER);

    mockFetchOnce(403);
    await act(async () => {
      await expect(apiGet('/company/some-forbidden-resource', { token: 'live-token' })).rejects.toThrow(ApiError);
    });

    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.sessionExpired).toBe(false);
    expect(mockRemoveAccessToken).not.toHaveBeenCalled();
  });

  it('does NOT sign out on an ordinary 401 that never carried a token (e.g. a login attempt)', async () => {
    const { result } = await signInAs(INTERN_USER);

    mockFetchOnce(401);
    await act(async () => {
      // No token option at all — this is what an unauthenticated call (like login itself)
      // looks like to the request layer, never a session-expiry signal.
      await expect(apiGet('/some/public/endpoint')).rejects.toThrow(ApiError);
    });

    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.sessionExpired).toBe(false);
    expect(mockRemoveAccessToken).not.toHaveBeenCalled();
  });

  it('does NOT sign out on a network failure', async () => {
    const { result } = await signInAs(INTERN_USER);

    (globalThis.fetch as jest.Mock).mockRejectedValueOnce(new Error('offline'));
    await act(async () => {
      await expect(apiGet('/intern/some-resource', { token: 'live-token' })).rejects.toThrow(NetworkError);
    });

    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.sessionExpired).toBe(false);
  });

  it('preserves which role was signed in across an unrelated request failure', async () => {
    const { result } = await signInAs(COMPANY_USER);

    mockFetchOnce(500);
    await act(async () => {
      await expect(apiGet('/company/some-resource', { token: 'live-token' })).rejects.toThrow(ApiError);
    });

    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.user?.role).toBe('COMPANY');
  });
});

describe('explicit logout', () => {
  it('clears the session without flagging it as an expiry', async () => {
    mockLogin.mockResolvedValue({ accessToken: 'live-token', tokenType: 'Bearer', ...INTERN_USER });
    mockGetCurrentUser.mockResolvedValue(INTERN_USER);

    const { result } = await renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.isInitializing).toBe(false));

    await act(async () => {
      await result.current.signIn({ email: INTERN_USER.email, password: 'secret' });
    });
    expect(result.current.isAuthenticated).toBe(true);

    await act(async () => {
      await result.current.signOut();
    });

    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.sessionExpired).toBe(false);
    expect(mockRemoveAccessToken).toHaveBeenCalled();
  });

  it('allows signing back in after an explicit logout, and after a session expiry', async () => {
    mockLogin.mockResolvedValue({ accessToken: 'live-token', tokenType: 'Bearer', ...INTERN_USER });
    mockGetCurrentUser.mockResolvedValue(INTERN_USER);

    const { result } = await renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.isInitializing).toBe(false));

    await act(async () => {
      await result.current.signIn({ email: INTERN_USER.email, password: 'secret' });
    });
    await act(async () => {
      await result.current.signOut();
    });
    expect(result.current.isAuthenticated).toBe(false);

    // Sign in again — this must not be short-circuited by any leftover "already expired" guard.
    await act(async () => {
      await result.current.signIn({ email: INTERN_USER.email, password: 'secret' });
    });
    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.sessionExpired).toBe(false);

    // Now let that second session genuinely expire, and confirm the flag fires again (i.e.
    // the earlier logout didn't leave the one-shot debounce permanently tripped).
    mockFetchOnce(401);
    await act(async () => {
      await expect(apiGet('/intern/some-resource', { token: 'live-token' })).rejects.toThrow(ApiError);
    });
    await waitFor(() => expect(result.current.isAuthenticated).toBe(false));
    expect(result.current.sessionExpired).toBe(true);
  });

  it('mockStoreAccessToken was actually used during sign-in (sanity check on the mocks above)', async () => {
    mockLogin.mockResolvedValue({ accessToken: 'live-token', tokenType: 'Bearer', ...INTERN_USER });
    mockGetCurrentUser.mockResolvedValue(INTERN_USER);

    const { result } = await renderHook(() => useAuth(), { wrapper: AuthProvider });
    await waitFor(() => expect(result.current.isInitializing).toBe(false));

    await act(async () => {
      await result.current.signIn({ email: INTERN_USER.email, password: 'secret' });
    });

    expect(mockStoreAccessToken).toHaveBeenCalledWith('live-token');
  });
});
