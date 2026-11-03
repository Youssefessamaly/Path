import { useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { AppIcon, BrandMark } from './Visuals'

const ROLE_LABELS: Record<string, string> = {
  OWNER: 'Company owner',
  RECRUITER: 'Recruiter',
  INTERVIEWER: 'Interviewer',
}

const NAV_ITEMS = [
  { label: 'Overview', to: '/dashboard', icon: 'overview' as const },
  { label: 'Internships', to: '/internships', icon: 'briefcase' as const },
  { label: 'Pipeline templates', to: '/pipeline-templates', icon: 'workflow' as const },
  { label: 'Company profile', to: '/company-profile', icon: 'building' as const },
]

function pageTitle(pathname: string): string {
  if (pathname.startsWith('/company-profile')) return 'Company profile'
  if (pathname.startsWith('/pipeline-templates')) return 'Pipeline templates'
  if (pathname.startsWith('/internships')) return 'Internships'
  return 'Overview'
}

export function CompanyShell() {
  const { user, membership, logout } = useAuth()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const [mobileNavOpen, setMobileNavOpen] = useState(false)

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const email = user?.email ?? ''
  const initials = email.slice(0, 2).toUpperCase() || 'CO'
  const roleLabel = membership ? (ROLE_LABELS[membership.role] ?? membership.role) : 'Team member'
  const onProfile = pathname.startsWith('/company-profile')

  return (
    <div className="company-shell">
      {mobileNavOpen && (
        <button type="button" className="sidebar-backdrop" aria-label="Close navigation" onClick={() => setMobileNavOpen(false)} />
      )}
      <aside className={`company-sidebar${mobileNavOpen ? ' is-open' : ''}`}>
        <Link className="company-sidebar-brand" to="/dashboard" onClick={() => setMobileNavOpen(false)} aria-label="Path home">
          <BrandMark onDark />
          <span className="brand-wordmark">
            <strong>Path</strong>
            <small>COMPANY PORTAL</small>
          </span>
        </Link>

        <div className="sidebar-navigation">
          <p className="sidebar-section-label">WORKSPACE</p>
          <nav className="company-nav" aria-label="Company navigation">
            {NAV_ITEMS.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) => `company-nav-link${isActive || (item.to !== '/dashboard' && pathname.startsWith(item.to)) ? ' active' : ''}`}
                onClick={() => setMobileNavOpen(false)}
              >
                <AppIcon name={item.icon} size={19} />
                <span>{item.label}</span>
                <AppIcon name="chevron-right" size={16} className="nav-chevron" />
              </NavLink>
            ))}
          </nav>
        </div>

        <div className="sidebar-bottom">
          <p className="sidebar-section-label sidebar-account-label">SIGNED IN AS</p>
          <div className="company-sidebar-footer">
            <Link to="/company-profile" className="sidebar-avatar sidebar-avatar-link" aria-label="Open company profile" onClick={() => setMobileNavOpen(false)}>{initials}</Link>
            <Link to="/company-profile" className="company-sidebar-account sidebar-account-link" onClick={() => setMobileNavOpen(false)} title="View company profile">
              <span className="company-sidebar-email" title={email}>{email}</span>
              <span className="company-sidebar-role">{roleLabel}</span>
            </Link>
            <button type="button" className="sidebar-logout" onClick={handleLogout} title="Log out" aria-label="Log out">
              <AppIcon name="logout" size={18} />
            </button>
          </div>
        </div>
      </aside>

      <div className="company-workspace">
        <header className="workspace-topbar">
          <div className="workspace-topbar-left">
            <button type="button" className="mobile-nav-toggle" onClick={() => setMobileNavOpen((open) => !open)} aria-label={mobileNavOpen ? 'Close menu' : 'Open menu'}>
              <AppIcon name={mobileNavOpen ? 'close' : 'menu'} size={22} />
            </button>
            <span className="breadcrumb-root">Workspace</span>
            <AppIcon name="chevron-right" size={14} className="breadcrumb-separator" />
            <strong>{pageTitle(pathname)}</strong>
          </div>
          <div className="workspace-topbar-right">
            <span className="workspace-current-date">{new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date())}</span>
            <span className="topbar-divider" />
            <Link to="/company-profile" className={`topbar-profile-link${onProfile ? ' is-active' : ''}`} aria-label="View company profile and information" title="Open company profile">
              <span className="topbar-account-avatar">{initials}</span>
              <span className="topbar-role">{roleLabel}</span>
              <AppIcon name="chevron-right" size={15} className="topbar-profile-chevron" />
            </Link>
          </div>
        </header>
        <main className="company-main" id="main-content">
          <div className="workspace-content"><Outlet /></div>
        </main>
        <footer className="workspace-footer"><span>Path for companies</span><span>Make opportunity happen.</span></footer>
      </div>
    </div>
  )
}
