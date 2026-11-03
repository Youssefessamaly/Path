import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

const ROLE_LABELS: Record<string, string> = {
  OWNER: 'Owner',
  RECRUITER: 'Recruiter',
  INTERVIEWER: 'Interviewer',
}

/**
 * The company portal chrome: a dark teal sidebar with branding, section navigation, and the
 * current member's email + role, distinct in shape from admin-web's single top header since
 * this app has multiple sections (dashboard, internships, pipeline templates, and more to
 * come).
 */
export function CompanyShell() {
  const { user, membership, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="company-shell">
      <aside className="company-sidebar">
        <div className="company-sidebar-brand">
          <strong>PATHORA</strong>
          <span>Company Portal</span>
        </div>

        <nav className="company-nav">
          <NavLink to="/dashboard" className={({ isActive }) => `company-nav-link${isActive ? ' active' : ''}`}>
            Dashboard
          </NavLink>
          <NavLink to="/internships" className={({ isActive }) => `company-nav-link${isActive ? ' active' : ''}`}>
            Internships
          </NavLink>
          <NavLink
            to="/pipeline-templates"
            className={({ isActive }) => `company-nav-link${isActive ? ' active' : ''}`}
          >
            Pipeline Templates
          </NavLink>
        </nav>

        <div className="company-sidebar-footer">
          <div className="company-sidebar-account">
            {user && <span className="company-sidebar-email">{user.email}</span>}
            {membership && (
              <span className="company-sidebar-role">{ROLE_LABELS[membership.role] ?? membership.role}</span>
            )}
          </div>
          <button type="button" className="btn btn-secondary btn-sm" onClick={handleLogout}>
            Log out
          </button>
        </div>
      </aside>

      <main className="company-main">
        <Outlet />
      </main>
    </div>
  )
}
