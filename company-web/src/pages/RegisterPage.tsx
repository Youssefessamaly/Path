import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ApiError, NetworkError, registerCompany } from '../lib/api'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** Mirrors mobile's register/company.tsx error handling, with the one addition this screen
 *  actually needs: a specific message for the 409 duplicate-email case (the backend's own
 *  check — AuthService.register — never re-implemented here), since the shared
 *  friendlyMessageForStatus wording in lib/api.ts is written for ATS conflicts, not this. */
function describeRegisterError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 409) {
      return 'An account with this email already exists.'
    }
    if (error.status === 400) {
      return 'Please check your email and password and try again.'
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

/**
 * Company registration — the same two-field POST /auth/register/company payload mobile's
 * register/company.tsx sends (email, password only; company name/industry/etc. are collected
 * afterward through the existing onboarding flow, not here). A new account always starts
 * PENDING approval (CompanyProfile's own default) and is never auto-approved or auto-logged-in
 * by this page — registration only ever redirects to /login, exactly like mobile does,
 * because there is no backend capability to sign a brand-new account in without first
 * authenticating with its own credentials.
 */
export function RegisterPage() {
  const { isAuthenticated, isInitializing } = useAuth()
  const navigate = useNavigate()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [agreedToTerms, setAgreedToTerms] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  if (!isInitializing && isAuthenticated) {
    return <Navigate to="/dashboard" replace />
  }

  const isEmailValid = EMAIL_PATTERN.test(email)
  const isPasswordValid = password.length >= 8
  const passwordsMatch = confirmPassword.length > 0 && password === confirmPassword
  const canSubmit = isEmailValid && isPasswordValid && passwordsMatch && agreedToTerms && !isSubmitting

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!canSubmit) {
      return
    }

    setSubmitError(null)
    setIsSubmitting(true)
    try {
      await registerCompany({ email: email.trim(), password })
      // Registration succeeded — send the company to the login page so they authenticate
      // normally with their newly created (PENDING) account, matching mobile exactly.
      navigate('/login', {
        replace: true,
        state: { message: 'Account created. Log in to continue — your company will need admin approval before posting internships.' },
      })
    } catch (error) {
      setSubmitError(describeRegisterError(error))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-card card">
        <div className="login-logo">
          <strong>PATHORA</strong>
          <span>Create your company account</span>
        </div>

        <form className="login-form" onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="email">Official email</label>
            <input
              id="email"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="hr@yourcompany.com"
              disabled={isSubmitting}
              required
            />
          </div>

          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="Create a password"
              disabled={isSubmitting}
              required
            />
            <span className="field-hint">At least 8 characters</span>
          </div>

          <div className="field">
            <label htmlFor="confirmPassword">Confirm password</label>
            <input
              id="confirmPassword"
              type="password"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              placeholder="Re-enter your password"
              disabled={isSubmitting}
              required
            />
            {confirmPassword.length > 0 && !passwordsMatch && (
              <span className="field-hint text-error">Passwords don't match</span>
            )}
          </div>

          <label className="checkbox-field">
            <input
              type="checkbox"
              checked={agreedToTerms}
              onChange={() => setAgreedToTerms((current) => !current)}
              disabled={isSubmitting}
            />
            I agree to Pathora's Terms of Service and Privacy Policy.
          </label>

          {submitError && (
            <div className="banner banner-error" role="alert">
              {submitError}
            </div>
          )}

          <button type="submit" className="btn btn-primary btn-block" disabled={!canSubmit}>
            {isSubmitting ? 'Creating account…' : 'Create account'}
          </button>
        </form>

        <div style={{ textAlign: 'center', fontSize: 13.5 }} className="text-secondary">
          Already have a company account? <Link to="/login" style={{ fontWeight: 700 }}>Log in</Link>
        </div>
      </div>
    </div>
  )
}
