import { AuthScaffold } from '../components/AuthScaffold'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, NetworkError, forgotPassword } from '../lib/api'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/**
 * Step 1 of the existing OTP-based password reset flow — reused exactly as mobile's
 * forgot-password.tsx uses it. POST /auth/forgot-password always returns the same generic
 * message whether or not the email exists (PasswordResetController's own GENERIC_MESSAGE) —
 * never shown here as a distinct "email not found" error, since the backend deliberately
 * doesn't reveal that.
 */
export function ForgotPasswordPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const isEmailValid = EMAIL_PATTERN.test(email)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!isEmailValid || isSubmitting) {
      return
    }

    const trimmedEmail = email.trim()
    setSubmitError(null)
    setIsSubmitting(true)
    try {
      await forgotPassword({ email: trimmedEmail })
      navigate('/verify-otp', { state: { email: trimmedEmail } })
    } catch (error) {
      if (error instanceof ApiError) {
        setSubmitError(error.status === 400 ? 'Please enter a valid email address.' : error.message)
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
    <AuthScaffold>
        <div className="login-logo">
          <strong>PATH</strong>
          <span>Reset your password</span>
        </div>

        <form className="login-form" onSubmit={handleSubmit}>
          <p className="text-secondary" style={{ marginBottom: 4 }}>
            Enter your email address and we'll send you a 6-digit code to reset your password.
          </p>

          <div className="field">
            <label htmlFor="email">Email address</label>
            <input
              id="email"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@example.com"
              disabled={isSubmitting}
              required
            />
          </div>

          {submitError && (
            <div className="banner banner-error" role="alert">
              {submitError}
            </div>
          )}

          <button type="submit" className="btn btn-primary btn-block" disabled={!isEmailValid || isSubmitting}>
            {isSubmitting ? 'Sending…' : 'Send code'}
          </button>
        </form>

        <div style={{ textAlign: 'center', fontSize: 13.5 }} className="text-secondary">
          <Link to="/login">← Back to login</Link>
        </div>
    </AuthScaffold>
  )
}
