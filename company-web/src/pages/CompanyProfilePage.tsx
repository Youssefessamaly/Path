import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ErrorView, LoadingView } from '../components/StateViews'
import { AppIcon } from '../components/Visuals'
import {
  getCompanyApprovalStatus,
  getCompanyProfile,
  type CompanyApprovalStatus,
  type CompanyApprovalStatusResponse,
  type CompanyProfile,
} from '../lib/api'

const APPROVAL_TEXT: Record<CompanyApprovalStatus, { label: string; description: string }> = {
  APPROVED: { label: 'Approved', description: 'Your company has been reviewed and approved.' },
  PENDING: { label: 'Under review', description: 'Your company information is waiting for administrator review.' },
  REJECTED: { label: 'Not approved', description: 'Your company was not approved. Contact Path support for next steps.' },
}

function initialsForCompany(name: string, fallback: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  return parts.length ? parts.slice(0, 2).map((part) => part[0]).join('').toUpperCase() : (fallback.slice(0, 2).toUpperCase() || 'CO')
}

function safeWebsite(value: string | null): string | null {
  if (!value) return null
  try {
    const url = new URL(value)
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : null
  } catch {
    return null
  }
}

function ProfileCard({ icon, title, children, className = '' }: { icon: 'building' | 'mail' | 'shield' | 'info'; title: string; children: ReactNode; className?: string }) {
  return (
    <section className={`profile-detail-card ${className}`}>
      <div className="profile-card-heading">
        <span className="profile-section-icon"><AppIcon name={icon} size={18} /></span>
        <h2>{title}</h2>
      </div>
      {children}
    </section>
  )
}

function DetailRow({ label, value }: { label: string; value: ReactNode | null | undefined }) {
  return (
    <div className="profile-detail-row">
      <dt>{label}</dt>
      <dd>{value || <span className="profile-not-provided">Not provided</span>}</dd>
    </div>
  )
}

export function CompanyProfilePage() {
  const { user, membership } = useAuth()
  const [profile, setProfile] = useState<CompanyProfile | null>(null)
  const [approval, setApproval] = useState<CompanyApprovalStatusResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadProfile = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [company, status] = await Promise.all([getCompanyProfile(), getCompanyApprovalStatus()])
      setProfile(company)
      setApproval(status)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to load company information.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void loadProfile() }, [loadProfile])

  if (loading) return <LoadingView />
  if (error || !profile || !approval) return <ErrorView message={error ?? 'Company details are unavailable.'} onRetry={loadProfile} />

  const companyName = profile.companyName?.trim() || 'Your company'
  const subtitle = [profile.industry, profile.location].filter(Boolean).join(' · ')
  const websiteUrl = safeWebsite(profile.website)
  const approvalLabel = APPROVAL_TEXT[approval.status]
  const memberRole = membership?.role === 'OWNER' ? 'Company owner' : membership?.role === 'RECRUITER' ? 'Recruiter' : membership?.role === 'INTERVIEWER' ? 'Interviewer' : 'Team member'

  return (
    <div className="company-profile-page">
      <div className="profile-page-heading">
        <div>
          <p className="profile-eyebrow">YOUR ORGANIZATION</p>
          <h1>Company profile</h1>
          <p>All the information associated with your company workspace, just like in the Path mobile app.</p>
        </div>
        <button type="button" onClick={() => void loadProfile()} className="btn btn-secondary profile-refresh"><AppIcon name="refresh" size={17} /> Refresh details</button>
      </div>

      <section className="profile-identity-card" aria-label="Company identity">
        <div className="profile-identity-main">
          <span className="profile-company-avatar" aria-hidden="true">{initialsForCompany(companyName, user?.email ?? '')}</span>
          <div className="profile-identity-copy">
            <span className="profile-overline">COMPANY ACCOUNT</span>
            <h2>{companyName}</h2>
            <p>{subtitle || 'Your company workspace on Path'}</p>
          </div>
        </div>
        <div className="profile-identity-meta">
          <span><AppIcon name="building" size={16} /> Company details</span>
          <span className={`profile-approval-pill profile-approval-${approval.status.toLowerCase()}`}><span /> {approvalLabel.label}</span>
        </div>
      </section>

      <div className="profile-information-grid">
        <div className="profile-left-column">
          <ProfileCard icon="info" title="About the company">
            <p className="profile-description">{profile.description?.trim() || 'No description has been added to this company yet.'}</p>
          </ProfileCard>
          <ProfileCard icon="building" title="Company information">
            <dl className="profile-detail-list">
              <DetailRow label="Company name" value={profile.companyName} />
              <DetailRow label="Industry" value={profile.industry} />
              <DetailRow label="Company size" value={profile.companySize} />
              <DetailRow label="Location" value={profile.location} />
              <DetailRow label="Website" value={websiteUrl ? <a href={websiteUrl} target="_blank" rel="noopener noreferrer" className="profile-website"><AppIcon name="globe" size={16} /> {profile.website} <AppIcon name="arrow-up-right" size={15} /></a> : profile.website} />
            </dl>
          </ProfileCard>
        </div>
        <div className="profile-right-column">
          <ProfileCard icon="shield" title="Verification status">
            <div className={`profile-verification-box profile-verify-${approval.status.toLowerCase()}`}>
              <AppIcon name={approval.status === 'APPROVED' ? 'shield' : approval.status === 'PENDING' ? 'clock' : 'info'} size={22} />
              <div><strong>{approvalLabel.label}</strong><p>{approvalLabel.description}</p></div>
            </div>
            <dl className="profile-detail-list">
              <DetailRow label="Profile setup" value={profile.onboardingCompleted ? 'Completed' : 'Incomplete'} />
              <DetailRow label="Your access" value={memberRole} />
            </dl>
          </ProfileCard>
          <ProfileCard icon="mail" title="Contact details">
            <dl className="profile-detail-list">
              <DetailRow label="Contact person" value={profile.contactName} />
              <DetailRow label="Account email" value={user?.email} />
              <DetailRow label="Contact phone" value={profile.contactPhone} />
            </dl>
          </ProfileCard>
          <div className="profile-help-note"><AppIcon name="info" size={19} /><p>Company details are shown from your existing Path account. Company owners can update these details in the Path mobile app.</p></div>
        </div>
      </div>
      <Link to="/dashboard" className="profile-back-link"><AppIcon name="arrow-right" size={17} /> Back to overview</Link>
    </div>
  )
}
