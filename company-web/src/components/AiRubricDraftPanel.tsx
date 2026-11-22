import { useState } from 'react'
import {
  ApiError,
  draftRubricWithAi,
  type AiSuggestedRequirement,
  type EvidenceSource,
  type EvidenceStrength,
  type RequirementType,
  type ScreeningRequirementCreatePayload,
} from '../lib/api'
import { AppIcon } from './Visuals'

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

const SOURCE_LABELS: Record<EvidenceSource, string> = {
  SKILLS_LIST: 'Skills list',
  PROJECT: 'Project',
  WORK_EXPERIENCE: 'Work experience',
  INTERNSHIP: 'Internship',
  COURSEWORK: 'Coursework',
  CERTIFICATION: 'Certification',
  RESEARCH: 'Research',
  UNKNOWN: 'Unstructured text',
}

function describeError(err: unknown, fallback: string): string {
  return err instanceof Error ? err.message : fallback
}

function toCreatePayload(suggestion: AiSuggestedRequirement, displayOrder: number): ScreeningRequirementCreatePayload {
  const payload: ScreeningRequirementCreatePayload = {
    requirementType: suggestion.requirementType,
    weight: suggestion.suggestedWeight ?? 10,
    displayOrder,
  }
  if (suggestion.skillResolved && suggestion.resolvedSkillId) {
    payload.skillId = suggestion.resolvedSkillId
  } else {
    payload.label = suggestion.label
  }
  if (suggestion.suggestedMinimumEvidenceStrength) {
    payload.minimumEvidenceStrength = suggestion.suggestedMinimumEvidenceStrength as EvidenceStrength
  }
  if (suggestion.suggestedAcceptedEvidenceSources.length > 0) {
    payload.acceptedEvidenceSources = suggestion.suggestedAcceptedEvidenceSources
  }
  return payload
}

function SuggestionCard({
  suggestion,
  nextDisplayOrder,
  onAdd,
}: {
  suggestion: AiSuggestedRequirement
  nextDisplayOrder: number
  onAdd: (payload: ScreeningRequirementCreatePayload) => Promise<void>
}) {
  const [isAdding, setIsAdding] = useState(false)
  const [isAdded, setIsAdded] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleAdd = async () => {
    setError(null)
    setIsAdding(true)
    try {
      await onAdd(toCreatePayload(suggestion, nextDisplayOrder))
      setIsAdded(true)
    } catch (err) {
      setError(describeError(err, 'Unable to add this requirement.'))
    } finally {
      setIsAdding(false)
    }
  }

  return (
    <div className="card row-item" style={{ alignItems: 'flex-start' }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div className="row-meta">
          <span className={`badge ${TYPE_BADGE_CLASS[suggestion.requirementType]}`}>
            {TYPE_LABELS[suggestion.requirementType]}
          </span>
          <strong dir="auto">{suggestion.resolvedSkillName ?? suggestion.label}</strong>
          {suggestion.suggestedWeight !== null && <span className="text-tertiary">Weight {suggestion.suggestedWeight}</span>}
          {!suggestion.skillResolved && (
            <span className="badge badge-muted" title="This did not match a skill in your catalog by exact name">
              Free-text — not a catalog skill
            </span>
          )}
        </div>
        {(suggestion.suggestedMinimumEvidenceStrength || suggestion.suggestedAcceptedEvidenceSources.length > 0) && (
          <p className="text-tertiary mt-2">
            {suggestion.suggestedMinimumEvidenceStrength && `Minimum strength: ${suggestion.suggestedMinimumEvidenceStrength}`}
            {suggestion.suggestedMinimumEvidenceStrength && suggestion.suggestedAcceptedEvidenceSources.length > 0 && ' · '}
            {suggestion.suggestedAcceptedEvidenceSources.length > 0 &&
              `Evidence from: ${suggestion.suggestedAcceptedEvidenceSources.map((source) => SOURCE_LABELS[source]).join(', ')}`}
          </p>
        )}
        {error && (
          <div className="banner banner-error mt-2" role="alert">
            {error}
          </div>
        )}
      </div>
      <div className="row-actions">
        <button
          type="button"
          className="btn btn-secondary btn-sm"
          onClick={handleAdd}
          disabled={isAdding || isAdded}
        >
          {isAdded ? 'Added to draft' : isAdding ? 'Adding…' : 'Add to draft'}
        </button>
      </div>
    </div>
  )
}

/**
 * "Generate with AI" panel for a DRAFT rubric — Stage 5B Phase C2. Calls the existing Phase C1
 * POST .../rubric/ai-draft endpoint with the recruiter's own free-text description (English or
 * Arabic). Every suggestion stays exactly that — a suggestion — until the recruiter explicitly
 * clicks "Add to draft" on it, which goes through the SAME existing addRequirement call the
 * manual "Add requirement" form already uses (passed in as onAddRequirement); this component
 * never creates, edits, or activates anything by itself. Only ever shown while the rubric is
 * DRAFT — see RubricSection, which doesn't render this for an ACTIVE/ARCHIVED version.
 */
export function AiRubricDraftPanel({
  internshipId,
  nextDisplayOrder,
  onAddRequirement,
}: {
  internshipId: string
  nextDisplayOrder: number
  onAddRequirement: (payload: ScreeningRequirementCreatePayload) => Promise<void>
}) {
  const [instructions, setInstructions] = useState('')
  const [isGenerating, setIsGenerating] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [unavailableReason, setUnavailableReason] = useState<string | null>(null)
  const [suggestions, setSuggestions] = useState<AiSuggestedRequirement[] | null>(null)
  const [isExpanded, setIsExpanded] = useState(false)

  const canGenerate = instructions.trim().length >= 10 && instructions.trim().length <= 2000 && !isGenerating

  const handleGenerate = async () => {
    if (!canGenerate) {
      return
    }
    setError(null)
    setUnavailableReason(null)
    setIsGenerating(true)
    try {
      const result = await draftRubricWithAi(internshipId, instructions.trim())
      if (result.completed) {
        setSuggestions(result.suggestedRequirements)
      } else {
        setSuggestions(null)
        setUnavailableReason(result.unavailableReason ?? 'AI rubric drafting is not available right now.')
      }
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) {
        setError('Please describe your requirements in 10–2000 characters.')
      } else {
        setError(describeError(err, 'Unable to generate suggestions right now.'))
      }
    } finally {
      setIsGenerating(false)
    }
  }

  return (
    <div className="card mt-4" style={{ background: 'var(--pathora-paper)' }}>
      <button
        type="button"
        className="btn btn-secondary btn-sm"
        onClick={() => setIsExpanded((open) => !open)}
        aria-expanded={isExpanded}
      >
        <AppIcon name="sparkle" size={15} /> {isExpanded ? 'Hide' : 'Generate with AI'}
      </button>

      {isExpanded && (
        <div className="mt-4">
          <div className="banner banner-info mb-4" role="status">
            Optional and never required. Describe who you're looking for in plain English or Arabic — AI will
            suggest REQUIRED/PREFERRED/BONUS requirements for you to review. Nothing is added to this draft until
            you explicitly confirm each one below.
          </div>

          <div className="field">
            <label htmlFor="ai-rubric-instructions">Describe your ideal candidate</label>
            <textarea
              id="ai-rubric-instructions"
              dir="auto"
              rows={3}
              maxLength={2000}
              placeholder="e.g. We need a backend engineering intern with Java, Spring Boot, SQL, and Git. University projects count as evidence. Docker is preferred."
              value={instructions}
              onChange={(event) => setInstructions(event.target.value)}
              disabled={isGenerating}
            />
            <span className="field-hint">{instructions.trim().length}/2000 characters (minimum 10)</span>
          </div>

          <div className="mt-2">
            <button type="button" className="btn btn-primary btn-sm" onClick={handleGenerate} disabled={!canGenerate}>
              {isGenerating ? 'Generating…' : 'Generate suggestions'}
            </button>
          </div>

          {error && (
            <div className="banner banner-error mt-4" role="alert">
              {error}
            </div>
          )}

          {unavailableReason && (
            <div className="banner banner-warning mt-4" role="status">
              {unavailableReason}
            </div>
          )}

          {suggestions && suggestions.length === 0 && (
            <p className="text-tertiary mt-4">No suggestions were generated from that description — try adding more detail.</p>
          )}

          {suggestions && suggestions.length > 0 && (
            <div className="row-list mt-4">
              {suggestions.map((suggestion, index) => (
                <SuggestionCard
                  key={`${suggestion.label}-${index}`}
                  suggestion={suggestion}
                  nextDisplayOrder={nextDisplayOrder + index}
                  onAdd={onAddRequirement}
                />
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
