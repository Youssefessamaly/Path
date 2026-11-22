import { useState, type FormEvent } from 'react'
import {
  ApiError,
  type EvidenceSource,
  type EvidenceStrength,
  type InternshipSkill,
  type RequirementType,
  type ScreeningRequirement,
  type ScreeningRequirementCreatePayload,
  type ScreeningRequirementUpdatePayload,
} from '../lib/api'
import { AppIcon } from './Visuals'

/**
 * Reusable requirement CRUD UI shared by a ScreeningProfile (reusable template) and an
 * internship's InternshipRubric draft — the backend already shares one Requirement DTO shape
 * for both, so this client does too (see lib/api.ts). readOnly renders the same data without
 * any edit affordance — used for an archived profile or an ACTIVE/ARCHIVED rubric, which the
 * backend itself refuses to mutate (409).
 */

const TYPE_LABELS: Record<RequirementType, string> = {
  REQUIRED: 'Required',
  PREFERRED: 'Preferred',
  BONUS: 'Bonus',
}

const TYPE_BADGE_CLASS: Record<RequirementType, string> = {
  REQUIRED: 'badge-success',
  PREFERRED: 'badge-neutral',
  BONUS: 'badge-muted',
}

const STRENGTH_LABELS: Record<EvidenceStrength, string> = {
  STRONG: 'Strong',
  MODERATE: 'Moderate',
  WEAK: 'Weak',
}

// UNKNOWN is intentionally omitted from the picker — "only accept evidence from unstructured
// text" is not a meaningful restriction a recruiter would choose. Leaving every source
// unchecked already means "no restriction," which covers that case.
const SOURCE_OPTIONS: { value: EvidenceSource; label: string }[] = [
  { value: 'PROJECT', label: 'Project' },
  { value: 'WORK_EXPERIENCE', label: 'Work experience' },
  { value: 'INTERNSHIP', label: 'Internship' },
  { value: 'RESEARCH', label: 'Research' },
  { value: 'CERTIFICATION', label: 'Certification' },
  { value: 'COURSEWORK', label: 'Coursework' },
  { value: 'SKILLS_LIST', label: 'Skills list' },
]

function describeError(err: unknown, fallback: string): string {
  if (err instanceof ApiError && err.status === 409) {
    return 'This profile/rubric already has a requirement for that skill.'
  }
  return err instanceof Error ? err.message : fallback
}

type DraftFields = {
  requirementType: RequirementType
  skillId: string
  label: string
  weight: string
  minimumEvidenceStrength: EvidenceStrength | ''
  acceptedEvidenceSources: EvidenceSource[]
  recruiterNotes: string
}

const EMPTY_DRAFT: DraftFields = {
  requirementType: 'REQUIRED',
  skillId: '',
  label: '',
  weight: '50',
  minimumEvidenceStrength: '',
  acceptedEvidenceSources: [],
  recruiterNotes: '',
}

function toCreatePayload(draft: DraftFields, displayOrder: number): ScreeningRequirementCreatePayload | null {
  const weight = Number(draft.weight)
  if (!Number.isFinite(weight) || weight < 1 || weight > 100) {
    return null
  }
  const skillId = draft.skillId.trim()
  const label = draft.label.trim()
  if (!skillId && !label) {
    return null
  }
  return {
    requirementType: draft.requirementType,
    skillId: skillId || undefined,
    label: skillId ? undefined : label,
    weight,
    minimumEvidenceStrength: draft.minimumEvidenceStrength || undefined,
    acceptedEvidenceSources: draft.acceptedEvidenceSources.length > 0 ? draft.acceptedEvidenceSources : undefined,
    recruiterNotes: draft.recruiterNotes.trim() || undefined,
    displayOrder,
  }
}

function toUpdatePayload(draft: DraftFields): ScreeningRequirementUpdatePayload {
  const weight = Number(draft.weight)
  return {
    requirementType: draft.requirementType,
    label: draft.skillId ? undefined : draft.label.trim() || undefined,
    weight: Number.isFinite(weight) && weight >= 1 && weight <= 100 ? weight : undefined,
    minimumEvidenceStrength: draft.minimumEvidenceStrength || undefined,
    acceptedEvidenceSources: draft.acceptedEvidenceSources,
    recruiterNotes: draft.recruiterNotes.trim() || undefined,
  }
}

function toggleSource(sources: EvidenceSource[], source: EvidenceSource): EvidenceSource[] {
  return sources.includes(source) ? sources.filter((existing) => existing !== source) : [...sources, source]
}

function RequirementFields({
  idPrefix,
  draft,
  setDraft,
  skillCatalog,
  allowSkillChange,
}: {
  idPrefix: string
  draft: DraftFields
  setDraft: (next: DraftFields) => void
  skillCatalog: InternshipSkill[]
  /** False when editing an existing requirement — the backend has no way to switch a
   *  requirement between skill-based and label-based, so the skill picker is locked then. */
  allowSkillChange: boolean
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div className="field">
        <label htmlFor={`${idPrefix}-type`}>Classification</label>
        <select
          id={`${idPrefix}-type`}
          value={draft.requirementType}
          onChange={(event) => setDraft({ ...draft, requirementType: event.target.value as RequirementType })}
        >
          <option value="REQUIRED">Required</option>
          <option value="PREFERRED">Preferred</option>
          <option value="BONUS">Bonus</option>
        </select>
      </div>

      {allowSkillChange ? (
        <div className="field">
          <label htmlFor={`${idPrefix}-skill`}>Canonical skill</label>
          <select
            id={`${idPrefix}-skill`}
            value={draft.skillId}
            onChange={(event) => setDraft({ ...draft, skillId: event.target.value })}
          >
            <option value="">— Not a catalog skill (use a free-text qualification below) —</option>
            {skillCatalog.map((skill) => (
              <option key={skill.id} value={skill.id}>
                {skill.name}
              </option>
            ))}
          </select>
          <span className="field-hint">Pick a catalog skill, or leave blank and describe a free-text qualification instead.</span>
        </div>
      ) : null}

      {(!draft.skillId || !allowSkillChange) && (
        <div className="field">
          <label htmlFor={`${idPrefix}-label`}>{allowSkillChange ? 'Free-text qualification' : 'Label'}</label>
          <input
            id={`${idPrefix}-label`}
            type="text"
            value={draft.label}
            onChange={(event) => setDraft({ ...draft, label: event.target.value })}
            maxLength={200}
            placeholder="e.g. Valid driving license"
            disabled={allowSkillChange && draft.skillId !== ''}
          />
          <span className="field-hint">
            A requirement with no canonical skill is always shown as needing manual recruiter review during screening.
          </span>
        </div>
      )}

      <div className="field">
        <label htmlFor={`${idPrefix}-weight`}>Weight (1–100)</label>
        <input
          id={`${idPrefix}-weight`}
          type="number"
          min={1}
          max={100}
          value={draft.weight}
          onChange={(event) => setDraft({ ...draft, weight: event.target.value })}
        />
        <span className="field-hint">How much this requirement contributes to the overall weighted coverage shown during screening.</span>
      </div>

      <div className="field">
        <label htmlFor={`${idPrefix}-strength`}>Minimum evidence strength</label>
        <select
          id={`${idPrefix}-strength`}
          value={draft.minimumEvidenceStrength}
          onChange={(event) => setDraft({ ...draft, minimumEvidenceStrength: event.target.value as EvidenceStrength | '' })}
        >
          <option value="">No minimum</option>
          <option value="WEAK">Weak</option>
          <option value="MODERATE">Moderate</option>
          <option value="STRONG">Strong</option>
        </select>
      </div>

      <div className="field">
        <label>Accepted evidence sources</label>
        <div className="row-meta">
          {SOURCE_OPTIONS.map((option) => (
            <div className="checkbox-field" key={option.value}>
              <input
                id={`${idPrefix}-source-${option.value}`}
                type="checkbox"
                checked={draft.acceptedEvidenceSources.includes(option.value)}
                onChange={() => setDraft({ ...draft, acceptedEvidenceSources: toggleSource(draft.acceptedEvidenceSources, option.value) })}
              />
              <label htmlFor={`${idPrefix}-source-${option.value}`}>{option.label}</label>
            </div>
          ))}
        </div>
        <span className="field-hint">Leave all unchecked to accept evidence from any CV section.</span>
      </div>

      <div className="field">
        <label htmlFor={`${idPrefix}-notes`}>Recruiter notes (optional)</label>
        <textarea
          id={`${idPrefix}-notes`}
          value={draft.recruiterNotes}
          onChange={(event) => setDraft({ ...draft, recruiterNotes: event.target.value })}
          maxLength={1000}
        />
      </div>
    </div>
  )
}

function RequirementRow({
  requirement,
  readOnly,
  skillCatalog,
  onUpdate,
  onDelete,
}: {
  requirement: ScreeningRequirement
  readOnly: boolean
  skillCatalog: InternshipSkill[]
  onUpdate: (payload: ScreeningRequirementUpdatePayload) => Promise<void>
  onDelete: () => Promise<void>
}) {
  const [isEditing, setIsEditing] = useState(false)
  const [draft, setDraft] = useState<DraftFields>({
    requirementType: requirement.requirementType,
    skillId: requirement.skillId ?? '',
    label: requirement.label ?? '',
    weight: String(requirement.weight),
    minimumEvidenceStrength: requirement.minimumEvidenceStrength ?? '',
    acceptedEvidenceSources: requirement.acceptedEvidenceSources,
    recruiterNotes: requirement.recruiterNotes ?? '',
  })
  const [error, setError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)

  const handleSave = async () => {
    setError(null)
    setIsSaving(true)
    try {
      await onUpdate(toUpdatePayload(draft))
      setIsEditing(false)
    } catch (err) {
      setError(describeError(err, 'Unable to save this requirement.'))
    } finally {
      setIsSaving(false)
    }
  }

  if (isEditing) {
    return (
      <div className="card">
        <RequirementFields idPrefix={`req-${requirement.id}`} draft={draft} setDraft={setDraft} skillCatalog={skillCatalog} allowSkillChange={false} />
        {error && <div className="banner banner-error mt-2" role="alert">{error}</div>}
        <div className="row-actions mt-4">
          <button type="button" className="btn btn-primary btn-sm" onClick={handleSave} disabled={isSaving}>
            {isSaving ? 'Saving…' : 'Save'}
          </button>
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => setIsEditing(false)} disabled={isSaving}>
            Cancel
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="card row-item">
      <div style={{ flex: 1, minWidth: 0 }}>
        <div className="row-meta">
          <span className={`badge ${TYPE_BADGE_CLASS[requirement.requirementType]}`}>{TYPE_LABELS[requirement.requirementType]}</span>
          <strong>{requirement.skillName ?? requirement.label}</strong>
          <span className="text-tertiary">Weight {requirement.weight}</span>
          {requirement.minimumEvidenceStrength && (
            <span className="badge badge-muted">Min: {STRENGTH_LABELS[requirement.minimumEvidenceStrength]}</span>
          )}
        </div>
        {requirement.acceptedEvidenceSources.length > 0 && (
          <p className="text-tertiary mt-2">
            Evidence accepted from: {requirement.acceptedEvidenceSources.map((source) => SOURCE_OPTIONS.find((option) => option.value === source)?.label ?? source).join(', ')}
          </p>
        )}
        {requirement.recruiterNotes && <p className="text-secondary mt-2">{requirement.recruiterNotes}</p>}
        {!requirement.skillId && (
          <p className="text-tertiary mt-2">
            <AppIcon name="info" size={13} /> Free-text qualification — always flagged for manual review during screening.
          </p>
        )}
      </div>

      {!readOnly && (
        <div className="row-actions">
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => setIsEditing(true)}>
            Edit
          </button>
          <button type="button" className="icon-btn icon-btn-danger" aria-label="Delete requirement" onClick={onDelete}>
            ✕
          </button>
        </div>
      )}
    </div>
  )
}

export function ScreeningRequirementsEditor({
  requirements,
  readOnly,
  skillCatalog,
  onAdd,
  onUpdate,
  onDelete,
}: {
  requirements: ScreeningRequirement[]
  readOnly: boolean
  skillCatalog: InternshipSkill[]
  onAdd: (payload: ScreeningRequirementCreatePayload) => Promise<void>
  onUpdate: (requirementId: string, payload: ScreeningRequirementUpdatePayload) => Promise<void>
  onDelete: (requirementId: string) => Promise<void>
}) {
  const [draft, setDraft] = useState<DraftFields>(EMPTY_DRAFT)
  const [isAdding, setIsAdding] = useState(false)
  const [addError, setAddError] = useState<string | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const handleAdd = async (event: FormEvent) => {
    event.preventDefault()
    const payload = toCreatePayload(draft, requirements.length)
    if (!payload) {
      setAddError('Enter a weight between 1 and 100, and either a canonical skill or a free-text qualification.')
      return
    }
    setAddError(null)
    setIsAdding(true)
    try {
      await onAdd(payload)
      setDraft(EMPTY_DRAFT)
    } catch (err) {
      setAddError(describeError(err, 'Unable to add this requirement.'))
    } finally {
      setIsAdding(false)
    }
  }

  const handleDelete = async (requirementId: string) => {
    setDeleteError(null)
    try {
      await onDelete(requirementId)
    } catch (err) {
      setDeleteError(describeError(err, 'Unable to delete this requirement.'))
    }
  }

  return (
    <div>
      {requirements.length === 0 ? (
        <p className="text-secondary">No requirements configured yet.</p>
      ) : (
        <div className="row-list">
          {requirements.map((requirement) => (
            <RequirementRow
              key={requirement.id}
              requirement={requirement}
              readOnly={readOnly}
              skillCatalog={skillCatalog}
              onUpdate={(payload) => onUpdate(requirement.id, payload)}
              onDelete={() => handleDelete(requirement.id)}
            />
          ))}
        </div>
      )}

      {deleteError && <div className="banner banner-error mt-4" role="alert">{deleteError}</div>}

      {!readOnly && (
        <form className="card mt-4" onSubmit={handleAdd}>
          <h3 className="section-title">Add a requirement</h3>
          <RequirementFields idPrefix="new-req" draft={draft} setDraft={setDraft} skillCatalog={skillCatalog} allowSkillChange />
          {addError && <div className="banner banner-error mt-2" role="alert">{addError}</div>}
          <div className="mt-4">
            <button type="submit" className="btn btn-primary" disabled={isAdding}>
              {isAdding ? 'Adding…' : 'Add requirement'}
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
