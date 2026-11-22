import { useState } from 'react'
import { ApiError, analyzeEvidenceWithAi, type AiEvidenceSuggestion, type ScreeningReport } from '../lib/api'
import { AppIcon } from './Visuals'

function describeError(err: unknown, fallback: string): string {
  if (err instanceof ApiError && err.status === 403) {
    return 'You do not have access to analyze this application.'
  }
  if (err instanceof ApiError && err.status === 404) {
    return 'This report could not be found — it may have been superseded by a newer screening pass.'
  }
  if (err instanceof ApiError && err.status === 409) {
    return 'The active rubric has changed since this report was produced — re-screen this applicant first.'
  }
  return err instanceof Error ? err.message : fallback
}

function SuggestionRow({ suggestion }: { suggestion: AiEvidenceSuggestion }) {
  return (
    <div className="card row-item" style={{ alignItems: 'flex-start' }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div className="row-meta">
          <span className="badge badge-ai">AI-assisted interpretation</span>
          <strong dir="auto">{suggestion.skillName ?? suggestion.label ?? 'Requirement'}</strong>
          {suggestion.strength && <span className="text-tertiary">Strength: {suggestion.strength}</span>}
          {suggestion.uncertain && <span className="badge badge-amber">Needs human verification</span>}
        </div>
        <blockquote
          dir="auto"
          className="text-secondary mt-2"
          style={{ fontStyle: 'italic', borderLeft: '3px solid #dce6df', paddingLeft: 10, margin: 0 }}
        >
          “{suggestion.excerptQuote}”
        </blockquote>
        <p className="text-secondary mt-2">{suggestion.explanation}</p>
      </div>
    </div>
  )
}

/**
 * "Analyze with AI" panel for one ScreeningReport — Stage 5B Phase C2. Calls the existing
 * Phase C1 endpoint using only (internshipId, applicationId, report.id); the backend alone
 * resolves the frozen CV snapshot and the eligible requirements — nothing here ever reads or
 * sends CV text from the browser. Entirely additive: the deterministic results above this
 * panel (see ReportDetailPanel) are never recomputed or altered by anything in here, and
 * nothing in this panel can change the application's status.
 */
export function AiEvidenceAnalysisPanel({
  internshipId,
  applicationId,
  report,
}: {
  internshipId: string
  applicationId: string
  report: ScreeningReport
}) {
  const [isAnalyzing, setIsAnalyzing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [unavailableReason, setUnavailableReason] = useState<string | null>(null)
  const [suggestions, setSuggestions] = useState<AiEvidenceSuggestion[] | null>(null)
  const [hasRun, setHasRun] = useState(false)

  const eligibleCount = report.results.filter(
    (result) => result.result === 'NEEDS_REVIEW' || result.result === 'INSUFFICIENT_EVIDENCE',
  ).length

  const handleAnalyze = async () => {
    setError(null)
    setUnavailableReason(null)
    setIsAnalyzing(true)
    try {
      const result = await analyzeEvidenceWithAi(internshipId, applicationId, report.id)
      setHasRun(true)
      if (result.completed) {
        setSuggestions(result.suggestions)
      } else {
        setSuggestions(null)
        setUnavailableReason(result.unavailableReason ?? 'AI analysis is not available right now.')
      }
    } catch (err) {
      setHasRun(true)
      setError(describeError(err, 'Unable to run AI analysis right now.'))
    } finally {
      setIsAnalyzing(false)
    }
  }

  if (eligibleCount === 0) {
    return (
      <div className="card mt-4" style={{ background: 'var(--pathora-paper)' }}>
        <div className="row-meta">
          <AppIcon name="sparkle" size={16} />
          <strong>AI-assisted interpretation</strong>
        </div>
        <p className="text-tertiary mt-2">
          Every requirement above was confidently resolved by the deterministic engine — there is nothing here for
          AI to add.
        </p>
      </div>
    )
  }

  return (
    <div className="card mt-4" style={{ background: 'var(--pathora-paper)' }}>
      <div className="row-meta">
        <AppIcon name="sparkle" size={16} />
        <strong>AI-assisted interpretation</strong>
        <span className="text-tertiary">
          {eligibleCount} requirement{eligibleCount === 1 ? '' : 's'} above need further interpretation
        </span>
      </div>
      <p className="text-tertiary mt-2">
        Optional and never required for a recruitment decision. AI only looks at the {eligibleCount} requirement
        {eligibleCount === 1 ? '' : 's'} marked "Insufficient evidence" or "Manual review needed" above — it never
        re-decides a requirement the deterministic engine already confidently matched or ruled out, and it can
        never change this application's status.
      </p>

      <div className="mt-2">
        <button type="button" className="btn btn-secondary btn-sm" onClick={handleAnalyze} disabled={isAnalyzing}>
          <AppIcon name="sparkle" size={14} /> {isAnalyzing ? 'Analyzing…' : hasRun ? 'Analyze again' : 'Analyze with AI'}
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

      {hasRun && !error && !unavailableReason && suggestions && suggestions.length === 0 && (
        <p className="text-tertiary mt-4">
          AI did not find additional supporting evidence for these requirements in the candidate's CV. This does
          not necessarily mean the candidate lacks the skill — only that no excerpt could be confidently cited.
        </p>
      )}

      {suggestions && suggestions.length > 0 && (
        <div className="row-list mt-4">
          {suggestions.map((suggestion) => (
            <SuggestionRow key={suggestion.requirementId} suggestion={suggestion} />
          ))}
        </div>
      )}
    </div>
  )
}
