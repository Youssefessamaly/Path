import type { ReactNode } from 'react'

type IconName =
  | 'overview' | 'briefcase' | 'workflow' | 'chevron-right' | 'arrow-right'
  | 'arrow-up-right' | 'plus' | 'logout' | 'menu' | 'close' | 'calendar'
  | 'check' | 'clock' | 'sparkle' | 'layers' | 'search' | 'map-pin'
  | 'users' | 'shield' | 'settings' | 'target' | 'file' | 'copy'
  | 'building' | 'phone' | 'globe' | 'mail' | 'info' | 'refresh'

const paths: Record<IconName, ReactNode> = {
  overview: <><rect x="3.5" y="3.5" width="7" height="7" rx="1.5" /><rect x="13.5" y="3.5" width="7" height="7" rx="1.5" /><rect x="3.5" y="13.5" width="7" height="7" rx="1.5" /><rect x="13.5" y="13.5" width="7" height="7" rx="1.5" /></>,
  briefcase: <><rect x="3" y="7" width="18" height="14" rx="2.5" /><path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M3 12.5c4 3.2 14 3.2 18 0M12 14v2" /></>,
  workflow: <><rect x="3" y="3" width="7" height="7" rx="2" /><rect x="14" y="14" width="7" height="7" rx="2" /><path d="M10 6.5h5a3 3 0 0 1 3 3V14M6.5 10v8a3 3 0 0 0 3 3H14" /></>,
  'chevron-right': <path d="m9 18 6-6-6-6" />,
  'arrow-right': <><path d="M4 12h16m-6-6 6 6-6 6" /></>,
  'arrow-up-right': <><path d="M5 19 19 5M8 5h11v11" /></>,
  plus: <path d="M12 5v14M5 12h14" />,
  logout: <><path d="M10 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h4" /><path d="M13 8l4 4-4 4m-9-4h13" /></>,
  menu: <path d="M4 7h16M4 12h16M4 17h16" />,
  close: <path d="M5 5l14 14M19 5 5 19" />,
  calendar: <><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M7 3v4m10-4v4M3 10h18" /></>,
  check: <><circle cx="12" cy="12" r="9" /><path d="m8 12 2.8 2.8L16.5 9" /></>,
  clock: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
  sparkle: <><path d="m12 2 2.6 6.9L21.5 12l-6.9 3.1L12 22l-2.6-6.9L2.5 12l6.9-3.1L12 2Z" /></>,
  layers: <><path d="m12 3 9 5-9 5-9-5 9-5Zm-9 10 9 5 9-5M3 18l9 5 9-5" /></>,
  search: <><circle cx="10.8" cy="10.8" r="6.8" /><path d="m16 16 5 5" /></>,
  'map-pin': <><path d="M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0Z" /><circle cx="12" cy="10" r="2.5" /></>,
  building: <><rect x="4" y="3" width="16" height="18" rx="2" /><path d="M9 21v-5h6v5M8 8h2m4 0h2M8 12h2m4 0h2" /></>,
  phone: <path d="M8 3H5a2 2 0 0 0-2 2c0 8.8 7.2 16 16 16a2 2 0 0 0 2-2v-3l-5-2-2.5 2.5A16 16 0 0 1 7.5 10L10 7.5 8 3Z" />,
  globe: <><circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3c-6 5-6 13 0 18M12 3c6 5 6 13 0 18" /></>,
  mail: <><rect x="2.5" y="5" width="19" height="14" rx="2" /><path d="m3 7 9 7 9-7" /></>,
  info: <><circle cx="12" cy="12" r="9" /><path d="M12 11v6M12 7.5h.01" /></>,
  refresh: <><path d="M20 8A8 8 0 0 0 6 5L4 7m0-4v4h4M4 16a8 8 0 0 0 14 3l2-2m0 4v-4h-4" /></>,
  users: <><circle cx="9" cy="8" r="3" /><path d="M3.5 20v-2a5.5 5.5 0 0 1 11 0v2M16 5.5a3 3 0 0 1 0 6M17 15a4.5 4.5 0 0 1 4 4.5V20" /></>,
  shield: <><path d="m12 2 8 3v6c0 5.2-3.3 8.6-8 11-4.7-2.4-8-5.8-8-11V5l8-3Z" /><path d="m9 11.5 2 2 4-4" /></>,
  settings: <><circle cx="12" cy="12" r="3" /><path d="M12 2v3m0 14v3M2 12h3m14 0h3M4.9 4.9 7 7m10 10 2.1 2.1m0-14.2L17 7M7 17l-2.1 2.1" /></>,
  target: <><circle cx="12" cy="12" r="9" /><circle cx="12" cy="12" r="5" /><circle cx="12" cy="12" r="1" /></>,
  file: <><path d="M6 3h8l5 5v13H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z" /><path d="M14 3v6h5M8 13h7M8 17h7" /></>,
  copy: <><rect x="8" y="8" width="12" height="13" rx="2" /><path d="M16 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h3" /></>,
}

export function AppIcon({ name, size = 20, className = '' }: { name: IconName; size?: number; className?: string }) {
  return (
    <svg aria-hidden="true" className={className} width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round">
      {paths[name]}
    </svg>
  )
}

/** Exact mark used by the mobile app splash and PathoraLogoMark (viewBox/path/dot). */
export function BrandMark({ className = '', onDark = false }: { className?: string; onDark?: boolean }) {
  return (
    <span className={`path-brand-mark ${className}`} aria-hidden="true">
      <svg width="40" height="40" viewBox="0 0 64 64" fill="none">
        <path d="M10 46 C18 46, 22 34, 30 34 C38 34, 38 20, 50 18" stroke={onDark ? '#FFFFFF' : '#147D6F'} strokeWidth="6" strokeLinecap="round" fill="none" />
        <circle cx="50" cy="18" r="4.5" fill="#E8A33D" />
      </svg>
    </span>
  )
}
