import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ErrorView, LoadingView } from '../../components/StateViews'
import { useAuth } from '../../auth/useAuth'
import {
  ApiError,
  addTemplateStage,
  archivePipelineTemplate,
  deleteTemplateStage,
  duplicatePipelineTemplate,
  getPipelineTemplate,
  renameTemplateStage,
  reorderTemplateStages,
  updatePipelineTemplate,
  type PipelineTemplate,
  type TemplateStage,
} from '../../lib/api'

function describeError(err: unknown, fallback: string): string {
  return err instanceof Error ? err.message : fallback
}

function StageRow({
  stage,
  index,
  total,
  isOwner,
  onRename,
  onDelete,
  onMoveUp,
  onMoveDown,
}: {
  stage: TemplateStage
  index: number
  total: number
  isOwner: boolean
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
          onClick={() => isOwner && setIsEditing(true)}
          role={isOwner ? 'button' : undefined}
          tabIndex={isOwner ? 0 : undefined}
        >
          {stage.name}
        </span>
      )}

      {isOwner && !isEditing && (
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
 * The pipeline builder: rename/describe a template, mark it as the company default, duplicate
 * or archive it, and configure its ordered stages (add, rename inline, delete, reorder via
 * up/down controls — no drag-and-drop library, per the "avoid unnecessary UI libraries"
 * guideline). Every action calls the real backend; there is no local-only/fake state. Archived
 * templates and non-owner viewers (recruiters) get a read-only rendering of the same page.
 */
export function PipelineTemplateEditorPage() {
  const { templateId } = useParams<{ templateId: string }>()
  const navigate = useNavigate()
  const { membership } = useAuth()
  const isOwner = membership?.role === 'OWNER'

  const [template, setTemplate] = useState<PipelineTemplate | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const [nameDraft, setNameDraft] = useState('')
  const [descriptionDraft, setDescriptionDraft] = useState('')
  const [isSavingDetails, setIsSavingDetails] = useState(false)

  const [newStageName, setNewStageName] = useState('')
  const [isAddingStage, setIsAddingStage] = useState(false)

  const load = useCallback(() => {
    if (!templateId) {
      return
    }
    setError(null)
    getPipelineTemplate(templateId)
      .then((loaded) => {
        setTemplate(loaded)
        setNameDraft(loaded.name)
        setDescriptionDraft(loaded.description ?? '')
      })
      .catch((loadError: unknown) => {
        setError(describeError(loadError, 'Unable to load this template.'))
      })
  }, [templateId])

  useEffect(() => {
    load()
  }, [load])

  if (!templateId) {
    return <ErrorView message="No template selected." onRetry={() => navigate('/pipeline-templates')} />
  }

  if (error) {
    return <ErrorView message={error} onRetry={load} />
  }

  if (!template) {
    return <LoadingView />
  }

  const canEdit = isOwner && !template.archived

  const saveDetails = async (event: FormEvent) => {
    event.preventDefault()
    const trimmedName = nameDraft.trim()
    if (!trimmedName) {
      return
    }
    setActionError(null)
    setIsSavingDetails(true)
    try {
      const updated = await updatePipelineTemplate(template.id, {
        name: trimmedName,
        description: descriptionDraft.trim(),
      })
      setTemplate(updated)
    } catch (err) {
      setActionError(describeError(err, 'Unable to save changes.'))
    } finally {
      setIsSavingDetails(false)
    }
  }

  const toggleDefault = async () => {
    setActionError(null)
    try {
      const updated = await updatePipelineTemplate(template.id, { isDefault: !template.isDefault })
      setTemplate(updated)
    } catch (err) {
      setActionError(describeError(err, 'Unable to update the default template.'))
    }
  }

  const handleArchive = async () => {
    setActionError(null)
    try {
      const updated = await archivePipelineTemplate(template.id)
      setTemplate(updated)
    } catch (err) {
      setActionError(describeError(err, 'Unable to archive this template.'))
    }
  }

  const handleDuplicate = async () => {
    setActionError(null)
    try {
      const duplicate = await duplicatePipelineTemplate(template.id)
      navigate(`/pipeline-templates/${duplicate.id}`)
    } catch (err) {
      setActionError(describeError(err, 'Unable to duplicate this template.'))
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
      await addTemplateStage(template.id, trimmed)
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
      await renameTemplateStage(template.id, stageId, name)
      load()
    } catch (err) {
      setActionError(describeError(err, 'Unable to rename the stage.'))
    }
  }

  const handleDeleteStage = async (stageId: string) => {
    setActionError(null)
    try {
      await deleteTemplateStage(template.id, stageId)
      load()
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setActionError('A template must have at least one stage — delete the whole template instead.')
      } else {
        setActionError(describeError(err, 'Unable to delete the stage.'))
      }
    }
  }

  const moveStage = async (fromIndex: number, toIndex: number) => {
    if (toIndex < 0 || toIndex >= template.stages.length) {
      return
    }
    const reordered = [...template.stages]
    const [moved] = reordered.splice(fromIndex, 1)
    reordered.splice(toIndex, 0, moved)

    setActionError(null)
    try {
      await reorderTemplateStages(template.id, reordered.map((stage) => stage.id))
      load()
    } catch (err) {
      setActionError(describeError(err, 'Unable to reorder stages.'))
    }
  }

  return (
    <div>
      <button type="button" className="back-link" onClick={() => navigate('/pipeline-templates')}>
        ← Back to pipeline templates
      </button>

      <div className="page-header">
        <div>
          <h1>{template.name}</h1>
          <div className="row-meta mt-2">
            {template.isDefault && <span className="badge badge-neutral">Default</span>}
            {template.archived && <span className="badge badge-muted">Archived</span>}
          </div>
        </div>

        {isOwner && (
          <div className="row-actions">
            <button type="button" className="btn btn-secondary" onClick={handleDuplicate}>
              Duplicate
            </button>
            {!template.archived && (
              <button type="button" className="btn btn-danger" onClick={handleArchive}>
                Archive
              </button>
            )}
          </div>
        )}
      </div>

      {template.archived && (
        <div className="banner banner-warning mb-4" role="status">
          This template is archived and read-only. It can no longer be assigned to new
          internships.
        </div>
      )}

      {actionError && (
        <div className="banner banner-error mb-4" role="alert">
          {actionError}
        </div>
      )}

      {canEdit && (
        <div className="card mb-4">
          <h2 className="section-title">Details</h2>
          <form onSubmit={saveDetails} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div className="field">
              <label htmlFor="template-name">Name</label>
              <input
                id="template-name"
                value={nameDraft}
                onChange={(event) => setNameDraft(event.target.value)}
                maxLength={120}
                required
              />
            </div>
            <div className="field">
              <label htmlFor="template-description">Description</label>
              <textarea
                id="template-description"
                value={descriptionDraft}
                onChange={(event) => setDescriptionDraft(event.target.value)}
                maxLength={2000}
                placeholder="Optional — what this pipeline is for"
              />
            </div>
            <div className="checkbox-field">
              <input
                id="template-default"
                type="checkbox"
                checked={template.isDefault}
                onChange={toggleDefault}
              />
              <label htmlFor="template-default">Use as this company's default pipeline</label>
            </div>
            <div>
              <button
                type="submit"
                className="btn btn-primary"
                disabled={isSavingDetails || !nameDraft.trim()}
              >
                {isSavingDetails ? 'Saving…' : 'Save details'}
              </button>
            </div>
          </form>
        </div>
      )}

      {!isOwner && template.description && (
        <div className="card mb-4">
          <p className="text-secondary">{template.description}</p>
        </div>
      )}

      <div className="card">
        <h2 className="section-title">Stages</h2>

        {template.stages.length === 0 ? (
          <p className="text-secondary">No stages configured yet.</p>
        ) : (
          <div className="stage-list">
            {template.stages.map((stage, index) => (
              <StageRow
                key={stage.id}
                stage={stage}
                index={index}
                total={template.stages.length}
                isOwner={canEdit}
                onRename={(name) => handleRenameStage(stage.id, name)}
                onDelete={() => handleDeleteStage(stage.id)}
                onMoveUp={() => moveStage(index, index - 1)}
                onMoveDown={() => moveStage(index, index + 1)}
              />
            ))}
          </div>
        )}

        {canEdit && (
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
    </div>
  )
}
