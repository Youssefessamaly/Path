import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ErrorView, LoadingView } from '../../components/StateViews'
import { ScreeningRequirementsEditor } from '../../components/ScreeningRequirementsEditor'
import {
  activateScreeningProfile,
  addScreeningProfileRequirement,
  archiveScreeningProfile,
  deleteScreeningProfileRequirement,
  duplicateScreeningProfile,
  getScreeningProfile,
  getSkillCatalog,
  updateScreeningProfile,
  updateScreeningProfileRequirement,
  type InternshipSkill,
  type ScreeningProfile,
} from '../../lib/api'

function describeError(err: unknown, fallback: string): string {
  return err instanceof Error ? err.message : fallback
}

/**
 * Configure one reusable ScreeningProfile template: name/description, archive/activate, and
 * its requirements (via the shared ScreeningRequirementsEditor, also used by an internship's
 * rubric draft — see RubricSection). Archiving never affects a rubric already copied from this
 * profile; it only stops the profile being offered for NEW rubrics going forward.
 */
export function ScreeningProfileEditorPage() {
  const { profileId } = useParams<{ profileId: string }>()
  const navigate = useNavigate()

  const [profile, setProfile] = useState<ScreeningProfile | null>(null)
  const [skillCatalog, setSkillCatalog] = useState<InternshipSkill[]>([])
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const [nameDraft, setNameDraft] = useState('')
  const [descriptionDraft, setDescriptionDraft] = useState('')
  const [isSavingDetails, setIsSavingDetails] = useState(false)

  const load = useCallback(() => {
    if (!profileId) return
    setError(null)
    getScreeningProfile(profileId)
      .then((loaded) => {
        setProfile(loaded)
        setNameDraft(loaded.name)
        setDescriptionDraft(loaded.description ?? '')
      })
      .catch((err: unknown) => setError(describeError(err, 'Unable to load this screening profile.')))
  }, [profileId])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    getSkillCatalog().then(setSkillCatalog).catch(() => setSkillCatalog([]))
  }, [])

  if (!profileId) {
    return <ErrorView message="No screening profile selected." onRetry={() => navigate('/screening-profiles')} />
  }
  if (error) {
    return <ErrorView message={error} onRetry={load} />
  }
  if (!profile) {
    return <LoadingView />
  }

  const canEdit = profile.status === 'ACTIVE'

  const saveDetails = async (event: FormEvent) => {
    event.preventDefault()
    const trimmed = nameDraft.trim()
    if (!trimmed) return
    setActionError(null)
    setIsSavingDetails(true)
    try {
      const updated = await updateScreeningProfile(profile.id, { name: trimmed, description: descriptionDraft.trim() })
      setProfile(updated)
    } catch (err) {
      setActionError(describeError(err, 'Unable to save changes.'))
    } finally {
      setIsSavingDetails(false)
    }
  }

  const handleArchive = async () => {
    setActionError(null)
    try {
      setProfile(await archiveScreeningProfile(profile.id))
    } catch (err) {
      setActionError(describeError(err, 'Unable to archive this profile.'))
    }
  }

  const handleActivate = async () => {
    setActionError(null)
    try {
      setProfile(await activateScreeningProfile(profile.id))
    } catch (err) {
      setActionError(describeError(err, 'Unable to reactivate this profile.'))
    }
  }

  const handleDuplicate = async () => {
    setActionError(null)
    try {
      const duplicate = await duplicateScreeningProfile(profile.id)
      navigate(`/screening-profiles/${duplicate.id}`)
    } catch (err) {
      setActionError(describeError(err, 'Unable to duplicate this profile.'))
    }
  }

  return (
    <div>
      <button type="button" className="back-link" onClick={() => navigate('/screening-profiles')}>
        ← Back to screening profiles
      </button>

      <div className="page-header">
        <div>
          <h1>{profile.name}</h1>
          <div className="row-meta mt-2">
            {profile.status === 'ARCHIVED' && <span className="badge badge-muted">Archived</span>}
            <span className="text-tertiary">{profile.requirements.length} requirement{profile.requirements.length === 1 ? '' : 's'}</span>
          </div>
        </div>

        <div className="row-actions">
          <button type="button" className="btn btn-secondary" onClick={handleDuplicate}>
            Duplicate
          </button>
          {profile.status === 'ACTIVE' ? (
            <button type="button" className="btn btn-danger" onClick={handleArchive}>
              Archive
            </button>
          ) : (
            <button type="button" className="btn btn-secondary" onClick={handleActivate}>
              Reactivate
            </button>
          )}
        </div>
      </div>

      {profile.status === 'ARCHIVED' && (
        <div className="banner banner-warning mb-4" role="status">
          This profile is archived and shown read-only. It will not be offered when creating a new internship rubric,
          but any rubric already copied from it is unaffected.
        </div>
      )}

      {actionError && <div className="banner banner-error mb-4" role="alert">{actionError}</div>}

      {canEdit && (
        <div className="card mb-4">
          <h2 className="section-title">Details</h2>
          <form onSubmit={saveDetails} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div className="field">
              <label htmlFor="profile-name">Name</label>
              <input id="profile-name" value={nameDraft} onChange={(event) => setNameDraft(event.target.value)} maxLength={150} required />
            </div>
            <div className="field">
              <label htmlFor="profile-description">Description</label>
              <textarea
                id="profile-description"
                value={descriptionDraft}
                onChange={(event) => setDescriptionDraft(event.target.value)}
                maxLength={2000}
                placeholder="Optional — what this screening template is for"
              />
            </div>
            <div>
              <button type="submit" className="btn btn-primary" disabled={isSavingDetails || !nameDraft.trim()}>
                {isSavingDetails ? 'Saving…' : 'Save details'}
              </button>
            </div>
          </form>
        </div>
      )}

      {!canEdit && profile.description && (
        <div className="card mb-4">
          <p className="text-secondary">{profile.description}</p>
        </div>
      )}

      <div className="card">
        <h2 className="section-title">Requirements</h2>
        <ScreeningRequirementsEditor
          requirements={profile.requirements}
          readOnly={!canEdit}
          skillCatalog={skillCatalog}
          onAdd={async (payload) => {
            await addScreeningProfileRequirement(profile.id, payload)
            load()
          }}
          onUpdate={async (requirementId, payload) => {
            await updateScreeningProfileRequirement(profile.id, requirementId, payload)
            load()
          }}
          onDelete={async (requirementId) => {
            await deleteScreeningProfileRequirement(profile.id, requirementId)
            load()
          }}
        />
      </div>
    </div>
  )
}
