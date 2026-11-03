import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ErrorView, LoadingView } from '../../components/StateViews'
import {
  ApiError,
  createCompanyApplicationQuestion,
  deleteCompanyApplicationQuestion,
  getCompanyApplicationQuestions,
  getCompanyInternship,
  updateCompanyApplicationQuestion,
  type ApplicationQuestion,
  type ApplicationQuestionType,
  type CompanyInternship,
} from '../../lib/api'

const QUESTION_TYPE_OPTIONS: { label: string; value: ApplicationQuestionType }[] = [
  { label: 'Short answer', value: 'SHORT_TEXT' },
  { label: 'Long answer', value: 'LONG_TEXT' },
]

const QUESTION_TYPE_LABELS: Record<ApplicationQuestionType, string> = {
  SHORT_TEXT: 'Short answer',
  LONG_TEXT: 'Long answer',
}

const LOCKED_MESSAGE =
  'Application questions are locked because this internship has already received an application.'

function isLockConflict(err: unknown): boolean {
  return err instanceof ApiError && err.status === 409
}

function describeQuestionError(err: unknown, fallback: string): string {
  if (isLockConflict(err)) {
    return LOCKED_MESSAGE
  }
  return err instanceof Error ? err.message : fallback
}

function nextDisplayOrder(questions: ApplicationQuestion[]): number {
  if (questions.length === 0) {
    return 0
  }
  return Math.max(...questions.map((question) => question.displayOrder)) + 1
}

function QuestionRow({
  question,
  onSave,
  onDelete,
}: {
  question: ApplicationQuestion
  onSave: (questionId: string, payload: {
    questionText: string
    questionType: ApplicationQuestionType
    required: boolean
    displayOrder: number
  }) => Promise<string | null>
  onDelete: (questionId: string) => void
}) {
  const [isEditing, setIsEditing] = useState(false)
  const [text, setText] = useState(question.questionText)
  const [type, setType] = useState<ApplicationQuestionType>(question.questionType)
  const [required, setRequired] = useState(question.required)
  const [order, setOrder] = useState(String(question.displayOrder))
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const startEdit = () => {
    setText(question.questionText)
    setType(question.questionType)
    setRequired(question.required)
    setOrder(String(question.displayOrder))
    setError(null)
    setIsEditing(true)
  }

  const handleSave = async () => {
    const trimmed = text.trim()
    const parsedOrder = Number(order)
    if (!trimmed || !Number.isInteger(parsedOrder) || parsedOrder < 0 || isSaving) {
      setError('Enter a question and a display order of zero or greater.')
      return
    }
    setError(null)
    setIsSaving(true)
    const failure = await onSave(question.id, {
      questionText: trimmed,
      questionType: type,
      required,
      displayOrder: parsedOrder,
    })
    setIsSaving(false)
    if (failure) {
      setError(failure)
      return
    }
    setIsEditing(false)
  }

  if (isEditing) {
    return (
      <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div className="field">
          <label htmlFor={`question-text-${question.id}`}>Question</label>
          <textarea
            id={`question-text-${question.id}`}
            value={text}
            onChange={(event) => setText(event.target.value.slice(0, 500))}
            maxLength={500}
          />
        </div>
        <div className="row-actions">
          <div className="field">
            <label htmlFor={`question-type-${question.id}`}>Answer type</label>
            <select
              id={`question-type-${question.id}`}
              value={type}
              onChange={(event) => setType(event.target.value as ApplicationQuestionType)}
            >
              {QUESTION_TYPE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor={`question-order-${question.id}`}>Order</label>
            <input
              id={`question-order-${question.id}`}
              type="number"
              min={0}
              value={order}
              onChange={(event) => setOrder(event.target.value)}
              style={{ width: 90 }}
            />
          </div>
          <label className="checkbox-field" style={{ alignSelf: 'center', marginTop: 20 }}>
            <input type="checkbox" checked={required} onChange={() => setRequired((current) => !current)} />
            Required
          </label>
        </div>
        {error && (
          <div className="banner banner-error" role="alert">
            {error}
          </div>
        )}
        <div className="row-actions">
          <button type="button" className="btn btn-primary btn-sm" onClick={handleSave} disabled={isSaving}>
            {isSaving ? 'Saving…' : 'Save'}
          </button>
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => setIsEditing(false)}>
            Cancel
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="card row-item">
      <div className="row-main">
        <strong>{question.questionText}</strong>
      </div>
      <div className="row-meta">
        <span className="badge badge-neutral">{QUESTION_TYPE_LABELS[question.questionType]}</span>
        <span className={`badge ${question.required ? 'badge-amber' : 'badge-muted'}`}>
          {question.required ? 'Required' : 'Optional'}
        </span>
        <span className="text-tertiary">Order {question.displayOrder}</span>
      </div>
      <div className="row-actions">
        <button type="button" className="btn btn-secondary btn-sm" onClick={startEdit}>
          Edit
        </button>
        <button type="button" className="btn btn-danger btn-sm" onClick={() => onDelete(question.id)}>
          Delete
        </button>
      </div>
    </div>
  )
}

/**
 * Company-configured custom application questions for an INTERNAL internship — full CRUD via
 * the existing CompanyApplicationQuestionController endpoints, frozen with a 409 once the
 * internship has received its first application (mirrors mobile's
 * internships/[id]/application-questions.tsx exactly, including the locked-state message).
 */
export function ApplicationQuestionsPage() {
  const { internshipId } = useParams<{ internshipId: string }>()
  const navigate = useNavigate()

  const [internship, setInternship] = useState<CompanyInternship | null>(null)
  const [questions, setQuestions] = useState<ApplicationQuestion[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const [newText, setNewText] = useState('')
  const [newType, setNewType] = useState<ApplicationQuestionType>('SHORT_TEXT')
  const [newRequired, setNewRequired] = useState(false)
  const [isAdding, setIsAdding] = useState(false)

  const load = useCallback(() => {
    if (!internshipId) {
      return
    }
    setError(null)
    Promise.all([getCompanyInternship(internshipId), getCompanyApplicationQuestions(internshipId)])
      .then(([loadedInternship, loadedQuestions]) => {
        setInternship(loadedInternship)
        setQuestions(loadedQuestions)
      })
      .catch((err: unknown) => setError(describeQuestionError(err, 'Unable to load application questions.')))
  }, [internshipId])

  useEffect(() => {
    load()
  }, [load])

  if (!internshipId) {
    return <ErrorView message="No internship selected." onRetry={() => navigate('/internships')} />
  }

  if (error) {
    return <ErrorView message={error} onRetry={load} />
  }

  if (!internship || questions === null) {
    return <LoadingView />
  }

  const handleAdd = async (event: FormEvent) => {
    event.preventDefault()
    const trimmed = newText.trim()
    if (!trimmed || isAdding) {
      return
    }
    setActionError(null)
    setIsAdding(true)
    try {
      await createCompanyApplicationQuestion(internshipId, {
        questionText: trimmed,
        questionType: newType,
        required: newRequired,
        displayOrder: nextDisplayOrder(questions),
      })
      setNewText('')
      setNewType('SHORT_TEXT')
      setNewRequired(false)
      load()
    } catch (err) {
      setActionError(describeQuestionError(err, 'Unable to add the question.'))
    } finally {
      setIsAdding(false)
    }
  }

  const handleSaveQuestion = async (
    questionId: string,
    payload: { questionText: string; questionType: ApplicationQuestionType; required: boolean; displayOrder: number },
  ): Promise<string | null> => {
    try {
      await updateCompanyApplicationQuestion(internshipId, questionId, payload)
      load()
      return null
    } catch (err) {
      return describeQuestionError(err, 'Unable to save the question.')
    }
  }

  const handleDelete = async (questionId: string) => {
    setActionError(null)
    try {
      await deleteCompanyApplicationQuestion(internshipId, questionId)
      load()
    } catch (err) {
      setActionError(describeQuestionError(err, 'Unable to delete the question.'))
    }
  }

  const isExternal = internship.applicationMethod === 'EXTERNAL'

  return (
    <div>
      <button type="button" className="back-link" onClick={() => navigate(`/internships/${internshipId}`)}>
        ← Back to {internship.title}
      </button>

      <div className="page-header">
        <div>
          <h1>Application questions</h1>
          <p className="page-header-subtitle">Custom questions candidates answer when applying to this internship.</p>
        </div>
      </div>

      {isExternal ? (
        <div className="banner banner-warning" role="status">
          This internship uses an external application page — Pathora-hosted application questions don't apply to it.
        </div>
      ) : (
        <>
          {actionError && (
            <div className="banner banner-error mb-4" role="alert">
              {actionError}
            </div>
          )}

          {questions.length === 0 ? (
            <div className="card mb-4">
              <p className="text-secondary">No custom questions yet. Candidates will only submit the standard application.</p>
            </div>
          ) : (
            <div className="row-list mb-4">
              {questions
                .slice()
                .sort((a, b) => a.displayOrder - b.displayOrder)
                .map((question) => (
                  <QuestionRow
                    key={question.id}
                    question={question}
                    onSave={handleSaveQuestion}
                    onDelete={handleDelete}
                  />
                ))}
            </div>
          )}

          <div className="card">
            <h2 className="section-title">Add a question</h2>
            <form onSubmit={handleAdd} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div className="field">
                <label htmlFor="new-question-text">Question</label>
                <textarea
                  id="new-question-text"
                  value={newText}
                  onChange={(event) => setNewText(event.target.value.slice(0, 500))}
                  placeholder="Why are you interested in this role?"
                  maxLength={500}
                />
              </div>
              <div className="row-actions">
                <div className="field">
                  <label htmlFor="new-question-type">Answer type</label>
                  <select
                    id="new-question-type"
                    value={newType}
                    onChange={(event) => setNewType(event.target.value as ApplicationQuestionType)}
                  >
                    {QUESTION_TYPE_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </div>
                <label className="checkbox-field" style={{ alignSelf: 'center', marginTop: 20 }}>
                  <input
                    type="checkbox"
                    checked={newRequired}
                    onChange={() => setNewRequired((current) => !current)}
                  />
                  Required
                </label>
              </div>
              <div>
                <button type="submit" className="btn btn-primary" disabled={!newText.trim() || isAdding}>
                  {isAdding ? 'Adding…' : 'Add question'}
                </button>
              </div>
            </form>
          </div>
        </>
      )}
    </div>
  )
}
