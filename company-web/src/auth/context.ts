import { createContext } from 'react'
import type { CompanyMembership, CurrentUserResponse } from '../lib/api'

export type CompanyUser = CurrentUserResponse

export type AuthContextValue = {
  user: CompanyUser | null
  /** The authenticated user's resolved company membership — role (OWNER/RECRUITER/
   *  INTERVIEWER) and status. Null only while still loading right after login/restore. */
  membership: CompanyMembership | null
  isAuthenticated: boolean
  /** True only while restoring a session from a stored token on first load — distinct from
   *  "not authenticated", so the app can show nothing/a blank shell instead of flashing the
   *  login page before the restore attempt resolves. */
  isInitializing: boolean
  login: (email: string, password: string) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)
