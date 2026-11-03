import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import {
  ApiError,
  getCurrentUser,
  getOwnMembership,
  login as apiLogin,
  registerUnauthorizedHandler,
  type CompanyMembership,
} from '../lib/api'
import { clearStoredToken, getStoredToken, storeToken } from '../lib/token'
import { AuthContext, type AuthContextValue, type CompanyUser } from './context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CompanyUser | null>(null)
  const [membership, setMembership] = useState<CompanyMembership | null>(null)
  const [isInitializing, setIsInitializing] = useState(true)

  const logout = useCallback(() => {
    clearStoredToken()
    setUser(null)
    setMembership(null)
  }, [])

  // Any 401 from anywhere in the app (not just a deliberate logout click) clears the session
  // the same way — see lib/api.ts's unauthorizedHandler.
  useEffect(() => {
    registerUnauthorizedHandler(logout)
    return () => registerUnauthorizedHandler(null)
  }, [logout])

  // Re-validate a stored token (role AND company membership) against the backend on load,
  // rather than trusting an unverified, possibly-expired token left over from a previous visit
  // to this tab. A COMPANY-role user with no resolvable membership (revoked, or never a member
  // of any company) is treated the same as an invalid session — there is nothing this portal
  // can show them.
  useEffect(() => {
    const token = getStoredToken()
    if (!token) {
      setIsInitializing(false)
      return
    }

    Promise.all([getCurrentUser(), getOwnMembership()])
      .then(([current, ownMembership]) => {
        if (current.role !== 'COMPANY') {
          clearStoredToken()
          setUser(null)
          setMembership(null)
          return
        }
        setUser(current)
        setMembership(ownMembership)
      })
      .catch(() => {
        // An invalid/expired token already triggered the unauthorized handler above for a
        // 401; a 403/404 (not a COMPANY member, or membership was revoked) is treated the
        // same way here. Any other failure just leaves this tab logged out rather than
        // guessing.
        clearStoredToken()
        setUser(null)
        setMembership(null)
      })
      .finally(() => setIsInitializing(false))
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const response = await apiLogin({ email, password })

    if (response.role !== 'COMPANY') {
      // Never store a token for a non-company account, even momentarily. Platform ADMIN and
      // INTERN accounts must never reach this portal.
      throw new Error('This account does not have company access.')
    }

    storeToken(response.accessToken)

    try {
      const ownMembership = await getOwnMembership()
      setUser({ userId: response.userId, email: response.email, role: response.role })
      setMembership(ownMembership)
    } catch (error) {
      clearStoredToken()
      if (error instanceof ApiError && error.status === 404) {
        throw new Error('This account is not an authorized member of any company yet.')
      }
      throw error
    }
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      membership,
      isAuthenticated: Boolean(user && membership),
      isInitializing,
      login,
      logout,
    }),
    [user, membership, isInitializing, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
