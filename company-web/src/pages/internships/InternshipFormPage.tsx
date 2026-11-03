import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ErrorView, LoadingView } from '../../components/StateViews'
import {
  createCompanyInternship,
  getCompanyInternship,
  getSkillCatalog,
  updateCompanyInternship,
  type ApplicationMethod,
  type CompanyInternship,
  type InternshipSkill,
  type InternshipWorkMode,
} from '../../lib/api'

const WORK_MODE_OPTIONS: { label: string; value: InternshipWorkMode }[] = [
  { label: 'On-site', value: 'ONSITE' },
  { label: 'Hybrid', value: 'HYBRID' },
  { label: 'Remote', value: 'REMOTE' },
]

/** Mirrors mobile's MINIMUM_ACADEMIC_YEAR_OPTIONS exactly (lib/internship-options.ts) —
 *  'NONE' is a UI-only sentinel for "no restriction" and never reaches the API. */
const MINIMUM_ACADEMIC_YEAR_OPTIONS: { label: string; value: string }[] = [
  { label: 'No restriction', value: 'NONE' },
  { label: 'Year 1+', value: '1' },
  { label: 'Year 2+', value: '2' },
  { label: 'Year 3+', value: '3' },
  { label: 'Year 4+', value: '4' },
  { label: 'Year 5+', value: '5' },
  { label: 'Year 6', value: '6' },
]

const MAX_TARGET_MAJORS = 10

/** Same basic http(s) shape check mobile uses (isValidExternalApplicationUrl in
 *  lib/internship-options.ts) — the backend remains the authoritative validator. */
function isValidExternalApplicationUrl(url: string): boolean {
  return /^https?:\/\/[^\s]+\.[^\s]+$/i.test(url.trim())
}

function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10)
}

/** Mirrors mobile's minimumAcademicYearToOptionValue (lib/internship-options.ts): maps the
 *  backend's nullable Integer onto this form's 'NONE' sentinel option. */
function minimumAcademicYearToOptionValue(value: number | null): string {
  return value === null ? 'NONE' : String(value)
}

function describeError(err: unknown, fallback: string): string {
  return err instanceof Error ? err.message : fallback
}

/**
 * Create and edit share this one form — the same fields, the same validation, the same
 * backend endpoints (POST /company/internships, PATCH /company/internships/{id}) the mobile
 * app's new.tsx / edit.tsx wizards already use. Desktop gets a single scrollable form instead
 * of mobile's forced multi-step wizard; every field and rule is otherwise identical.
 */
export function InternshipFormPage({ mode }: { mode: 'create' | 'edit' }) {
  const { internshipId } = useParams<{ internshipId: string }>()
  const navigate = useNavigate()

  const [isLoading, setIsLoading] = useState(mode === 'edit')
  const [loadError, setLoadError] = useState<string | null>(null)

  const [catalog, setCatalog] = useState<InternshipSkill[]>([])

  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [location, setLocation] = useState('')
  const [workMode, setWorkMode] = useState<InternshipWorkMode | ''>('')
  const [applicationDeadline, setApplicationDeadline] = useState('')
  const [startDate, setStartDate] = useState('')
  const [durationMonths, setDurationMonths] = useState('')
  const [selectedSkillIds, setSelectedSkillIds] = useState<string[]>([])
  const [targetMajors, setTargetMajors] = useState<string[]>([])
  const [majorDraft, setMajorDraft] = useState('')
  const [minimumAcademicYear, setMinimumAcademicYear] = useState('NONE')
  const [applicationMethod, setApplicationMethod] = useState<ApplicationMethod | ''>('')
  const [externalUrl, setExternalUrl] = useState('')

  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const load = useCallback(() => {
    setLoadError(null)
    const catalogPromise = getSkillCatalog().catch(() => [] as InternshipSkill[])

    if (mode === 'create') {
      catalogPromise.then(setCatalog)
      return
    }

    if (!internshipId) {
      return
    }

    setIsLoading(true)
    Promise.all([getCompanyInternship(internshipId), catalogPromise])
      .then(([internship, skills]: [CompanyInternship, InternshipSkill[]]) => {
        setCatalog(skills)
        setTitle(internship.title)
        setDescription(internship.description)
        setLocation(internship.location)
        setWorkMode(internship.workMode)
        setApplicationDeadline(internship.applicationDeadline)
        setStartDate(internship.startDate ?? '')
        setDurationMonths(internship.durationMonths ? String(internship.durationMonths) : '')
        setSelectedSkillIds(internship.skills.map((skill) => skill.id))
        setTargetMajors(internship.targetMajors)
        setMinimumAcademicYear(minimumAcademicYearToOptionValue(internship.minimumAcademicYear))
        setApplicationMethod(internship.applicationMethod)
        setExternalUrl(internship.externalApplicationUrl ?? '')
      })
      .catch((err: unknown) => setLoadError(describeError(err, 'Unable to load this internship.')))
      .finally(() => setIsLoading(false))
  }, [mode, internshipId])

  useEffect(() => {
    load()
  }, [load])

  const toggleSkill = (skillId: string) => {
    setSelectedSkillIds((current) =>
      current.includes(skillId) ? current.filter((id) => id !== skillId) : [...current, skillId],
    )
  }

  const addMajor = () => {
    const trimmed = majorDraft.trim()
    if (!trimmed || targetMajors.length >= MAX_TARGET_MAJORS) {
      return
    }
    if (!targetMajors.some((major) => major.toLowerCase() === trimmed.toLowerCase())) {
      setTargetMajors((current) => [...current, trimmed])
    }
    setMajorDraft('')
  }

  const removeMajor = (major: string) => {
    setTargetMajors((current) => current.filter((item) => item !== major))
  }

  const handleApplicationMethodChange = (method: ApplicationMethod) => {
    setApplicationMethod(method)
    if (method === 'INTERNAL') {
      setExternalUrl('')
    }
  }

  const canSubmit =
    title.trim().length > 0 &&
    description.trim().length > 0 &&
    location.trim().length > 0 &&
    workMode !== '' &&
    applicationDeadline.length > 0 &&
    selectedSkillIds.length > 0 &&
    applicationMethod !== '' &&
    (applicationMethod === 'INTERNAL' || isValidExternalApplicationUrl(externalUrl)) &&
    !isSubmitting

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!canSubmit || !workMode || !applicationMethod) {
      return
    }

    setSubmitError(null)
    setIsSubmitting(true)

    try {
      if (mode === 'create') {
        const created = await createCompanyInternship({
          title: title.trim(),
          description: description.trim(),
          location: location.trim(),
          workMode,
          applicationDeadline,
          ...(startDate ? { startDate } : {}),
          ...(durationMonths ? { durationMonths: Number(durationMonths) } : {}),
          skillIds: selectedSkillIds,
          targetMajors,
          ...(minimumAcademicYear !== 'NONE' ? { minimumAcademicYear: Number(minimumAcademicYear) } : {}),
          applicationMethod,
          ...(applicationMethod === 'EXTERNAL' ? { externalApplicationUrl: externalUrl.trim() } : {}),
        })
        navigate(`/internships/${created.id}`)
        return
      }

      if (!internshipId) {
        return
      }

      await updateCompanyInternship(internshipId, {
        title: title.trim(),
        description: description.trim(),
        location: location.trim(),
        workMode,
        applicationDeadline,
        ...(startDate ? { startDate } : {}),
        ...(durationMonths ? { durationMonths: Number(durationMonths) } : {}),
        skillIds: selectedSkillIds,
        targetMajors,
        // Always sent explicitly (never omitted) — selecting "No restriction" must clear a
        // previously-set value, and only an explicit JSON null does that (see api.ts).
        minimumAcademicYear: minimumAcademicYear === 'NONE' ? null : Number(minimumAcademicYear),
        applicationMethod,
        // Switching to INTERNAL omits this entirely — the backend clears the stored URL
        // itself once applicationMethod resolves to INTERNAL.
        ...(applicationMethod === 'EXTERNAL' ? { externalApplicationUrl: externalUrl.trim() } : {}),
      })
      navigate(`/internships/${internshipId}`)
    } catch (err) {
      setSubmitError(describeError(err, `Unable to ${mode === 'create' ? 'create' : 'update'} this internship.`))
    } finally {
      setIsSubmitting(false)
    }
  }

  if (mode === 'edit' && loadError) {
    return <ErrorView message={loadError} onRetry={load} />
  }

  if (mode === 'edit' && isLoading) {
    return <LoadingView />
  }

  return (
    <div>
      <button
        type="button"
        className="back-link"
        onClick={() => navigate(mode === 'edit' && internshipId ? `/internships/${internshipId}` : '/internships')}
      >
        ← Back
      </button>

      <div className="page-header">
        <div>
          <h1>{mode === 'create' ? 'Post an internship' : 'Edit internship'}</h1>
          <p className="page-header-subtitle">
            {mode === 'create'
              ? 'Share a role and start receiving applications.'
              : 'Update the details interns see for this internship.'}
          </p>
        </div>
      </div>

      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
        <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <h2 className="section-title">The opportunity</h2>

          <div className="field">
            <label htmlFor="title">Title</label>
            <input
              id="title"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              placeholder="Backend Engineering Intern"
              maxLength={200}
              required
            />
          </div>

          <div className="field">
            <label htmlFor="description">Description</label>
            <textarea
              id="description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="What will the intern work on?"
              maxLength={5000}
              required
              style={{ minHeight: 140 }}
            />
          </div>

          <div className="field">
            <label htmlFor="location">Location</label>
            <input
              id="location"
              value={location}
              onChange={(event) => setLocation(event.target.value)}
              placeholder="Cairo, Egypt"
              maxLength={200}
              required
            />
          </div>
        </div>

        <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <h2 className="section-title">Schedule &amp; format</h2>

          <div className="field">
            <label>Work mode</label>
            <div className="row-actions">
              {WORK_MODE_OPTIONS.map((option) => (
                <label key={option.value} className="checkbox-field">
                  <input
                    type="radio"
                    name="workMode"
                    value={option.value}
                    checked={workMode === option.value}
                    onChange={() => setWorkMode(option.value)}
                  />
                  {option.label}
                </label>
              ))}
            </div>
          </div>

          <div className="field">
            <label htmlFor="applicationDeadline">Application deadline</label>
            <input
              id="applicationDeadline"
              type="date"
              value={applicationDeadline}
              onChange={(event) => setApplicationDeadline(event.target.value)}
              min={mode === 'create' ? todayIsoDate() : undefined}
              required
            />
          </div>

          <div className="field">
            <label htmlFor="startDate">Start date (optional)</label>
            <input
              id="startDate"
              type="date"
              value={startDate}
              onChange={(event) => setStartDate(event.target.value)}
            />
          </div>

          <div className="field">
            <label htmlFor="durationMonths">Duration in months (optional)</label>
            <input
              id="durationMonths"
              type="number"
              min={1}
              max={24}
              value={durationMonths}
              onChange={(event) => setDurationMonths(event.target.value.replace(/\D/g, '').slice(0, 2))}
              placeholder="3"
            />
          </div>
        </div>

        <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <h2 className="section-title">Candidate requirements</h2>

          <div className="field">
            <label>Required skills</label>
            <div className="filter-tabs" style={{ flexWrap: 'wrap' }}>
              {catalog.map((skill) => (
                <button
                  key={skill.id}
                  type="button"
                  className={`filter-tab${selectedSkillIds.includes(skill.id) ? ' active' : ''}`}
                  onClick={() => toggleSkill(skill.id)}
                >
                  {skill.name}
                </button>
              ))}
            </div>
            <span className="field-hint">Select at least one required skill.</span>
          </div>

          <div className="field">
            <label htmlFor="major-draft">Target majors (optional)</label>
            <div className="add-stage-form">
              <input
                id="major-draft"
                value={majorDraft}
                onChange={(event) => setMajorDraft(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    event.preventDefault()
                    addMajor()
                  }
                }}
                placeholder="e.g. Computer Science"
                maxLength={150}
                disabled={targetMajors.length >= MAX_TARGET_MAJORS}
              />
              <button
                type="button"
                className="btn btn-secondary"
                onClick={addMajor}
                disabled={!majorDraft.trim() || targetMajors.length >= MAX_TARGET_MAJORS}
              >
                Add
              </button>
            </div>
            {targetMajors.length > 0 && (
              <div className="row-meta mt-2">
                {targetMajors.map((major) => (
                  <span key={major} className="badge badge-neutral">
                    {major}{' '}
                    <button
                      type="button"
                      onClick={() => removeMajor(major)}
                      aria-label={`Remove ${major}`}
                      style={{
                        border: 'none',
                        background: 'none',
                        cursor: 'pointer',
                        marginLeft: 4,
                        color: 'inherit',
                        fontWeight: 700,
                      }}
                    >
                      ×
                    </button>
                  </span>
                ))}
              </div>
            )}
            <span className="field-hint">Up to {MAX_TARGET_MAJORS} target majors.</span>
          </div>

          <div className="field">
            <label htmlFor="minimumAcademicYear">Minimum academic year (optional)</label>
            <select
              id="minimumAcademicYear"
              value={minimumAcademicYear}
              onChange={(event) => setMinimumAcademicYear(event.target.value)}
            >
              {MINIMUM_ACADEMIC_YEAR_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <span className="field-hint">Only students in this university year or above will be eligible.</span>
          </div>
        </div>

        <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <h2 className="section-title">Application method</h2>

          <div className="field">
            <label className="checkbox-field">
              <input
                type="radio"
                name="applicationMethod"
                checked={applicationMethod === 'INTERNAL'}
                onChange={() => handleApplicationMethodChange('INTERNAL')}
              />
              Apply through Pathora
            </label>
            <label className="checkbox-field">
              <input
                type="radio"
                name="applicationMethod"
                checked={applicationMethod === 'EXTERNAL'}
                onChange={() => handleApplicationMethodChange('EXTERNAL')}
              />
              Apply on company website
            </label>
          </div>

          {applicationMethod === 'EXTERNAL' && (
            <div className="field">
              <label htmlFor="externalUrl">Application website URL</label>
              <input
                id="externalUrl"
                type="url"
                value={externalUrl}
                onChange={(event) => setExternalUrl(event.target.value)}
                placeholder="https://yourcompany.com/careers/role"
              />
            </div>
          )}
        </div>

        {submitError && (
          <div className="banner banner-error" role="alert">
            {submitError}
          </div>
        )}

        <div>
          <button type="submit" className="btn btn-primary" disabled={!canSubmit}>
            {isSubmitting
              ? mode === 'create'
                ? 'Posting…'
                : 'Saving…'
              : mode === 'create'
                ? 'Post internship'
                : 'Save changes'}
          </button>
        </div>
      </form>
    </div>
  )
}
