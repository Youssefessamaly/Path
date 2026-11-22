import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { EmptyView, ErrorView, LoadingView } from '../../components/StateViews'
import { ScreeningRequirementsEditor } from '../../components/ScreeningRequirementsEditor'
import { AiRubricDraftPanel } from '../../components/AiRubricDraftPanel'
import { AiEvidenceAnalysisPanel } from '../../components/AiEvidenceAnalysisPanel'
import { AppIcon } from '../../components/Visuals'
import { useAuth } from '../../auth/useAuth'
import {
  ApiError,
  activateRubricDraft,
  addPipelineStage,
  addRubricRequirement,
  assignInternshipPipeline,
  closeCompanyInternship,
  createRubricDraft,
  deletePipelineStage,
  deleteRubricRequirement,
  discardRubricDraft,
  downloadApplicationCv,
  duplicateRubric,
  getCompanyInternship,
  getInternshipPipeline,
  getLatestScreeningReport,
  getPipelineTemplates,
  getScreeningProfiles,
  getScreeningSummaries,
  getSkillCatalog,
  listRubricVersions,
  renamePipelineStage,
  reorderPipelineStages,
  screenApplicant,
  screenApplicantsBatch,
  updateRubricRequirement,
  MAX_SCREENING_BATCH_SIZE,
  type ApplicationStatus,
  type CompanyInternship,
  type InternshipPipeline,
  type InternshipRubric,
  type InternshipSkill,
  type PipelineStage,
  type PipelineTemplate,
  type RubricStatus,
  type ScreeningOutcome,
  type ScreeningProfile,
  type ScreeningReport,
  type ScreeningSummary,
} from '../../lib/api'
import { formatDisplayDate } from '../../lib/date'

/** Mirrors PipelineTemplateService.starterStageNames() on the backend — display-only, for
 *  previewing the default pipeline before assignment. The backend remains the sole source of
 *  truth for what actually gets created on assignment. */
const STARTER_STAGE_NAMES = ['Applied', 'Screening', 'Interview', 'Offer']

const WORK_MODE_LABELS: Record<CompanyInternship['workMode'], string> = {
  ONSITE: 'On-site',
  HYBRID: 'Hybrid',
  REMOTE: 'Remote',
}

function describeError(err: unknown, fallback: string): string {
  return err instanceof Error ? err.message : fallback
}

function PipelineStageRow({
  stage,
  index,
  total,
  canEdit,
  onRename,
  onDelete,
  onMoveUp,
  onMoveDown,
}: {
  stage: PipelineStage
  index: number
  total: number
  canEdit: boolean
  onRename: (name: string) => void
  onDelete: () => void
  onMoveUp: () => void
  onMoveDown: () => void
}) {
  const [isEditing, setIsEditing] = useState(false)
  const [draftName, setDraftName] = useState(stage.name)

  const commitRename = () => {
    const trimmed = draftName.trim()
    setIsEditing(false)
    if (trimmed && trimmed !== stage.name) {
      onRename(trimmed)
    } else {
      setDraftName(stage.name)
    }
  }

  return (
    <div className="stage-row">
      <span className="stage-row-index">{index + 1}</span>

      {isEditing ? (
        <input
          autoFocus
          value={draftName}
          onChange={(event) => setDraftName(event.target.value)}
          onBlur={commitRename}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              commitRename()
            }
            if (event.key === 'Escape') {
              setDraftName(stage.name)
              setIsEditing(false)
            }
          }}
          maxLength={80}
          aria-label={`Stage ${index + 1} name`}
          style={{ flex: 1 }}
        />
      ) : (
        <span
          className="stage-row-name"
          onClick={() => canEdit && setIsEditing(true)}
          role={canEdit ? 'button' : undefined}
          tabIndex={canEdit ? 0 : undefined}
        >
          {stage.name}
        </span>
      )}

      {canEdit && !isEditing && (
        <div className="stage-row-controls">
          <button type="button" className="icon-btn" aria-label="Move stage up" disabled={index === 0} onClick={onMoveUp}>
            ↑
          </button>
          <button
            type="button"
            className="icon-btn"
            aria-label="Move stage down"
            disabled={index === total - 1}
            onClick={onMoveDown}
          >
            ↓
          </button>
          <button type="button" className="icon-btn icon-btn-danger" aria-label="Delete stage" onClick={onDelete}>
            ✕
          </button>
        </div>
      )}
    </div>
  )
}

/**
 * The Recruitment Pipeline section of the internship detail page. Entirely backed by the
 * existing InternshipPipelineController endpoints (GET/POST .../pipeline, stage CRUD + reorder)
 * — no new backend functionality. Three states: no pipeline assigned yet (an OWNER can assign
 * one from an existing template or the hardcoded starter), a pipeline with its ordered stages
 * (OWNER can add/rename/reorder/delete), or a load error. This is pipeline STRUCTURE only —
 * no candidate, application, or Kanban concept is touched here.
 */
function RecruitmentPipelineSection({ internshipId }: { internshipId: string }) {
  const { membership } = useAuth()
  const isOwner = membership?.role === 'OWNER'

  const [pipeline, setPipeline] = useState<InternshipPipeline | null>(null)
  const [hasNoPipeline, setHasNoPipeline] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const [templates, setTemplates] = useState<PipelineTemplate[] | null>(null)
  const [selectedTemplateId, setSelectedTemplateId] = useState<string>('')
  const [isAssigning, setIsAssigning] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const [newStageName, setNewStageName] = useState('')
  const [isAddingStage, setIsAddingStage] = useState(false)

  const load = useCallback(() => {
    setIsLoading(true)
    setLoadError(null)
    setHasNoPipeline(false)
    getInternshipPipeline(internshipId)
      .then((loaded) => {
        setPipeline(loaded)
      })
      .catch((err: unknown) => {
        if (err instanceof ApiError && err.status === 404) {
          setPipeline(null)
          setHasNoPipeline(true)
        } else {
          setLoadError(describeError(err, 'Unable to load the recruitment pipeline.'))
        }
      })
      .finally(() => setIsLoading(false))
  }, [internshipId])

  useEffect(() => {
    load()
  }, [load])

  // Templates are only needed for the "assign a pipeline" picker, and only for an owner.
  useEffect(() => {
    if (!isOwner || !hasNoPipeline) {
      return
    }
    getPipelineTemplates(false)
      .then(setTemplates)
      .catch(() => setTemplates([]))
  }, [isOwner, hasNoPipeline])

  const handleAssign = async (event: FormEvent) => {
    event.preventDefault()
    setActionError(null)
    setIsAssigning(true)
    try {
      const assigned = await assignInternshipPipeline(internshipId, selectedTemplateId || undefined)
      setPipeline(assigned)
      setHasNoPipeline(false)
    } catch (err) {
      setActionError(describeError(err, 'Unable to assign a pipeline to this internship.'))
    } finally {
      setIsAssigning(false)
    }
  }

  const handleAddStage = async (event: FormEvent) => {
    event.preventDefault()
    const trimmed = newStageName.trim()
    if (!trimmed || isAddingStage) {
      return
    }
    setActionError(null)
    setIsAddingStage(true)
    try {
      await addPipelineStage(internshipId, trimmed)
      setNewStageName('')
      load()
    } catch (err) {
      setActionError(describeError(err, 'Unable to add the stage.'))
    } finally {
      setIsAddingStage(false)
    }
  }

  const handleRenameStage = async (stageId: string, name: string) => {
    setActionError(null)
    try {
      await renamePipelineStage(internshipId, stageId, name)
      load()
    } catch (err) {
      setActionError(describeError(err, 'Unable to rename the stage.'))
    }
  }

  const handleDeleteStage = async (stageId: string) => {
    setActionError(null)
    try {
      await deletePipelineStage(internshipId, stageId)
      load()
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setActionError('A pipeline must have at least one stage.')
      } else {
        setActionError(describeError(err, 'Unable to delete the stage.'))
      }
    }
  }

  const moveStage = async (fromIndex: number, toIndex: number) => {
    if (!pipeline || toIndex < 0 || toIndex >= pipeline.stages.length) {
      return
    }
    const reordered = [...pipeline.stages]
    const [moved] = reordered.splice(fromIndex, 1)
    reordered.splice(toIndex, 0, moved)

    setActionError(null)
    try {
      await reorderPipelineStages(internshipId, reordered.map((stage) => stage.id))
      load()
    } catch (err) {
      setActionError(describeError(err, 'Unable to reorder stages.'))
    }
  }

  return (
    <div className="card mt-6">
      <h2 className="section-title">Recruitment Pipeline</h2>

      {isLoading && <LoadingView />}

      {loadError && <ErrorView message={loadError} onRetry={load} />}

      {!isLoading && !loadError && hasNoPipeline && (
        <div>
          <p className="text-secondary mb-4">No pipeline has been assigned to this internship yet.</p>

          {isOwner ? (
            <form onSubmit={handleAssign} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div className="field">
                <label htmlFor="pipeline-template-select">Pipeline template</label>
                <select
                  id="pipeline-template-select"
                  value={selectedTemplateId}
                  onChange={(event) => setSelectedTemplateId(event.target.value)}
                >
                  <option value="">Default pipeline (Applied → Screening → Interview → Offer)</option>
                  {templates?.map((template) => (
                    <option key={template.id} value={template.id}>
                      {template.name}
                      {template.isDefault ? ' (company default)' : ''}
                    </option>
                  ))}
                </select>
                <span className="field-hint">
                  Leave this as the default to use your company's default template if you have one, or the
                  standard starter pipeline otherwise.
                </span>
              </div>

              <div className="field">
                <label>Stage preview</label>
                <div className="stage-list">
                  {(selectedTemplateId
                    ? (templates?.find((template) => template.id === selectedTemplateId)?.stages.map((stage) => stage.name) ?? [])
                    : STARTER_STAGE_NAMES
                  ).map((name, index) => (
                    <div className="stage-row" key={`${name}-${index}`}>
                      <span className="stage-row-index">{index + 1}</span>
                      <span className="stage-row-name">{name}</span>
                    </div>
                  ))}
                </div>
              </div>

              {actionError && (
                <div className="banner banner-error" role="alert">
                  {actionError}
                </div>
              )}

              <div>
                <button type="submit" className="btn btn-primary" disabled={isAssigning}>
                  {isAssigning ? 'Assigning…' : 'Assign Pipeline'}
                </button>
              </div>
            </form>
          ) : (
            <p className="text-tertiary">Ask your company owner to assign a pipeline.</p>
          )}
        </div>
      )}

      {!isLoading && !loadError && pipeline && (
        <div>
          <div className="row-meta mb-4">
            <span className={`badge ${pipeline.sourceTemplateId ? 'badge-neutral' : 'badge-muted'}`}>
              {pipeline.sourceTemplateName ?? 'Default Pipeline'}
            </span>
            <span className="text-tertiary">
              {pipeline.stages.length} stage{pipeline.stages.length === 1 ? '' : 's'}
            </span>
          </div>

          {actionError && (
            <div className="banner banner-error mb-4" role="alert">
              {actionError}
            </div>
          )}

          <div className="stage-list">
            {pipeline.stages.map((stage, index) => (
              <PipelineStageRow
                key={stage.id}
                stage={stage}
                index={index}
                total={pipeline.stages.length}
                canEdit={isOwner}
                onRename={(name) => handleRenameStage(stage.id, name)}
                onDelete={() => handleDeleteStage(stage.id)}
                onMoveUp={() => moveStage(index, index - 1)}
                onMoveDown={() => moveStage(index, index + 1)}
              />
            ))}
          </div>

          {isOwner && (
            <form className="add-stage-form" onSubmit={handleAddStage}>
              <input
                type="text"
                placeholder="New stage name"
                value={newStageName}
                onChange={(event) => setNewStageName(event.target.value)}
                disabled={isAddingStage}
                maxLength={80}
                aria-label="New stage name"
              />
              <button type="submit" className="btn btn-primary" disabled={!newStageName.trim() || isAddingStage}>
                {isAddingStage ? 'Adding…' : 'Add stage'}
              </button>
            </form>
          )}
        </div>
      )}
    </div>
  )
}

const RUBRIC_STATUS_LABELS: Record<RubricStatus, string> = {
  DRAFT: 'Draft',
  ACTIVE: 'Active',
  ARCHIVED: 'Archived',
}

const RUBRIC_STATUS_BADGE: Record<RubricStatus, string> = {
  DRAFT: 'badge-neutral',
  ACTIVE: 'badge-success',
  ARCHIVED: 'badge-muted',
}

/** A no-op stub for the read-only history rows below, which never render an edit affordance
 *  that could call these — ScreeningRequirementsEditor's own readOnly branch guarantees that. */
async function noopRequirementAction(): Promise<void> {}

/**
 * The Screening Rubric section of the internship detail page — Stage 3's versioned
 * InternshipRubric, entirely backed by the existing CompanyInternshipRubricController
 * endpoints (no new backend). listRubricVersions alone is the single source of truth: the
 * current DRAFT (if any) or ACTIVE version is derived from it, and every other version is
 * shown, read-only, as history — never a second fetch for "the current one."
 */
function RubricSection({ internshipId }: { internshipId: string }) {
  const [versions, setVersions] = useState<InternshipRubric[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const [profiles, setProfiles] = useState<ScreeningProfile[]>([])
  const [skillCatalog, setSkillCatalog] = useState<InternshipSkill[]>([])
  const [selectedProfileId, setSelectedProfileId] = useState('')
  const [showHistory, setShowHistory] = useState(false)

  const [isCreatingDraft, setIsCreatingDraft] = useState(false)
  const [isActivating, setIsActivating] = useState(false)
  const [isDuplicating, setIsDuplicating] = useState(false)
  const [isDiscarding, setIsDiscarding] = useState(false)

  const load = useCallback(() => {
    setLoadError(null)
    listRubricVersions(internshipId)
      .then(setVersions)
      .catch((err: unknown) => setLoadError(describeError(err, 'Unable to load the screening rubric.')))
  }, [internshipId])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    getScreeningProfiles()
      .then((all) => setProfiles(all.filter((profile) => profile.status === 'ACTIVE')))
      .catch(() => setProfiles([]))
    getSkillCatalog().then(setSkillCatalog).catch(() => setSkillCatalog([]))
  }, [])

  const draft = versions?.find((version) => version.status === 'DRAFT') ?? null
  const active = versions?.find((version) => version.status === 'ACTIVE') ?? null
  const current = draft ?? active ?? null
  const history = versions?.filter((version) => version.id !== current?.id) ?? []

  const handleCreateDraft = async (event: FormEvent) => {
    event.preventDefault()
    setActionError(null)
    setIsCreatingDraft(true)
    try {
      await createRubricDraft(internshipId, selectedProfileId || undefined)
      setSelectedProfileId('')
      load()
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setActionError('A draft rubric already exists for this internship.')
        load()
      } else if (err instanceof ApiError && err.status === 404) {
        setActionError('That screening profile could not be found.')
      } else {
        setActionError(describeError(err, 'Unable to create a draft rubric.'))
      }
    } finally {
      setIsCreatingDraft(false)
    }
  }

  const handleActivate = async () => {
    setActionError(null)
    setIsActivating(true)
    try {
      await activateRubricDraft(internshipId)
      load()
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) {
        setActionError('Add at least one requirement before activating this rubric.')
      } else if (err instanceof ApiError && err.status === 404) {
        setActionError('No draft rubric to activate.')
        load()
      } else {
        setActionError(describeError(err, 'Unable to activate this rubric.'))
      }
    } finally {
      setIsActivating(false)
    }
  }

  const handleDuplicate = async () => {
    setActionError(null)
    setIsDuplicating(true)
    try {
      await duplicateRubric(internshipId)
      load()
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setActionError('A draft rubric already exists — edit it below instead of starting another one.')
        load()
      } else if (err instanceof ApiError && err.status === 404) {
        setActionError('No rubric exists yet for this internship.')
      } else {
        setActionError(describeError(err, 'Unable to start a new draft from the current rubric.'))
      }
    } finally {
      setIsDuplicating(false)
    }
  }

  const handleDiscard = async () => {
    if (!window.confirm('Discard this draft rubric? This cannot be undone.')) {
      return
    }
    setActionError(null)
    setIsDiscarding(true)
    try {
      await discardRubricDraft(internshipId)
      load()
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setActionError('No draft rubric to discard.')
        load()
      } else {
        setActionError(describeError(err, 'Unable to discard this draft.'))
      }
    } finally {
      setIsDiscarding(false)
    }
  }

  return (
    <div className="card mt-6">
      <h2 className="section-title">Screening Rubric</h2>

      {versions === null && !loadError && <LoadingView />}
      {loadError && <ErrorView message={loadError} onRetry={load} />}

      {versions !== null && !loadError && (
        <div>
          {actionError && (
            <div className="banner banner-error mb-4" role="alert">
              {actionError}
            </div>
          )}

          {!current && (
            <div>
              <p className="text-secondary mb-4">No screening rubric has been configured for this internship yet.</p>
              <form onSubmit={handleCreateDraft} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                <div className="field">
                  <label htmlFor="rubric-seed-profile">Start from a screening profile (optional)</label>
                  <select
                    id="rubric-seed-profile"
                    value={selectedProfileId}
                    onChange={(event) => setSelectedProfileId(event.target.value)}
                  >
                    <option value="">Start empty</option>
                    {profiles.map((profile) => (
                      <option key={profile.id} value={profile.id}>
                        {profile.name}
                      </option>
                    ))}
                  </select>
                  <span className="field-hint">
                    Copies that profile's requirements into the new draft — editing the draft afterward never changes
                    the source profile.
                  </span>
                </div>
                <div>
                  <button type="submit" className="btn btn-primary" disabled={isCreatingDraft}>
                    {isCreatingDraft ? 'Creating…' : 'Create draft rubric'}
                  </button>
                </div>
              </form>
            </div>
          )}

          {current && (
            <div>
              <div className="row-meta mb-4">
                <span className={`badge ${RUBRIC_STATUS_BADGE[current.status]}`}>{RUBRIC_STATUS_LABELS[current.status]}</span>
                <span className="text-tertiary">Version {current.version}</span>
                {current.status === 'ACTIVE' && current.activatedAt && (
                  <span className="text-tertiary">Activated {formatDisplayDate(current.activatedAt)}</span>
                )}
              </div>

              {current.status === 'DRAFT' && (
                <div className="banner banner-warning mb-4" role="status">
                  This draft is editable. Activating it will archive the current active version (if any) — past
                  screening reports always keep referencing the exact rubric version they were produced against and
                  are never rewritten.
                </div>
              )}

              {current.status === 'ACTIVE' && (
                <div className="banner banner-warning mb-4" role="status">
                  This is the live, frozen rubric used for screening. To change it, duplicate it into a new draft
                  below — it cannot be edited in place.
                </div>
              )}

              <ScreeningRequirementsEditor
                requirements={current.requirements}
                readOnly={current.status !== 'DRAFT'}
                skillCatalog={skillCatalog}
                onAdd={async (payload) => {
                  await addRubricRequirement(internshipId, payload)
                  load()
                }}
                onUpdate={async (requirementId, payload) => {
                  await updateRubricRequirement(internshipId, requirementId, payload)
                  load()
                }}
                onDelete={async (requirementId) => {
                  await deleteRubricRequirement(internshipId, requirementId)
                  load()
                }}
              />

              {current.status === 'DRAFT' && (
                <AiRubricDraftPanel
                  internshipId={internshipId}
                  nextDisplayOrder={current.requirements.length}
                  onAddRequirement={async (payload) => {
                    await addRubricRequirement(internshipId, payload)
                    load()
                  }}
                />
              )}

              <div className="row-actions mt-4">
                {current.status === 'DRAFT' && (
                  <>
                    <button
                      type="button"
                      className="btn btn-primary"
                      onClick={handleActivate}
                      disabled={isActivating || current.requirements.length === 0}
                    >
                      {isActivating ? 'Activating…' : 'Activate rubric'}
                    </button>
                    <button type="button" className="btn btn-danger" onClick={handleDiscard} disabled={isDiscarding}>
                      {isDiscarding ? 'Discarding…' : 'Discard draft'}
                    </button>
                  </>
                )}
                {current.status === 'ACTIVE' && (
                  <button type="button" className="btn btn-secondary" onClick={handleDuplicate} disabled={isDuplicating}>
                    {isDuplicating ? 'Starting…' : 'Duplicate into new draft'}
                  </button>
                )}
              </div>
            </div>
          )}

          {history.length > 0 && (
            <div className="mt-6">
              <button type="button" className="btn btn-secondary btn-sm" onClick={() => setShowHistory((open) => !open)}>
                {showHistory ? 'Hide' : 'Show'} version history ({history.length})
              </button>

              {showHistory && (
                <div className="row-list mt-4">
                  {history.map((version) => (
                    <div className="card" key={version.id}>
                      <div className="row-meta mb-2">
                        <span className={`badge ${RUBRIC_STATUS_BADGE[version.status]}`}>
                          {RUBRIC_STATUS_LABELS[version.status]}
                        </span>
                        <span className="text-tertiary">Version {version.version}</span>
                      </div>
                      <ScreeningRequirementsEditor
                        requirements={version.requirements}
                        readOnly
                        skillCatalog={skillCatalog}
                        onAdd={noopRequirementAction}
                        onUpdate={noopRequirementAction}
                        onDelete={noopRequirementAction}
                      />
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

const OUTCOME_LABELS: Record<ScreeningOutcome, string> = {
  MATCHED: 'Matched',
  NOT_EVIDENCED: 'Evidence not found',
  INSUFFICIENT_EVIDENCE: 'Insufficient evidence',
  NEEDS_REVIEW: 'Manual review needed',
}

const OUTCOME_BADGE_CLASS: Record<ScreeningOutcome, string> = {
  MATCHED: 'badge-success',
  NOT_EVIDENCED: 'badge-muted',
  INSUFFICIENT_EVIDENCE: 'badge-amber',
  NEEDS_REVIEW: 'badge-amber',
}

const APPLICATION_STATUS_LABELS: Record<ApplicationStatus, string> = {
  APPLIED: 'Applied',
  ACCEPTED: 'Accepted',
  REJECTED: 'Rejected',
}

const APPLICATION_STATUS_BADGE: Record<ApplicationStatus, string> = {
  APPLIED: 'badge-neutral',
  ACCEPTED: 'badge-success',
  REJECTED: 'badge-error',
}

const SCREENING_PAGE_SIZE = 10

/**
 * Full detail for one ScreeningReport: coverage figures (kept visually secondary — never
 * framed as a probability of hiring success, per the backend's own documented intent for
 * weightedCoveragePercentage), the rubric version and evaluation timestamp it was produced
 * against, and every requirement's outcome with its evidence source/strength, supporting CV
 * excerpt (already authorized and length-capped server-side), and reasoning.
 */
function ViewCvButton({ internshipId, applicationId }: { internshipId: string; applicationId: string }) {
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleView = async () => {
    setError(null)
    setIsLoading(true)
    try {
      const blob = await downloadApplicationCv(internshipId, applicationId)
      const url = URL.createObjectURL(blob)
      window.open(url, '_blank', 'noopener,noreferrer')
      // Revoke once the new tab has had a moment to load it — never hold the object URL (and
      // the CV bytes behind it) in memory longer than necessary.
      setTimeout(() => URL.revokeObjectURL(url), 60_000)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to open this CV.')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div>
      <button type="button" className="btn btn-secondary btn-sm" onClick={handleView} disabled={isLoading}>
        <AppIcon name="file" size={14} /> {isLoading ? 'Opening…' : 'View original CV'}
      </button>
      {error && (
        <div className="banner banner-error mt-2" role="alert">
          {error}
        </div>
      )}
    </div>
  )
}

function ReportDetailPanel({
  report,
  internshipId,
  applicationId,
}: {
  report: ScreeningReport
  internshipId: string
  applicationId: string
}) {
  return (
    <div className="card mt-2">
      <div className="row-meta mb-4">
        <span className="text-tertiary">Rubric version {report.rubricVersion}</span>
        <span className="text-tertiary">Evaluated {formatDisplayDate(report.evaluatedAt)}</span>
        <span className="text-tertiary">Algorithm {report.algorithmVersion}</span>
        <ViewCvButton internshipId={internshipId} applicationId={applicationId} />
      </div>

      {report.cvTextUnavailable && (
        <div className="banner banner-warning mb-4" role="status">
          This candidate's CV text could not be read. Requirements below marked "Manual review needed" cannot be
          confirmed as missing evidence — please review the CV directly before drawing a conclusion.
        </div>
      )}

      <div className="row-meta mb-4">
        <span className="badge badge-neutral">
          Required {report.matchedRequiredCount}/{report.totalRequiredCount}
        </span>
        <span className="badge badge-muted">
          Preferred {report.matchedPreferredCount}/{report.totalPreferredCount}
        </span>
        <span className="badge badge-muted">
          Bonus {report.matchedBonusCount}/{report.totalBonusCount}
        </span>
      </div>

      <div className="field mb-4">
        <label>Weighted coverage (secondary — not a prediction of hiring success)</label>
        <p>
          <strong>{report.weightedCoveragePercentage}%</strong>
          <span className="field-hint" style={{ display: 'block' }}>
            A transparent, deterministic ratio of matched requirement weight across REQUIRED, PREFERRED, and BONUS
            requirements together. It is never a probability, score, or basis for automatic rejection.
          </span>
        </p>
      </div>

      <div className="row-list">
        {report.results.map((result) => (
          <div className="card row-item" key={result.requirementId}>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div className="row-meta">
                <span className={`badge ${OUTCOME_BADGE_CLASS[result.result]}`}>{OUTCOME_LABELS[result.result]}</span>
                <strong>{result.skillName ?? result.label ?? 'Requirement'}</strong>
                <span className="text-tertiary">Weight {result.weight}</span>
              </div>
              {(result.evidenceSource || result.evidenceStrength) && (
                <p className="text-tertiary mt-2">
                  {result.evidenceSource && `Source: ${result.evidenceSource.replace('_', ' ')}`}
                  {result.evidenceSource && result.evidenceStrength && ' · '}
                  {result.evidenceStrength && `Strength: ${result.evidenceStrength}`}
                </p>
              )}
              {result.textReference && (
                <blockquote
                  className="text-secondary mt-2"
                  style={{ fontStyle: 'italic', borderLeft: '3px solid #dce6df', paddingLeft: 10, margin: 0 }}
                >
                  “{result.textReference}”
                </blockquote>
              )}
              <p className="text-secondary mt-2">{result.reasoning}</p>
            </div>
          </div>
        ))}
      </div>

      <AiEvidenceAnalysisPanel internshipId={internshipId} applicationId={applicationId} report={report} />
    </div>
  )
}

function ApplicantRow({
  summary,
  internshipId,
  selected,
  selectionDisabled,
  onToggleSelect,
  onScreened,
}: {
  summary: ScreeningSummary
  internshipId: string
  selected: boolean
  selectionDisabled: boolean
  onToggleSelect: () => void
  onScreened: (report: ScreeningReport) => void
}) {
  const [isExpanded, setIsExpanded] = useState(false)
  const [report, setReport] = useState<ScreeningReport | null>(null)
  const [isLoadingReport, setIsLoadingReport] = useState(false)
  const [reportError, setReportError] = useState<string | null>(null)
  const [isScreening, setIsScreening] = useState(false)
  const [screenError, setScreenError] = useState<string | null>(null)

  const hasReport = summary.latestReportId !== null || report !== null

  const handleToggleView = async () => {
    if (isExpanded) {
      setIsExpanded(false)
      return
    }
    setIsExpanded(true)
    if (report) {
      return
    }
    setReportError(null)
    setIsLoadingReport(true)
    try {
      setReport(await getLatestScreeningReport(internshipId, summary.applicationId))
    } catch (err) {
      setReportError(describeError(err, 'Unable to load this screening report.'))
    } finally {
      setIsLoadingReport(false)
    }
  }

  const handleScreen = async () => {
    setScreenError(null)
    setIsScreening(true)
    try {
      const fresh = await screenApplicant(internshipId, summary.applicationId)
      setReport(fresh)
      setIsExpanded(true)
      onScreened(fresh)
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setScreenError('This internship has no active screening rubric yet — activate one above first.')
      } else {
        setScreenError(describeError(err, 'Unable to screen this applicant.'))
      }
    } finally {
      setIsScreening(false)
    }
  }

  return (
    <div>
      <div className="card row-item">
        <input
          type="checkbox"
          checked={selected}
          disabled={selectionDisabled && !selected}
          onChange={onToggleSelect}
          aria-label={`Select ${summary.applicantName ?? 'this applicant'} for batch screening`}
        />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="row-meta">
            <strong>{summary.applicantName ?? 'Unnamed applicant'}</strong>
            <span className={`badge ${APPLICATION_STATUS_BADGE[summary.applicationStatus]}`}>
              {APPLICATION_STATUS_LABELS[summary.applicationStatus]}
            </span>
            {!hasReport && <span className="badge badge-muted">Not yet screened</span>}
            {hasReport && summary.weightedCoveragePercentage !== null && (
              <>
                <span className="text-tertiary">Coverage {summary.weightedCoveragePercentage}%</span>
                <span className="text-tertiary">
                  Required {summary.matchedRequiredCount}/{summary.totalRequiredCount}
                </span>
                {summary.needsReview && <span className="badge badge-amber">Manual review needed</span>}
                <span className="text-tertiary">Screened {formatDisplayDate(summary.latestEvaluatedAt)}</span>
              </>
            )}
          </div>
        </div>
        <div className="row-actions">
          <button type="button" className="btn btn-secondary btn-sm" onClick={handleScreen} disabled={isScreening}>
            {isScreening ? 'Screening…' : hasReport ? 'Re-screen' : 'Screen applicant'}
          </button>
          {hasReport && (
            <button type="button" className="btn btn-secondary btn-sm" onClick={handleToggleView}>
              {isExpanded ? 'Hide report' : 'View report'}
            </button>
          )}
        </div>
      </div>

      {screenError && (
        <div className="banner banner-error mt-2" role="alert">
          {screenError}
        </div>
      )}

      {isExpanded && (
        <>
          {isLoadingReport && <LoadingView />}
          {reportError && <ErrorView message={reportError} onRetry={handleToggleView} />}
          {report && !isLoadingReport && !reportError && (
            <ReportDetailPanel report={report} internshipId={internshipId} applicationId={summary.applicationId} />
          )}
        </>
      )}
    </div>
  )
}

/**
 * The Applicant Screening section — Stage 4's explainable, deterministic screening, entirely
 * backed by the existing CompanyScreeningController endpoints (no new backend). Listing
 * applicants is a pure read of already-stored reports (getScreeningSummaries) and never
 * triggers screening; screening only ever happens from an explicit "Screen applicant"/"Screen
 * N selected" click. Nothing here reads or writes application status — that stays mobile-only
 * and entirely unaffected by anything on this page.
 */
function ApplicantScreeningSection({ internshipId }: { internshipId: string }) {
  const [page, setPage] = useState(0)
  const [summaries, setSummaries] = useState<ScreeningSummary[] | null>(null)
  const [totalElements, setTotalElements] = useState(0)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [isBatchScreening, setIsBatchScreening] = useState(false)
  const [batchError, setBatchError] = useState<string | null>(null)

  const load = useCallback(() => {
    setLoadError(null)
    getScreeningSummaries(internshipId, page, SCREENING_PAGE_SIZE)
      .then((result) => {
        setSummaries(result.content)
        setTotalElements(result.totalElements)
      })
      .catch((err: unknown) => setLoadError(describeError(err, 'Unable to load applicants.')))
  }, [internshipId, page])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    setSelectedIds(new Set())
  }, [page])

  const handleScreenedOne = (applicationId: string, report: ScreeningReport) => {
    setSummaries(
      (current) =>
        current?.map((summary) =>
          summary.applicationId === applicationId
            ? {
                ...summary,
                latestReportId: report.id,
                latestEvaluatedAt: report.evaluatedAt,
                needsReview: report.needsReview,
                weightedCoveragePercentage: report.weightedCoveragePercentage,
                matchedRequiredCount: report.matchedRequiredCount,
                totalRequiredCount: report.totalRequiredCount,
              }
            : summary,
        ) ?? null,
    )
  }

  const toggleSelect = (applicationId: string) => {
    setSelectedIds((current) => {
      const next = new Set(current)
      if (next.has(applicationId)) {
        next.delete(applicationId)
      } else if (next.size < MAX_SCREENING_BATCH_SIZE) {
        next.add(applicationId)
      }
      return next
    })
  }

  const handleBatchScreen = async () => {
    if (selectedIds.size === 0) {
      return
    }
    if (!window.confirm(`Screen ${selectedIds.size} selected applicant${selectedIds.size === 1 ? '' : 's'} now?`)) {
      return
    }
    setBatchError(null)
    setIsBatchScreening(true)
    try {
      const reports = await screenApplicantsBatch(internshipId, Array.from(selectedIds))
      reports.forEach((report) => handleScreenedOne(report.applicationId, report))
      setSelectedIds(new Set())
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setBatchError('This internship has no active screening rubric yet — activate one above first.')
      } else {
        setBatchError(describeError(err, 'Unable to screen the selected applicants.'))
      }
    } finally {
      setIsBatchScreening(false)
    }
  }

  const hasNextPage = (page + 1) * SCREENING_PAGE_SIZE < totalElements
  const hasPrevPage = page > 0

  return (
    <div className="card mt-6">
      <h2 className="section-title">Applicant Screening</h2>

      {summaries === null && !loadError && <LoadingView />}
      {loadError && <ErrorView message={loadError} onRetry={load} />}

      {summaries !== null && !loadError && (
        <div>
          {summaries.length === 0 && page === 0 ? (
            <EmptyView
              title="No applicants yet"
              body="Applicants will appear here once someone applies to this internship."
            />
          ) : (
            <>
              <div className="list-page-toolbar">
                <span className="text-tertiary">
                  {selectedIds.size > 0
                    ? `${selectedIds.size} selected (max ${MAX_SCREENING_BATCH_SIZE} per batch)`
                    : `${totalElements} applicant${totalElements === 1 ? '' : 's'}`}
                </span>
                <button
                  type="button"
                  className="btn btn-primary btn-sm"
                  onClick={handleBatchScreen}
                  disabled={selectedIds.size === 0 || isBatchScreening}
                >
                  <AppIcon name="refresh" size={14} />
                  {isBatchScreening ? 'Screening…' : `Screen ${selectedIds.size || ''} selected`.trim()}
                </button>
              </div>

              {batchError && (
                <div className="banner banner-error mb-4" role="alert">
                  {batchError}
                </div>
              )}

              <div className="row-list">
                {summaries.map((summary) => (
                  <ApplicantRow
                    key={summary.applicationId}
                    summary={summary}
                    internshipId={internshipId}
                    selected={selectedIds.has(summary.applicationId)}
                    selectionDisabled={selectedIds.size >= MAX_SCREENING_BATCH_SIZE}
                    onToggleSelect={() => toggleSelect(summary.applicationId)}
                    onScreened={(report) => handleScreenedOne(summary.applicationId, report)}
                  />
                ))}
              </div>

              <div className="row-actions mt-4">
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={() => setPage((current) => current - 1)}
                  disabled={!hasPrevPage}
                >
                  ← Previous
                </button>
                <span className="text-tertiary">Page {page + 1}</span>
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={() => setPage((current) => current + 1)}
                  disabled={!hasNextPage}
                >
                  Next →
                </button>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  )
}

/**
 * Internship detail page. Shows the internship's existing fields (reusing the existing
 * GET /company/internships/{id} endpoint — no new backend) plus the Recruitment Pipeline,
 * Screening Rubric, and Applicant Screening sections below. Creating/editing the internship
 * itself stays mobile-first, matching the internship list page's existing scope.
 */
export function InternshipDetailPage() {
  const { internshipId } = useParams<{ internshipId: string }>()
  const navigate = useNavigate()

  const [internship, setInternship] = useState<CompanyInternship | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [closeError, setCloseError] = useState<string | null>(null)
  const [isClosing, setIsClosing] = useState(false)

  const load = useCallback(() => {
    if (!internshipId) {
      return
    }
    setError(null)
    getCompanyInternship(internshipId)
      .then(setInternship)
      .catch((err: unknown) => {
        setError(describeError(err, 'Unable to load this internship.'))
      })
  }, [internshipId])

  const handleClose = async () => {
    if (!internshipId || isClosing) {
      return
    }
    if (!window.confirm('Close this internship? Interns will no longer be able to see or apply to it.')) {
      return
    }
    setCloseError(null)
    setIsClosing(true)
    try {
      const updated = await closeCompanyInternship(internshipId)
      setInternship(updated)
    } catch (err) {
      setCloseError(describeError(err, 'Unable to close this internship.'))
    } finally {
      setIsClosing(false)
    }
  }

  useEffect(() => {
    load()
  }, [load])

  if (!internshipId) {
    return <ErrorView message="No internship selected." onRetry={() => navigate('/internships')} />
  }

  if (error) {
    return <ErrorView message={error} onRetry={load} />
  }

  if (!internship) {
    return <LoadingView />
  }

  return (
    <div>
      <button type="button" className="back-link" onClick={() => navigate('/internships')}>
        ← Back to internships
      </button>

      <div className="page-header">
        <div>
          <h1>{internship.title}</h1>
          <div className="row-meta mt-2">
            <span className={`badge ${internship.status === 'OPEN' ? 'badge-success' : 'badge-muted'}`}>
              {internship.status === 'OPEN' ? 'Open' : 'Closed'}
            </span>
            <span>{internship.location} · {WORK_MODE_LABELS[internship.workMode]}</span>
            <span>Deadline {formatDisplayDate(internship.applicationDeadline)}</span>
          </div>
        </div>

        <div className="row-actions">
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => navigate(`/internships/${internship.id}/edit`)}
          >
            Edit
          </button>
          {internship.applicationMethod === 'INTERNAL' && (
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => navigate(`/internships/${internship.id}/application-questions`)}
            >
              Application Questions
            </button>
          )}
          {internship.status === 'OPEN' && (
            <button type="button" className="btn btn-danger" onClick={handleClose} disabled={isClosing}>
              {isClosing ? 'Closing…' : 'Close internship'}
            </button>
          )}
        </div>
      </div>

      {closeError && (
        <div className="banner banner-error mb-4" role="alert">
          {closeError}
        </div>
      )}

      <div className="card">
        <h2 className="section-title">Description</h2>
        <p>{internship.description}</p>

        {internship.skills.length > 0 && (
          <>
            <h2 className="section-title mt-4">Required skills</h2>
            <div className="row-meta">
              {internship.skills.map((skill) => (
                <span key={skill.id} className="badge badge-neutral">
                  {skill.name}
                </span>
              ))}
            </div>
          </>
        )}
      </div>

      <RecruitmentPipelineSection internshipId={internship.id} />
      <RubricSection internshipId={internship.id} />
      <ApplicantScreeningSection internshipId={internship.id} />
    </div>
  )
}
