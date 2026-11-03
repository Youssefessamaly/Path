import { AuthScaffold } from '../components/AuthScaffold'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ApiError, NetworkError } from '../lib/api'

/**
 * In the login context a 401 always means "wrong email or password" — there is no prior
 * session to have expired — so it is described differently here than lib/api.ts's generic
 * "your session has expired" message for an authenticated request gone stale.
 */
function describeLoginError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Invalid email or password.'
    }
    return error.message
  }
  if (error instanceof NetworkError) {
    return error.message
  }
  if (error instanceof Error) {
    return error.message
  }
  return 'Something went wrong. Please try again.'
}

export function LoginPage() {
  const { login, isAuthenticated, isInitializing } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  // Set by RegisterPage/ResetPasswordPage via navigate(..., { state }) after a successful
  // registration or password reset — never put in the URL, and cleared on next render by
  // history.replaceState (see the effect below) so a page refresh doesn't keep re-showing it.
  const successMessage = (location.state as { message?: string } | null)?.message ?? null

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  if (!isInitializing && isAuthenticated) {
    return <Navigate to="/dashboard" replace />
  }

  const canSubmit = email.trim().length > 0 && password.length > 0 && !isSubmitting

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!canSubmit) {
      return
    }

    setError(null)
    setIsSubmitting(true)
    try {
      await login(email.trim(), password)
      navigate('/dashboard', { replace: true })
    } catch (loginError) {
      setError(describeLoginError(loginError))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthScaffold>
        <div className="login-logo">
          <strong>PATH</strong>
          <span>Company Portal</span>
        </div>

        <form className="login-form" onSubmit={handleSubmit}>
          {successMessage && (
            <div className="banner banner-success" role="status">
              {successMessage}
            </div>
          )}

          <div className="field">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              disabled={isSubmitting}
              required
            />
          </div>

          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              disabled={isSubmitting}
              required
            />
          </div>

          {error && (
            <div className="banner banner-error" role="alert">
              {error}
            </div>
          )}

          <button type="submit" className="btn btn-primary btn-block" disabled={!canSubmit}>
            {isSubmitting ? 'Signing in…' : 'Sign in'}
          </button>

          <div style={{ textAlign: 'center' }}>
            <Link to="/forgot-password" className="back-link" style={{ display: 'inline-flex', marginBottom: 0 }}>
              Forgot password?
            </Link>
          </div>
        </form>

        <div style={{ textAlign: 'center', fontSize: 13.5 }} className="text-secondary">
          Don't have a company account?{' '}
          <Link to="/register" style={{ fontWeight: 700 }}>
            Create Company Account
          </Link>
        </div>
    </AuthScaffold>
  )
}
