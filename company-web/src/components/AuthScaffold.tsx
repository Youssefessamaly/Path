import type { PropsWithChildren } from 'react'
import { AppIcon, BrandMark } from './Visuals'

export function AuthScaffold({ children }: PropsWithChildren) {
  return (
    <div className="login-page">
      <aside className="auth-story" aria-label="About PATH for companies">
        <div className="auth-story-brand"><BrandMark onDark /><strong>Path</strong><small>FOR COMPANIES</small></div>
        <div className="auth-story-main">
          <div className="auth-story-tag"><span /> THE FUTURE OF EARLY CAREERS</div>
          <h1>Great talent.<br /><em>Great beginnings.</em></h1>
          <p>Your space to create opportunities, connect with rising talent, and build a recruitment process that feels effortless.</p>
          <div className="auth-story-visual" aria-hidden="true">
            <span className="auth-orbit auth-orbit-one" /><span className="auth-orbit auth-orbit-two" />
            <span className="auth-orbit-center"><AppIcon name="sparkle" size={31} /></span>
            <span className="auth-orbit-chip auth-orbit-chip-one"><AppIcon name="briefcase" size={17} /> Opportunities</span>
            <span className="auth-orbit-chip auth-orbit-chip-two"><AppIcon name="workflow" size={17} /> Better processes</span>
          </div>
        </div>
        <div className="auth-story-footer">Where the next generation gets its start. <AppIcon name="arrow-up-right" size={17} /></div>
      </aside>
      <div className="auth-form-pane">
        <div className="login-card card">{children}</div>
        <div className="auth-form-footer">© {new Date().getFullYear()} PATH · Company portal</div>
      </div>
    </div>
  )
}
