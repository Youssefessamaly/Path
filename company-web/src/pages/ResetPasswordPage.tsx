import { useEffect, useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ApiError, NetworkError, resetPassword } from '../lib/api'

/**
 * Step 3 (final) of the password reset flow — mirrors mobile's reset-password.tsx. The
 * resetToken is carried via router state from VerifyOtpPage, never the URL. A direct visit
 * with no token redirects back to the start of the flow, same as mobile's own
 * useEffect-based guard.
 */
export function ResetPasswordPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as { email?: string; resetToken?: string } | null
  const resetToken = state?.resetToken ?? null

  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  useEffect(() => {
    if (!resetToken) {
      navigate('/forgot-password', { replace: true })
    }
  }, [resetToken, navigate])

  if (!resetToken) {
    return null
  }

  const isPasswordValid = newPassword.length >= 8
  const passwordsMatch = confirmPassword.length > 0 && newPassword === confirmPassword
  const canSubmit = isPasswordValid && passwordsMatch && !isSubmitting

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!canSubmit) {
      return
    }
    setSubmitError(null)
    setIsSubmitting(true)
    try {
      await resetPassword({ resetToken, newPassword })
      navigate('/login', {
        replace: true,
        state: { message: 'Your password has been reset successfully. Please log in.' },
      })
    } catch (error) {
      if (error instanceof ApiError) {
        setSubmitError('This reset link has expired or is invalid. Please start again.')
      } else if (error instanceof NetworkError) {
        setSubmitError(error.message)
      } else {
        setSubmitError('Something went wrong. Please try again.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-card card">
        <div className="login-logo">
          <strong>PATHORA</strong>
          <span>Create new password</span>
        </div>

        <form className="login-form" onSubmit={handleSubmit}>
          <p className="text-secondary" style={{ marginBottom: 4 }}>
            Use a password you haven't used before.
          </p>

          <div className="field">
            <label htmlFor="newPassword">New password</label>
            <input
              id="newPassword"
              type="password"
              autoComplete="new-password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              placeholder="Create a new password"
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
              placeholder="Re-enter your new password"
              disabled={isSubmitting}
              required
            />
            {confirmPassword.length > 0 && !passwordsMatch && (
              <span className="field-hint text-error">Passwords don't match</span>
            )}
          </div>

          {submitError && (
            <div className="banner banner-error" role="alert">
              {submitError}
            </div>
          )}

          <button type="submit" className="btn btn-primary btn-block" disabled={!canSubmit}>
            {isSubmitting ? 'Resetting…' : 'Reset password'}
          </button>
        </form>
      </div>
    </div>
  )
}
