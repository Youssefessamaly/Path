import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ErrorView, LoadingView } from '../../components/StateViews'
import { useAuth } from '../../auth/useAuth'
import {
  ApiError,
  addPipelineStage,
  assignInternshipPipeline,
  closeCompanyInternship,
  deletePipelineStage,
  getCompanyInternship,
  getInternshipPipeline,
  getPipelineTemplates,
  renamePipelineStage,
  reorderPipelineStages,
  type CompanyInternship,
  type InternshipPipeline,
  type PipelineStage,
  type PipelineTemplate,
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

/**
 * Internship detail page. Shows the internship's existing fields (reusing the existing
 * GET /company/internships/{id} endpoint — no new backend) plus the new Recruitment Pipeline
 * section below. Creating/editing the internship itself stays mobile-first, matching the
 * internship list page's existing scope.
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
    </div>
  )
}
