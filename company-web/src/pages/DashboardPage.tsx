import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ErrorView, LoadingView } from '../components/StateViews'
import { useAuth } from '../auth/useAuth'
import {
  getCompanyApprovalStatus,
  getCompanyInternships,
  getCompanyProfile,
  getPipelineTemplates,
  type CompanyApprovalStatusResponse,
  type CompanyInternship,
  type CompanyProfile,
  type PipelineTemplate,
} from '../lib/api'

type DashboardData = {
  profile: CompanyProfile
  approval: CompanyApprovalStatusResponse
  internships: CompanyInternship[]
  templates: PipelineTemplate[]
}

const APPROVAL_BANNER: Record<CompanyApprovalStatusResponse['status'], { className: string; message: string } | null> = {
  PENDING: {
    className: 'banner-warning',
    message: 'Your company is pending admin review. Internship and ATS features stay locked until it is approved.',
  },
  REJECTED: {
    className: 'banner-error',
    message: 'Your company registration was rejected. Contact Pathora support for details.',
  },
  APPROVED: null,
}

export function DashboardPage() {
  const { membership } = useAuth()
  const navigate = useNavigate()
  const [data, setData] = useState<DashboardData | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setError(null)
    setData(null)
    try {
      const [profile, approval, internships, templates] = await Promise.all([
        getCompanyProfile(),
        getCompanyApprovalStatus(),
        getCompanyInternships().catch(() => [] as CompanyInternship[]),
        getPipelineTemplates().catch(() => [] as PipelineTemplate[]),
      ])
      setData({ profile, approval, internships, templates })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong. Please try again.')
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  if (error) {
    return <ErrorView message={error} onRetry={load} />
  }

  if (!data) {
    return <LoadingView />
  }

  const openInternships = data.internships.filter((internship) => internship.status === 'OPEN').length
  const activeTemplates = data.templates.filter((template) => !template.archived).length
  const banner = APPROVAL_BANNER[data.approval.status]

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>{data.profile.companyName ?? 'Your company'}</h1>
          <p className="page-header-subtitle">
            Signed in as {membership?.role === 'OWNER' ? 'the company owner' : (membership?.role ?? 'a team member').toLowerCase()}
          </p>
        </div>
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => navigate('/internships/new')}
          disabled={data.approval.status !== 'APPROVED'}
          title={data.approval.status === 'APPROVED' ? undefined : 'Your company must be approved before posting internships'}
        >
          + Post Internship
        </button>
      </div>

      {banner && (
        <div className={`banner ${banner.className} mb-4`} role="status">
          {banner.message}
        </div>
      )}

      <div className="stat-grid">
        <div className="card stat-card">
          <span className="stat-value">{data.internships.length}</span>
          <span className="stat-label">Total internships</span>
        </div>
        <div className="card stat-card">
          <span className="stat-value">{openInternships}</span>
          <span className="stat-label">Currently open</span>
        </div>
        <div className="card stat-card">
          <span className="stat-value">{activeTemplates}</span>
          <span className="stat-label">Active pipeline templates</span>
        </div>
      </div>

      <div className="card">
        <h2 className="section-title">Quick links</h2>
        <div className="row-actions">
          <Link to="/internships" className="btn btn-secondary">
            View internships
          </Link>
          <Link to="/pipeline-templates" className="btn btn-secondary">
            Manage pipeline templates
          </Link>
        </div>
      </div>
    </div>
  )
}
