import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { EmptyView, ErrorView, LoadingView } from '../../components/StateViews'
import { AppIcon } from '../../components/Visuals'
import { ApiError, createScreeningProfile, duplicateScreeningProfile, getScreeningProfiles, type ScreeningProfile } from '../../lib/api'

function describeError(err: unknown, fallback: string): string {
  return err instanceof Error ? err.message : fallback
}

function ProfileRow({ profile, onOpen, onDuplicate }: {
  profile: ScreeningProfile
  onOpen: () => void
  onDuplicate: () => void
}) {
  return (
    <div className="card row-item template-row">
      <span className="list-card-icon"><AppIcon name="target" size={20} /></span>
      <button className="template-open" type="button" onClick={onOpen} aria-label={`Open ${profile.name}`}>
        <strong>{profile.name}</strong>
        <span>{profile.description || 'Reusable screening template'}</span>
      </button>
      <div className="row-meta">
        <span>{profile.requirements.length} requirement{profile.requirements.length === 1 ? '' : 's'}</span>
        {profile.status === 'ARCHIVED' && <span className="badge badge-muted">Archived</span>}
      </div>
      <div className="row-actions">
        <button type="button" className="btn btn-secondary btn-sm" onClick={onDuplicate} aria-label={`Duplicate ${profile.name}`}>
          <AppIcon name="copy" size={15} /> Duplicate
        </button>
      </div>
      <button type="button" className="template-row-arrow" onClick={onOpen} aria-label={`View ${profile.name}`}>
        <AppIcon name="chevron-right" size={19} />
      </button>
    </div>
  )
}

/**
 * Reusable screening-profile templates — see ScreeningProfileService on the backend. Any
 * active company member may create/edit/duplicate these (no OWNER restriction, unlike pipeline
 * templates), matching CompanyAccessResolver.requireActiveMember exactly.
 */
export function ScreeningProfilesListPage() {
  const navigate = useNavigate()

  const [profiles, setProfiles] = useState<ScreeningProfile[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [showArchived, setShowArchived] = useState(false)
  const [newName, setNewName] = useState('')
  const [isCreating, setIsCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  const load = useCallback(() => {
    setProfiles(null)
    setError(null)
    getScreeningProfiles()
      .then(setProfiles)
      .catch((err: unknown) => {
        setError(err instanceof ApiError ? err.message : 'Unable to load screening profiles.')
      })
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault()
    const trimmed = newName.trim()
    if (!trimmed || isCreating) return
    setCreateError(null)
    setIsCreating(true)
    try {
      const created = await createScreeningProfile({ name: trimmed })
      navigate(`/screening-profiles/${created.id}`)
    } catch (err) {
      setCreateError(describeError(err, 'Unable to create the screening profile.'))
    } finally {
      setIsCreating(false)
    }
  }

  const handleDuplicate = async (profileId: string) => {
    try {
      const duplicate = await duplicateScreeningProfile(profileId)
      navigate(`/screening-profiles/${duplicate.id}`)
    } catch (err) {
      setError(describeError(err, 'Unable to duplicate this profile.'))
    }
  }

  const visibleProfiles = profiles?.filter((profile) => showArchived || profile.status === 'ACTIVE') ?? null

  return (
    <div>
      <div className="page-header">
        <div>
          <span className="list-page-intro"><AppIcon name="target" size={15} /> SMART SCREENING</span>
          <h1>Screening profiles</h1>
          <p className="page-header-subtitle">
            Reusable sets of required, preferred, and bonus requirements you can reuse across internship rubrics.
          </p>
        </div>
      </div>

      <div className="card mb-4 template-create-panel">
        <div className="template-create-intro">
          <span><AppIcon name="plus" size={19} /></span>
          <div>
            <strong>Create a new profile</strong>
            <p>Give it a name, then configure its requirements.</p>
          </div>
        </div>
        <form className="add-stage-form" onSubmit={handleCreate}>
          <input
            type="text"
            placeholder="e.g. Backend Engineering Screening"
            value={newName}
            onChange={(event) => setNewName(event.target.value)}
            disabled={isCreating}
            aria-label="New screening profile name"
            maxLength={150}
          />
          <button type="submit" className="btn btn-primary" disabled={!newName.trim() || isCreating}>
            <AppIcon name="plus" size={17} />
            {isCreating ? 'Creating…' : 'Create profile'}
          </button>
        </form>
        {createError && <div className="banner banner-error mt-2" role="alert">{createError}</div>}
      </div>

      <div className="list-page-toolbar">
        <div className="filter-tabs" role="group" aria-label="Filter screening profiles">
          <button type="button" className={`filter-tab${!showArchived ? ' active' : ''}`} aria-pressed={!showArchived} onClick={() => setShowArchived(false)}>
            Active
          </button>
          <button type="button" className={`filter-tab${showArchived ? ' active' : ''}`} aria-pressed={showArchived} onClick={() => setShowArchived(true)}>
            All (including archived)
          </button>
        </div>
        {visibleProfiles && (
          <span className="templates-list-count">
            {visibleProfiles.length} profile{visibleProfiles.length === 1 ? '' : 's'}
          </span>
        )}
      </div>

      {profiles === null && !error && <LoadingView />}
      {error && <ErrorView message={error} onRetry={load} />}
      {visibleProfiles !== null && !error && visibleProfiles.length === 0 && (
        <EmptyView title="No screening profiles yet" body="Create one above to start reusing it across internships." />
      )}
      {visibleProfiles !== null && !error && visibleProfiles.length > 0 && (
        <div className="row-list">
          {visibleProfiles.map((profile) => (
            <ProfileRow
              key={profile.id}
              profile={profile}
              onOpen={() => navigate(`/screening-profiles/${profile.id}`)}
              onDuplicate={() => handleDuplicate(profile.id)}
            />
          ))}
        </div>
      )}
    </div>
  )
}
