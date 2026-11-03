/**
 * The company JWT lives in sessionStorage only — never in source code, a cookie this app sets
 * itself, or a URL param — and only for the lifetime of this browser tab. Never log the value
 * anywhere in this module or its callers.
 *
 * Deliberately a distinct storage key from admin-web's `pathora_admin_token` — the two apps
 * must never read or clear each other's session, even if opened in tabs on the same origin
 * during local development.
 */

const TOKEN_KEY = 'pathora_company_token'

export function getStoredToken(): string | null {
  try {
    return sessionStorage.getItem(TOKEN_KEY)
  } catch {
    // sessionStorage can throw in some private-browsing modes — treat as "no session".
    return null
  }
}

export function storeToken(token: string): void {
  try {
    sessionStorage.setItem(TOKEN_KEY, token)
  } catch {
    // If storage isn't available the session just won't survive a reload; never throw here.
  }
}

export function clearStoredToken(): void {
  try {
    sessionStorage.removeItem(TOKEN_KEY)
  } catch {
    // ignore
  }
}
