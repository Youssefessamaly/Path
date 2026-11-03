import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { EmptyView, ErrorView, LoadingView } from '../../components/StateViews'
import { AppIcon } from '../../components/Visuals'
import { useAuth } from '../../auth/useAuth'
import {
  ApiError,
  createPipelineTemplate,
  duplicatePipelineTemplate,
  getPipelineTemplates,
  type PipelineTemplate,
} from '../../lib/api'

function TemplateRow({ template, isOwner, onOpen, onDuplicate }: {
  template: PipelineTemplate
  isOwner: boolean
  onOpen: () => void
  onDuplicate: () => void
}) {
  return (
    <div className="card row-item template-row">
      <span className="list-card-icon"><AppIcon name="workflow" size={20} /></span>
      <button className="template-open" type="button" onClick={onOpen} aria-label={`Open ${template.name}`}>
        <strong>{template.name}</strong>
        <span>{template.description || 'Reusable recruitment workflow'}</span>
      </button>
      <div className="row-meta">
        <span>{template.stages.length} stage{template.stages.length === 1 ? '' : 's'}</span>
        {template.isDefault && <span className="badge badge-neutral">Default</span>}
        {template.archived && <span className="badge badge-muted">Archived</span>}
      </div>
      {isOwner && (
        <div className="row-actions">
          <button type="button" className="btn btn-secondary btn-sm" onClick={onDuplicate} aria-label={`Duplicate ${template.name}`}>
            <AppIcon name="copy" size={15} /> Duplicate
          </button>
        </div>
      )}
      <button type="button" className="template-row-arrow" onClick={onOpen} aria-label={`View ${template.name}`}><AppIcon name="chevron-right" size={19} /></button>
    </div>
  )
}

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

  useEffect(() => { load() }, [load])

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault()
    const trimmed = newName.trim()
    if (!trimmed || isCreating) return
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
          <span className="list-page-intro"><AppIcon name="workflow" size={15} /> YOUR RECRUITMENT PROCESS</span>
          <h1>Pipeline templates</h1>
          <p className="page-header-subtitle">Design repeatable hiring workflows and keep every internship organized.</p>
        </div>
      </div>

      {isOwner && (
        <div className="card mb-4 template-create-panel">
          <div className="template-create-intro"><span><AppIcon name="plus" size={19} /></span><div><strong>Create a new template</strong><p>Give your workflow a name, then customize its stages.</p></div></div>
          <form className="add-stage-form" onSubmit={handleCreate}>
            <input type="text" placeholder="e.g. Engineering Internship Pipeline" value={newName} onChange={(event) => setNewName(event.target.value)} disabled={isCreating} aria-label="New template name" maxLength={120} />
            <button type="submit" className="btn btn-primary" disabled={!newName.trim() || isCreating}><AppIcon name="plus" size={17} />{isCreating ? 'Creating…' : 'Create template'}</button>
          </form>
          {createError && <div className="banner banner-error mt-2" role="alert">{createError}</div>}
        </div>
      )}

      <div className="list-page-toolbar">
        <div className="filter-tabs" role="group" aria-label="Filter pipeline templates">
          <button type="button" className={`filter-tab${!includeArchived ? ' active' : ''}`} aria-pressed={!includeArchived} onClick={() => setIncludeArchived(false)}>Active</button>
          <button type="button" className={`filter-tab${includeArchived ? ' active' : ''}`} aria-pressed={includeArchived} onClick={() => setIncludeArchived(true)}>All (including archived)</button>
        </div>
        {templates && <span className="templates-list-count">{templates.length} template{templates.length === 1 ? '' : 's'}</span>}
      </div>

      {templates === null && !error && <LoadingView />}
      {error && <ErrorView message={error} onRetry={load} />}
      {templates !== null && !error && templates.length === 0 && <EmptyView title="No pipeline templates yet" body={isOwner ? 'Create one above to set up your hiring journey.' : 'Ask your company owner to set one up.'} />}
      {templates !== null && !error && templates.length > 0 && (
        <div className="row-list">
          {templates.map((template) => <TemplateRow key={template.id} template={template} isOwner={isOwner} onOpen={() => navigate(`/pipeline-templates/${template.id}`)} onDuplicate={() => handleDuplicate(template.id)} />)}
        </div>
      )}
    </div>
  )
}
