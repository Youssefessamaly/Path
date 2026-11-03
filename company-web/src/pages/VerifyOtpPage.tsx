import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, NetworkError, forgotPassword, verifyOtp } from '../lib/api'

const OTP_DISPLAY_SECONDS = 5 * 60

function formatCountdown(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

/**
 * Step 2 of the password reset flow — mirrors mobile's verify-otp.tsx, including the 5-minute
 * client-side countdown display (the backend's own OTP expiry is authoritative; this is purely
 * a UX hint) and the resend action. The email is carried via router state from
 * ForgotPasswordPage, never the URL — a direct visit with no state redirects back to start.
 */
export function VerifyOtpPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const email = (location.state as { email?: string } | null)?.email ?? null

  const [code, setCode] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isResending, setIsResending] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [resendNotice, setResendNotice] = useState<string | null>(null)
  const [secondsRemaining, setSecondsRemaining] = useState(OTP_DISPLAY_SECONDS)

  useEffect(() => {
    if (!email) {
      navigate('/forgot-password', { replace: true })
    }
  }, [email, navigate])

  useEffect(() => {
    if (secondsRemaining <= 0) {
      return
    }
    const interval = setInterval(() => {
      setSecondsRemaining((current) => Math.max(0, current - 1))
    }, 1000)
    return () => clearInterval(interval)
  }, [secondsRemaining])

  if (!email) {
    return null
  }

  const isCodeValid = code.length === 6
  const isExpiredDisplay = secondsRemaining <= 0

  const handleChangeCode = (value: string) => {
    setCode(value.replace(/[^0-9]/g, '').slice(0, 6))
  }

  const handleVerify = async () => {
    if (!isCodeValid || isSubmitting) {
      return
    }
    setSubmitError(null)
    setResendNotice(null)
    setIsSubmitting(true)
    try {
      const result = await verifyOtp({ email, code })
      navigate('/reset-password', { state: { email, resetToken: result.resetToken } })
    } catch (error) {
      if (error instanceof ApiError) {
        setSubmitError('That code is invalid or has expired. Please try again or resend the code.')
      } else if (error instanceof NetworkError) {
        setSubmitError(error.message)
      } else {
        setSubmitError('Something went wrong. Please try again.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleResend = async () => {
    if (isResending) {
      return
    }
    setSubmitError(null)
    setResendNotice(null)
    setIsResending(true)
    try {
      await forgotPassword({ email })
      setSecondsRemaining(OTP_DISPLAY_SECONDS)
      setCode('')
      setResendNotice('A new verification code has been sent to your email.')
    } catch (error) {
      if (error instanceof ApiError) {
        setSubmitError(error.message)
      } else if (error instanceof NetworkError) {
        setSubmitError(error.message)
      } else {
        setSubmitError('Something went wrong. Please try again.')
      }
    } finally {
      setIsResending(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-card card">
        <div className="login-logo">
          <strong>PATHORA</strong>
          <span>Enter verification code</span>
        </div>

        <div className="login-form">
          <p className="text-secondary" style={{ marginBottom: 4 }}>
            A 6-digit code was sent to {email} and is valid for 5 minutes.
          </p>

          <div className="field">
            <label htmlFor="code">Verification code</label>
            <input
              id="code"
              value={code}
              onChange={(event) => handleChangeCode(event.target.value)}
              placeholder="123456"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
            />
            <span className={isExpiredDisplay ? 'field-hint text-error' : 'field-hint'}>
              {isExpiredDisplay ? 'Code expired — request a new one' : `Expires in ${formatCountdown(secondsRemaining)}`}
            </span>
          </div>

          {resendNotice && (
            <div className="banner banner-success" role="status">
              {resendNotice}
            </div>
          )}

          {submitError && (
            <div className="banner banner-error" role="alert">
              {submitError}
            </div>
          )}

          <button
            type="button"
            className="btn btn-primary btn-block"
            onClick={handleVerify}
            disabled={!isCodeValid || isSubmitting}
          >
            {isSubmitting ? 'Verifying…' : 'Verify code'}
          </button>

          <div style={{ textAlign: 'center', fontSize: 13.5 }} className="text-secondary">
            Didn't get a code?{' '}
            <button
              type="button"
              onClick={handleResend}
              disabled={isResending}
              style={{ border: 'none', background: 'none', color: 'var(--pathora-teal-600)', fontWeight: 700, cursor: 'pointer', padding: 0 }}
            >
              {isResending ? 'Resending…' : 'Resend code'}
            </button>
          </div>
        </div>

        <div style={{ textAlign: 'center', fontSize: 13.5 }} className="text-secondary">
          <Link to="/login">← Back to login</Link>
        </div>
      </div>
    </div>
  )
}
