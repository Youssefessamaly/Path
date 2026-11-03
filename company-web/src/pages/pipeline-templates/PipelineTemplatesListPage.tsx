import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { EmptyView, ErrorView, LoadingView } from '../../components/StateViews'
import { useAuth } from '../../auth/useAuth'
import {
  ApiError,
  createPipelineTemplate,
  duplicatePipelineTemplate,
  getPipelineTemplates,
  type PipelineTemplate,
} from '../../lib/api'

function TemplateRow({
  template,
  isOwner,
  onOpen,
  onDuplicate,
}: {
  template: PipelineTemplate
  isOwner: boolean
  onOpen: () => void
  onDuplicate: () => void
}) {
  return (
    <div className="card row-item clickable" onClick={onOpen} role="button" tabIndex={0}>
      <div className="row-main">
        <strong>{template.name}</strong>
        {template.description && <span className="text-secondary">{template.description}</span>}
      </div>

      <div className="row-meta">
        <span>
          {template.stages.length} stage{template.stages.length === 1 ? '' : 's'}
        </span>
        {template.isDefault && <span className="badge badge-neutral">Default</span>}
        {template.archived && <span className="badge badge-muted">Archived</span>}
      </div>

      {isOwner && (
        <div className="row-actions">
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={(event) => {
              event.stopPropagation()
              onDuplicate()
            }}
          >
            Duplicate
          </button>
        </div>
      )}
    </div>
  )
}

/** Lists this company's pipeline templates. Creating, renaming, duplicating, archiving, and
 *  configuring stages is OWNER-only on the backend (CompanyAccessResolver.requireOwner) — a
 *  recruiter sees the same list read-only, with no create/duplicate affordances. */
export function PipelineTemplatesListPage() {
  const navigate = useNavigate()
  const { membership } = useAuth()
  const isOwner = membership?.role === 'OWNER'

  const [templates, setTemplates] = useState<PipelineTemplate[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [includeArchived, setIncludeArchived] = useState(false)

  const [newName, setNewName] = useState('')
  const [isCreating, setIsCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  const load = useCallback(() => {
    setTemplates(null)
    setError(null)
    getPipelineTemplates(includeArchived)
      .then(setTemplates)
      .catch((loadError: unknown) => {
        setError(loadError instanceof ApiError ? loadError.message : 'Unable to load pipeline templates.')
      })
  }, [includeArchived])

  useEffect(() => {
    load()
  }, [load])

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault()
    const trimmed = newName.trim()
    if (!trimmed || isCreating) {
      return
    }

    setCreateError(null)
    setIsCreating(true)
    try {
      const created = await createPipelineTemplate({ name: trimmed })
      navigate(`/pipeline-templates/${created.id}`)
    } catch (err) {
      setCreateError(err instanceof Error ? err.message : 'Unable to create the template.')
    } finally {
      setIsCreating(false)
    }
  }

  const handleDuplicate = async (templateId: string) => {
    try {
      const duplicate = await duplicatePipelineTemplate(templateId)
      navigate(`/pipeline-templates/${duplicate.id}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to duplicate the template.')
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Pipeline Templates</h1>
          <p className="page-header-subtitle">
            Reusable recruitment pipelines your company can assign to internships.
          </p>
        </div>
      </div>

      {isOwner && (
        <div className="card mb-4">
          <h2 className="section-title">New template</h2>
          <form className="add-stage-form" onSubmit={handleCreate}>
            <input
              type="text"
              placeholder="e.g. Engineering Internship Pipeline"
              value={newName}
              onChange={(event) => setNewName(event.target.value)}
              disabled={isCreating}
              aria-label="New template name"
              maxLength={120}
            />
            <button type="submit" className="btn btn-primary" disabled={!newName.trim() || isCreating}>
              {isCreating ? 'Creating…' : 'Create template'}
            </button>
          </form>
          {createError && (
            <div className="banner banner-error mt-2" role="alert">
              {createError}
            </div>
          )}
        </div>
      )}

      <div className="filter-tabs">
        <button
          type="button"
          className={`filter-tab${!includeArchived ? ' active' : ''}`}
          onClick={() => setIncludeArchived(false)}
        >
          Active
        </button>
        <button
          type="button"
          className={`filter-tab${includeArchived ? ' active' : ''}`}
          onClick={() => setIncludeArchived(true)}
        >
          All (including archived)
        </button>
      </div>

      {templates === null && !error && <LoadingView />}

      {error && <ErrorView message={error} onRetry={load} />}

      {templates !== null && !error && templates.length === 0 && (
        <EmptyView
          title="No pipeline templates yet"
          body={isOwner ? 'Create one above to get started.' : 'Ask your company owner to set one up.'}
        />
      )}

      {templates !== null && !error && templates.length > 0 && (
        <div className="row-list">
          {templates.map((template) => (
            <TemplateRow
              key={template.id}
              template={template}
              isOwner={isOwner}
              onOpen={() => navigate(`/pipeline-templates/${template.id}`)}
              onDuplicate={() => handleDuplicate(template.id)}
            />
          ))}
        </div>
      )}
    </div>
  )
}
