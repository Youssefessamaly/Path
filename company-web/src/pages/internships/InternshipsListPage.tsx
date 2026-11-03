import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { EmptyView, ErrorView, LoadingView } from '../../components/StateViews'
import { AppIcon } from '../../components/Visuals'
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

function InternshipRow({ internship }: { internship: CompanyInternship }) {
  return (
    <Link className="card row-item clickable internship-row" to={`/internships/${internship.id}`}>
      <span className="list-card-icon"><AppIcon name="briefcase" size={20} /></span>
      <span className="row-main">
        <strong>{internship.title}</strong>
        <span className="text-secondary">{internship.location} · {WORK_MODE_LABELS[internship.workMode]}</span>
      </span>
      <span className="row-meta">
        <span>Deadline {formatDisplayDate(internship.applicationDeadline)}</span>
        <span>{internship.skills.length} required skill{internship.skills.length === 1 ? '' : 's'}</span>
      </span>
      <span className={`badge ${internship.status === 'OPEN' ? 'badge-success' : 'badge-muted'}`}>
        {internship.status === 'OPEN' ? 'Open' : 'Closed'}
      </span>
      <AppIcon name="chevron-right" size={18} className="list-chevron" />
    </Link>
  )
}

export function InternshipsListPage() {
  const navigate = useNavigate()
  const [internships, setInternships] = useState<CompanyInternship[] | null>(null)
  const [isApproved, setIsApproved] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<InternshipStatus | 'ALL'>('ALL')
  const [search, setSearch] = useState('')

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

  useEffect(() => { load() }, [load])

  const query = search.trim().toLowerCase()
  const visible = internships?.filter((internship) =>
    (filter === 'ALL' || internship.status === filter) &&
    (!query || `${internship.title} ${internship.location} ${internship.workMode}`.toLowerCase().includes(query)),
  ) ?? null

  return (
    <div>
      <div className="page-header">
        <div>
          <span className="list-page-intro"><AppIcon name="briefcase" size={15} /> YOUR OPPORTUNITIES</span>
          <h1>Internships</h1>
          <p className="page-header-subtitle">Create, review, and manage the opportunities your company shares with emerging talent.</p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => navigate('/internships/new')} disabled={!isApproved}
          title={isApproved ? undefined : 'Your company must be approved before posting internships'}>
          <AppIcon name="plus" size={18} /> Create internship
        </button>
      </div>

      {!isApproved && <div className="banner banner-warning mb-4" role="status">Your company must be approved before you can post internships.</div>}

      <div className="list-page-toolbar">
        <div className="filter-tabs" role="group" aria-label="Filter internships by status">
          {STATUS_FILTERS.map((item) => (
            <button key={item.value} type="button" className={`filter-tab${item.value === filter ? ' active' : ''}`} onClick={() => setFilter(item.value)} aria-pressed={item.value === filter}>
              {item.label}{internships && <span className="filter-count">{item.value === 'ALL' ? internships.length : internships.filter((internship) => internship.status === item.value).length}</span>}
            </button>
          ))}
        </div>
        <label className="list-search"><AppIcon name="search" size={17} /><input aria-label="Search internships" type="search" placeholder="Search internships..." value={search} onChange={(event) => setSearch(event.target.value)} /></label>
      </div>

      {internships === null && !error && <LoadingView />}
      {error && <ErrorView message={error} onRetry={load} />}
      {visible !== null && !error && visible.length === 0 && <EmptyView title={search ? 'No matching internships' : 'No internships to show'} body={search ? 'Try a different title or location.' : filter === 'ALL' ? 'Create your first opportunity to get started.' : 'There are no internships with this status.'} />}
      {visible !== null && !error && visible.length > 0 && <div className="row-list">{visible.map((internship) => <InternshipRow key={internship.id} internship={internship} />)}</div>}
    </div>
  )
}
