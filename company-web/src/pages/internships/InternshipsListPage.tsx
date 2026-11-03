import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { EmptyView, ErrorView, LoadingView } from '../../components/StateViews'
import {
  ApiError,
  getCompanyApprovalStatus,
  getCompanyInternships,
  type CompanyInternship,
  type InternshipStatus,
} from '../../lib/api'
import { formatDisplayDate } from '../../lib/date'

const STATUS_FILTERS: { label: string; value: InternshipStatus | 'ALL' }[] = [
  { label: 'All', value: 'ALL' },
  { label: 'Open', value: 'OPEN' },
  { label: 'Closed', value: 'CLOSED' },
]

const WORK_MODE_LABELS: Record<CompanyInternship['workMode'], string> = {
  ONSITE: 'On-site',
  HYBRID: 'Hybrid',
  REMOTE: 'Remote',
}

function InternshipRow({ internship, onOpen }: { internship: CompanyInternship; onOpen: () => void }) {
  return (
    <div className="card row-item clickable" onClick={onOpen} role="button" tabIndex={0}>
      <div className="row-main">
        <strong>{internship.title}</strong>
        <span className="text-secondary">
          {internship.location} · {WORK_MODE_LABELS[internship.workMode]}
        </span>
      </div>

      <div className="row-meta">
        <span>Deadline {formatDisplayDate(internship.applicationDeadline)}</span>
        <span>{internship.skills.length} required skill{internship.skills.length === 1 ? '' : 's'}</span>
      </div>

      <span className={`badge ${internship.status === 'OPEN' ? 'badge-success' : 'badge-muted'}`}>
        {internship.status === 'OPEN' ? 'Open' : 'Closed'}
      </span>
    </div>
  )
}

/** Listing of this company's internships, reusing the existing GET /company/internships
 *  endpoint already shared with the mobile app. Creating/editing now lives here too (see
 *  InternshipFormPage) — the backend enforces the same company-approval gate regardless of
 *  which client calls it; this page just reflects that state so the "Create Internship"
 *  action isn't a dead end for a PENDING/REJECTED company. Clicking a row opens its detail
 *  page, which also hosts the Recruitment Pipeline section. */
export function InternshipsListPage() {
  const navigate = useNavigate()
  const [internships, setInternships] = useState<CompanyInternship[] | null>(null)
  const [isApproved, setIsApproved] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<InternshipStatus | 'ALL'>('ALL')

  const load = useCallback(() => {
    setInternships(null)
    setError(null)
    Promise.all([
      getCompanyInternships(),
      getCompanyApprovalStatus().catch(() => null),
    ])
      .then(([loadedInternships, approval]) => {
        setInternships(loadedInternships)
        setIsApproved(approval ? approval.status === 'APPROVED' : true)
      })
      .catch((loadError: unknown) => {
        setError(loadError instanceof ApiError ? loadError.message : 'Unable to load internships.')
      })
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const visible = internships?.filter((internship) => filter === 'ALL' || internship.status === filter) ?? null

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Internships</h1>
          <p className="page-header-subtitle">Every internship your company has posted.</p>
        </div>
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => navigate('/internships/new')}
          disabled={!isApproved}
          title={isApproved ? undefined : 'Your company must be approved before posting internships'}
        >
          + Create Internship
        </button>
      </div>

      {!isApproved && (
        <div className="banner banner-warning mb-4" role="status">
          Your company must be approved before you can post internships.
        </div>
      )}

      <div className="filter-tabs">
        {STATUS_FILTERS.map((item) => (
          <button
            key={item.value}
            type="button"
            className={`filter-tab${item.value === filter ? ' active' : ''}`}
            onClick={() => setFilter(item.value)}
          >
            {item.label}
          </button>
        ))}
      </div>

      {internships === null && !error && <LoadingView />}

      {error && <ErrorView message={error} onRetry={load} />}

      {visible !== null && !error && visible.length === 0 && (
        <EmptyView
          title="No internships to show"
          body={filter === 'ALL' ? 'Click "Create Internship" above to post your first opportunity.' : undefined}
        />
      )}

      {visible !== null && !error && visible.length > 0 && (
        <div className="row-list">
          {visible.map((internship) => (
            <InternshipRow
              key={internship.id}
              internship={internship}
              onOpen={() => navigate(`/internships/${internship.id}`)}
            />
          ))}
        </div>
      )}
    </div>
  )
}
