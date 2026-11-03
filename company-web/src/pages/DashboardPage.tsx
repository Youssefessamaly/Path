import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ErrorView, LoadingView } from '../components/StateViews'
import { AppIcon } from '../components/Visuals'
import { useAuth } from '../auth/useAuth'
import { formatDisplayDate } from '../lib/date'
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
    message: 'Your company registration was rejected. Contact PATH support for details.',
  },
  APPROVED: null,
}

const WORK_MODE_LABELS: Record<CompanyInternship['workMode'], string> = {
  ONSITE: 'On-site',
  HYBRID: 'Hybrid',
  REMOTE: 'Remote',
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

  useEffect(() => { load() }, [load])

  if (error) return <ErrorView message={error} onRetry={load} />
  if (!data) return <LoadingView />

  const openInternships = data.internships.filter((internship) => internship.status === 'OPEN').length
  const activeTemplates = data.templates.filter((template) => !template.archived)
  const isApproved = data.approval.status === 'APPROVED'
  const banner = APPROVAL_BANNER[data.approval.status]
  const companyName = data.profile.companyName?.trim() || 'your team'
  const greeting = new Date().getHours() < 12 ? 'Good morning' : new Date().getHours() < 17 ? 'Good afternoon' : 'Good evening'

  return (
    <div className="dashboard-page">
      <section className="dashboard-hero">
        <div className="dashboard-hero-content">
          <div className="hero-eyebrow"><span className="hero-eyebrow-dot" /> YOUR COMPANY WORKSPACE</div>
          <h1>{greeting}, <span className="hero-company-name">{companyName}</span><span className="hero-greeting-ending">let's build what's next.</span></h1>
          <p>Every great career starts with an opportunity. Here's what's happening across your hiring workspace.</p>
          <div className="dashboard-hero-actions">
            <button
              type="button"
              className="btn btn-accent"
              onClick={() => navigate('/internships/new')}
              disabled={!isApproved}
              title={isApproved ? undefined : 'Your company must be approved before posting internships'}
            >
              <AppIcon name="plus" size={19} /> Post an internship
            </button>
            <Link className="hero-secondary-link" to="/internships">Explore internships <AppIcon name="arrow-up-right" size={17} /></Link>
          </div>
        </div>
        <div className="hero-illustration" aria-hidden="true">
          <span className="hero-illustration-orbit hero-orbit-one" />
          <span className="hero-illustration-orbit hero-orbit-two" />
          <span className="hero-illustration-orbit hero-orbit-three" />
          <span className="hero-center-glyph"><AppIcon name="sparkle" size={45} /></span>
          <span className="hero-floating-card hero-floating-card-a"><AppIcon name="briefcase" size={19} /><span>Opportunities</span><i /></span>
          <span className="hero-floating-card hero-floating-card-b"><AppIcon name="workflow" size={19} /><span>Career journeys</span><i /></span>
          <span className="hero-illustration-star star-one">✦</span><span className="hero-illustration-star star-two">✧</span>
        </div>
      </section>

      {banner && <div className={`banner ${banner.className} dashboard-banner`} role="status">{banner.message}</div>}

      <div className="dashboard-section-heading">
        <div><p className="eyebrow-dark">THE BIG PICTURE</p><h2>At a glance</h2></div>
        <span className="section-heading-hint">Your company activity</span>
      </div>

      <section className="dashboard-stats" aria-label="Company overview metrics">
        <article className="dashboard-metric">
          <div className="metric-top"><span className="metric-icon metric-icon-peach"><AppIcon name="briefcase" size={22} /></span><AppIcon name="arrow-up-right" size={19} className="metric-watermark" /></div>
          <strong className="metric-number">{data.internships.length}</strong>
          <span className="metric-label">Total internships</span>
          <span className="metric-caption">Opportunities created so far</span>
        </article>
        <article className="dashboard-metric">
          <div className="metric-top"><span className="metric-icon metric-icon-green"><AppIcon name="target" size={22} /></span><AppIcon name="arrow-up-right" size={19} className="metric-watermark" /></div>
          <strong className="metric-number">{openInternships}</strong>
          <span className="metric-label">Currently open</span>
          <span className="metric-caption">Open for applications</span>
        </article>
        <article className="dashboard-metric">
          <div className="metric-top"><span className="metric-icon metric-icon-blue"><AppIcon name="layers" size={22} /></span><AppIcon name="arrow-up-right" size={19} className="metric-watermark" /></div>
          <strong className="metric-number">{activeTemplates.length}</strong>
          <span className="metric-label">Active pipelines</span>
          <span className="metric-caption">Reusable hiring workflows</span>
        </article>
      </section>

      <div className="dashboard-content-grid">
        <section className="dashboard-panel dashboard-internship-panel">
          <div className="dashboard-panel-heading">
            <div><p className="eyebrow-dark">OPPORTUNITIES</p><h2>Latest internships</h2></div>
            <Link className="panel-action" to="/internships">View all <AppIcon name="arrow-up-right" size={16} /></Link>
          </div>
          {data.internships.length > 0 ? (
            <div className="dashboard-internship-list">
              {data.internships.slice(0, 4).map((internship) => (
                <Link to={`/internships/${internship.id}`} className="dashboard-internship" key={internship.id}>
                  <span className="dashboard-internship-icon"><AppIcon name="briefcase" size={21} /></span>
                  <span className="dashboard-internship-info">
                    <strong>{internship.title}</strong>
                    <span>{internship.location || 'Location not set'} · {WORK_MODE_LABELS[internship.workMode]} · Due {formatDisplayDate(internship.applicationDeadline)}</span>
                  </span>
                  <span className={`badge ${internship.status === 'OPEN' ? 'badge-success' : 'badge-muted'}`}>{internship.status === 'OPEN' ? 'Open' : 'Closed'}</span>
                  <AppIcon name="chevron-right" size={18} className="list-chevron" />
                </Link>
              ))}
            </div>
          ) : (
            <div className="dashboard-empty-list">
              <span><AppIcon name="briefcase" size={25} /></span>
              <h3>Your next opportunity starts here</h3>
              <p>Post your first internship and begin connecting with future talent.</p>
              <button type="button" className="btn btn-secondary" disabled={!isApproved} onClick={() => navigate('/internships/new')}>
                <AppIcon name="plus" size={17} /> Create internship
              </button>
            </div>
          )}
          {data.internships.length > 0 && <Link to="/internships" className="dashboard-list-footer">Manage all internships <AppIcon name="arrow-right" size={17} /></Link>}
        </section>

        <div className="dashboard-right-rail">
          <section className="dashboard-panel workspace-status-panel">
            <div className="dashboard-panel-heading"><div><p className="eyebrow-dark">YOUR WORKSPACE</p><h2>Company status</h2></div><AppIcon name="shield" size={21} className="rail-heading-icon" /></div>
            <div className={`company-status-indicator status-${data.approval.status.toLowerCase()}`}>
              <span className="status-icon"><AppIcon name={isApproved ? 'check' : 'clock'} size={23} /></span>
              <div><strong>{isApproved ? 'Approved & ready' : data.approval.status === 'PENDING' ? 'Awaiting approval' : 'Review unsuccessful'}</strong><span>{isApproved ? 'Your workspace is ready for hiring.' : data.approval.status === 'PENDING' ? 'Your request is being reviewed.' : 'Please contact PATH support.'}</span></div>
            </div>
            <div className="workspace-status-meta"><span>Account role</span><strong>{membership?.role === 'OWNER' ? 'Company owner' : (membership?.role ?? 'Team member').toLowerCase()}</strong></div>
            <div className="workspace-status-meta"><span>Company profile</span><strong>{data.profile.onboardingCompleted ? 'Completed' : 'Not completed'}</strong></div>
          </section>

          <section className="dashboard-panel dashboard-library-panel">
            <div className="dashboard-panel-heading"><div><p className="eyebrow-dark">HIRING TOOLS</p><h2>Pipeline library</h2></div><AppIcon name="workflow" size={21} className="rail-heading-icon" /></div>
            {activeTemplates.length ? (
              <div className="dashboard-template-list">
                {activeTemplates.slice(0, 2).map((template) => (
                  <Link to={`/pipeline-templates/${template.id}`} className="dashboard-template" key={template.id}>
                    <span className="template-mini-icon"><AppIcon name="layers" size={18} /></span>
                    <span><strong>{template.name}</strong><small>{template.stages.length} stage{template.stages.length === 1 ? '' : 's'}</small></span>
                    <AppIcon name="chevron-right" size={16} />
                  </Link>
                ))}
              </div>
            ) : (
              <p className="library-empty-text">No pipeline templates yet. Create a reusable flow for your hiring process.</p>
            )}
            <Link to="/pipeline-templates" className="dashboard-library-link">Manage templates <AppIcon name="arrow-right" size={17} /></Link>
          </section>
        </div>
      </div>
    </div>
  )
}
