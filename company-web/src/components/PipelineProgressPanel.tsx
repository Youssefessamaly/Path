import { useCallback, useEffect, useState } from 'react'
import {
  ApiError,
  advanceApplicationStage,
  getCandidateHistory,
  getCandidateStage,
  updateApplicationStatus,
  type ApplicationDecision,
  type CandidateStage,
  type StageEvent,
} from '../lib/api'
import { formatDisplayDate } from '../lib/date'
import { AppIcon } from './Visuals'

function describeError(err: unknown, fallback: string): string {
  if (err instanceof ApiError && err.status === 409) {
    return 'This no longer matches the applicant’s current state — refreshing.'
  }
  return err instanceof Error ? err.message : fallback
}

/**
 * Recruitment-pipeline progression + decision controls for one applicant — the
 * Application → CV Screening → recruiter decision → next configured stage workflow.
 * Entirely independent of Smart ATS screening coverage/AI output: neither can trigger
 * "Advance"/"Reject"/"Accept" — every transition here requires an explicit recruiter click
 * and a confirmation dialog. "Advance" always uses THIS internship's own persisted, ordered
 * stage list (GET /company/ats/applications/{id}); "Reject"/"Accept" reuse the existing,
 * unmodified application-status endpoint (previously mobile-only).
 */
export function PipelineProgressPanel({ internshipId, applicationId }: { internshipId: string; applicationId: string }) {
  const [stage, setStage] = useState<CandidateStage | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [history, setHistory] = useState<StageEvent[] | null>(null)
  const [showHistory, setShowHistory] = useState(false)
  const [historyError, setHistoryError] = useState<string | null>(null)

  const [isAdvancing, setIsAdvancing] = useState(false)
  const [isDeciding, setIsDeciding] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const load = useCallback(() => {
    setLoadError(null)
    getCandidateStage(applicationId)
      .then(setStage)
      .catch((err: unknown) => setLoadError(describeError(err, 'Unable to load recruitment pipeline status.')))
  }, [applicationId])

  useEffect(() => {
    load()
  }, [load])

  const loadHistory = () => {
    if (showHistory) {
      setShowHistory(false)
      return
    }
    setShowHistory(true)
    if (history) {
      return
    }
    setHistoryError(null)
    getCandidateHistory(applicationId)
      .then(setHistory)
      .catch((err: unknown) => setHistoryError(describeError(err, 'Unable to load stage history.')))
  }

  const handleAdvance = async () => {
    if (!stage?.nextStageName) {
      return
    }
    if (!window.confirm(`Advance this applicant to "${stage.nextStageName}"?`)) {
      return
    }
    setActionError(null)
    setIsAdvancing(true)
    try {
      const updated = await advanceApplicationStage(applicationId)
      setStage(updated)
      setHistory(null)
      setShowHistory(false)
    } catch (err) {
      setActionError(describeError(err, 'Unable to advance this applicant.'))
      load()
    } finally {
      setIsAdvancing(false)
    }
  }

  const handleDecision = async (decision: ApplicationDecision) => {
    const verb = decision === 'ACCEPTED' ? 'Accept' : 'Reject'
    if (!window.confirm(`${verb} this applicant? This cannot be undone.`)) {
      return
    }
    setActionError(null)
    setIsDeciding(true)
    try {
      await updateApplicationStatus(internshipId, applicationId, decision)
      load()
    } catch (err) {
      setActionError(describeError(err, `Unable to ${verb.toLowerCase()} this applicant.`))
      load()
    } finally {
      setIsDeciding(false)
    }
  }

  if (loadError) {
    return (
      <div className="card mt-4">
        <h2 className="section-title">Recruitment Pipeline</h2>
        <div className="banner banner-error" role="alert">
          {loadError}
        </div>
        <button type="button" className="btn btn-secondary btn-sm mt-2" onClick={load}>
          Try again
        </button>
      </div>
    )
  }

  if (!stage) {
    return (
      <div className="card mt-4">
        <h2 className="section-title">Recruitment Pipeline</h2>
        <p className="text-tertiary">Loading…</p>
      </div>
    )
  }

  const isDecided = stage.applicationStatus !== 'APPLIED'
  const hasPipeline = stage.pipelineId !== null

  return (
    <div className="card mt-4">
      <h2 className="section-title">Recruitment Pipeline</h2>

      {!hasPipeline && (
        <p className="text-secondary">
          This internship has no recruitment pipeline assigned yet — assign one from Recruitment Pipeline above
          to enable stage progression for this applicant.
        </p>
      )}

      {hasPipeline && (
        <>
          <div className="row-meta mb-4">
            <span className="badge badge-neutral">{stage.pipelineName}</span>
            <span
              className={`badge ${
                stage.applicationStatus === 'ACCEPTED'
                  ? 'badge-success'
                  : stage.applicationStatus === 'REJECTED'
                    ? 'badge-error'
                    : 'badge-muted'
              }`}
            >
              {stage.applicationStatus === 'APPLIED' ? 'In progress' : stage.applicationStatus}
            </span>
          </div>

          {stage.currentStageName ? (
            <div className="mb-4">
              <div className="field-hint mb-2">Pipeline progression</div>
              <div className="row-meta" style={{ flexWrap: 'wrap' }}>
                {stage.stages?.map((s) => (
                  <span
                    key={s.id}
                    className={`badge ${s.id === stage.currentStageId ? 'badge-success' : 'badge-muted'}`}
                  >
                    {s.name}
                  </span>
                ))}
              </div>
              <p className="text-secondary mt-2">
                Currently at <strong>{stage.currentStageName}</strong>
                {stage.enteredCurrentStageAt && ` since ${formatDisplayDate(stage.enteredCurrentStageAt)}`}.
                {stage.nextStageName
                  ? ` Next stage: ${stage.nextStageName}.`
                  : ' This is the final stage.'}
              </p>
            </div>
          ) : (
            <p className="text-secondary mb-4">
              Not yet tracked in the pipeline — this is expected for an application decided before a pipeline
              was assigned.
            </p>
          )}

          {actionError && (
            <div className="banner banner-error mb-4" role="alert">
              {actionError}
            </div>
          )}

          {!isDecided && (
            <div className="row-actions">
              {stage.nextStageName && (
                <button type="button" className="btn btn-primary btn-sm" onClick={handleAdvance} disabled={isAdvancing || isDeciding}>
                  {isAdvancing ? 'Advancing…' : `Advance to ${stage.nextStageName}`}
                </button>
              )}
              {!stage.nextStageName && stage.currentStageName && (
                <button
                  type="button"
                  className="btn btn-primary btn-sm"
                  onClick={() => handleDecision('ACCEPTED')}
                  disabled={isAdvancing || isDeciding}
                >
                  {isDeciding ? 'Working…' : 'Accept Applicant'}
                </button>
              )}
              <button
                type="button"
                className="btn btn-danger btn-sm"
                onClick={() => handleDecision('REJECTED')}
                disabled={isAdvancing || isDeciding}
              >
                {isDeciding ? 'Working…' : 'Reject Applicant'}
              </button>
            </div>
          )}

          {isDecided && (
            <p className="text-tertiary">
              This application has already been {stage.applicationStatus.toLowerCase()} — no further
              recruitment-stage action is possible.
            </p>
          )}

          <button type="button" className="btn btn-secondary btn-sm mt-4" onClick={loadHistory}>
            <AppIcon name="clock" size={14} /> {showHistory ? 'Hide' : 'Show'} stage history
          </button>

          {showHistory && (
            <div className="row-list mt-2">
              {historyError && (
                <div className="banner banner-error" role="alert">
                  {historyError}
                </div>
              )}
              {history?.length === 0 && <p className="text-tertiary">No stage history yet.</p>}
              {history?.map((event) => (
                <div className="card row-item" key={event.id}>
                  <div style={{ flex: 1 }}>
                    <div className="row-meta">
                      <span className="badge badge-neutral">{event.eventKind}</span>
                      <strong>
                        {event.fromStageName ? `${event.fromStageName} → ${event.toStageName}` : event.toStageName}
                      </strong>
                    </div>
                    <p className="text-tertiary mt-1">{formatDisplayDate(event.occurredAt)}</p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )
}
