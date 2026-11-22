import { useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { ApplicationDetailCard, ApplicationDetailText } from '@/components/application-polish';
import { PathoraButton } from '@/components/pathora-button';
import { PathoraColors as C, PathoraFonts as F } from '@/constants/pathora-theme';
import { useAuth } from '@/context/auth-context';
import {
  ApiError,
  analyzeEvidenceWithAi,
  getLatestScreeningReport,
  screenApplicant,
  type AiEvidenceSuggestion,
  type ScreeningReport,
  type ScreeningOutcome,
} from '@/lib/api';
import { describeOnboardingError } from '@/lib/onboarding';

const OUTCOME_LABELS: Record<ScreeningOutcome, string> = {
  MATCHED: 'Evidence found',
  NOT_EVIDENCED: 'Evidence not found',
  INSUFFICIENT_EVIDENCE: 'Insufficient evidence',
  NEEDS_REVIEW: 'Needs review',
};

const OUTCOME_COLORS: Record<ScreeningOutcome, string> = {
  MATCHED: C.success,
  NOT_EVIDENCED: C.textSecondary,
  INSUFFICIENT_EVIDENCE: '#B07A1E',
  NEEDS_REVIEW: '#B07A1E',
};

function OutcomePill({ outcome }: { outcome: ScreeningOutcome }) {
  return (
    <View style={[styles.pill, { backgroundColor: `${OUTCOME_COLORS[outcome]}1A` }]}>
      <Text style={[styles.pillText, { color: OUTCOME_COLORS[outcome] }]}>{OUTCOME_LABELS[outcome]}</Text>
    </View>
  );
}

/**
 * Deterministic screening report + optional "Analyze with AI" — Stage 5B Phase C3. Entirely
 * additive to the existing applicant detail screen: nothing here changes the application's
 * status, and nothing here is required before the recruiter decides (see the unchanged
 * Accept/Reject card below this section). Mirrors company-web's ReportDetailPanel/
 * AiEvidenceAnalysisPanel, condensed for a small screen.
 */
export function ScreeningReportSection({ internshipId, applicationId }: { internshipId: string; applicationId: string }) {
  const { token } = useAuth();
  const [report, setReport] = useState<ScreeningReport | null>(null);
  const [hasNoReport, setHasNoReport] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isScreening, setIsScreening] = useState(false);
  const [screenError, setScreenError] = useState<string | null>(null);

  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [aiError, setAiError] = useState<string | null>(null);
  const [aiUnavailable, setAiUnavailable] = useState<string | null>(null);
  const [aiSuggestions, setAiSuggestions] = useState<AiEvidenceSuggestion[] | null>(null);
  const [hasRunAi, setHasRunAi] = useState(false);

  const load = useCallback(async () => {
    if (!token) return;
    try {
      const loaded = await getLatestScreeningReport(token, internshipId, applicationId);
      setReport(loaded);
      setHasNoReport(false);
      setLoadError(null);
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setReport(null);
        setHasNoReport(true);
        setLoadError(null);
      } else {
        setLoadError(describeOnboardingError(err));
      }
    }
  }, [token, internshipId, applicationId]);

  useFocusEffect(useCallback(() => { void load(); }, [load]));

  const handleScreen = async () => {
    if (!token || isScreening) return;
    setScreenError(null);
    setIsScreening(true);
    try {
      const fresh = await screenApplicant(token, internshipId, applicationId);
      setReport(fresh);
      setHasNoReport(false);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setScreenError('This internship has no active screening rubric yet.');
      } else {
        setScreenError(describeOnboardingError(err));
      }
    } finally {
      setIsScreening(false);
    }
  };

  const eligibleCount = report
    ? report.results.filter((r) => r.result === 'NEEDS_REVIEW' || r.result === 'INSUFFICIENT_EVIDENCE').length
    : 0;

  const handleAnalyze = async () => {
    if (!token || !report || isAnalyzing) return;
    setAiError(null);
    setAiUnavailable(null);
    setIsAnalyzing(true);
    try {
      const result = await analyzeEvidenceWithAi(token, internshipId, applicationId, report.id);
      setHasRunAi(true);
      if (result.completed) {
        setAiSuggestions(result.suggestions);
      } else {
        setAiSuggestions(null);
        setAiUnavailable(result.unavailableReason ?? 'AI analysis is not available right now.');
      }
    } catch (err) {
      setHasRunAi(true);
      setAiError(describeOnboardingError(err));
    } finally {
      setIsAnalyzing(false);
    }
  };

  if (loadError) {
    return (
      <ApplicationDetailCard label="Smart ATS screening">
        <ApplicationDetailText muted>{loadError}</ApplicationDetailText>
        <PathoraButton label="Try again" variant="secondary" onPress={() => void load()} />
      </ApplicationDetailCard>
    );
  }

  if (hasNoReport) {
    return (
      <ApplicationDetailCard label="Smart ATS screening">
        <ApplicationDetailText muted>This applicant has not been screened against your rubric yet.</ApplicationDetailText>
        {screenError ? <Text style={styles.errorText}>{screenError}</Text> : null}
        <PathoraButton label={isScreening ? 'Screening…' : 'Screen applicant'} onPress={() => void handleScreen()} disabled={isScreening} />
      </ApplicationDetailCard>
    );
  }

  if (!report) {
    return null;
  }

  return (
    <>
      <ApplicationDetailCard label="Smart ATS screening">
        <View style={styles.summaryRow}>
          <Text style={styles.summaryText}>Required {report.matchedRequiredCount}/{report.totalRequiredCount}</Text>
          <Text style={styles.summaryText}>Preferred {report.matchedPreferredCount}/{report.totalPreferredCount}</Text>
          <Text style={styles.summaryText}>Bonus {report.matchedBonusCount}/{report.totalBonusCount}</Text>
        </View>
        <ApplicationDetailText muted>
          Weighted coverage {report.weightedCoveragePercentage}% — a transparent ratio, never a hiring probability.
        </ApplicationDetailText>
        {report.cvTextUnavailable ? (
          <Text style={styles.warnText}>
            This candidate&rsquo;s CV text could not be read — requirements marked &ldquo;Needs review&rdquo; cannot be confirmed as
            missing evidence.
          </Text>
        ) : null}

        {report.results.map((result) => (
          <View key={result.requirementId} style={styles.resultRow}>
            <View style={styles.resultTop}>
              <OutcomePill outcome={result.result} />
              <Text style={styles.resultName} numberOfLines={1}>{result.skillName ?? result.label ?? 'Requirement'}</Text>
            </View>
            {result.textReference ? <Text style={styles.quote} numberOfLines={3}>“{result.textReference}”</Text> : null}
            <Text style={styles.reasoning}>{result.reasoning}</Text>
          </View>
        ))}

        <PathoraButton label={isScreening ? 'Screening…' : 'Re-screen'} variant="secondary" onPress={() => void handleScreen()} disabled={isScreening} />
      </ApplicationDetailCard>

      {eligibleCount > 0 ? (
        <ApplicationDetailCard label="AI-assisted interpretation (optional)">
          <ApplicationDetailText muted>
            {eligibleCount} requirement{eligibleCount === 1 ? '' : 's'} above need further interpretation. AI only
            looks at those — it never re-decides a confident deterministic result, and it can never change this
            application&rsquo;s status.
          </ApplicationDetailText>
          <PathoraButton
            label={isAnalyzing ? 'Analyzing…' : hasRunAi ? 'Analyze again' : 'Analyze with AI'}
            variant="secondary"
            onPress={() => void handleAnalyze()}
            disabled={isAnalyzing}
          />
          {aiError ? <Text style={styles.errorText}>{aiError}</Text> : null}
          {aiUnavailable ? (
            <Text style={styles.warnText}>
              AI assistance isn&rsquo;t available right now — a deliberate safety setting, not an error: {aiUnavailable}
            </Text>
          ) : null}
          {hasRunAi && !aiError && !aiUnavailable && aiSuggestions && aiSuggestions.length === 0 ? (
            <ApplicationDetailText muted>
              AI did not find additional supporting evidence. This does not mean the candidate lacks the skill —
              only that no excerpt could be confidently cited.
            </ApplicationDetailText>
          ) : null}
          {aiSuggestions?.map((suggestion) => (
            <View key={suggestion.requirementId} style={styles.resultRow}>
              <View style={styles.resultTop}>
                <View style={styles.aiPill}><Text style={styles.aiPillText}>AI</Text></View>
                <Text style={styles.resultName} numberOfLines={1}>{suggestion.skillName ?? suggestion.label ?? 'Requirement'}</Text>
                {suggestion.uncertain ? <View style={styles.reviewPill}><Text style={styles.reviewPillText}>Needs verification</Text></View> : null}
              </View>
              <Text style={styles.quote} numberOfLines={4}>“{suggestion.excerptQuote}”</Text>
              <Text style={styles.reasoning}>{suggestion.explanation}</Text>
            </View>
          ))}
        </ApplicationDetailCard>
      ) : null}
    </>
  );
}

const styles = StyleSheet.create({
  summaryRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  summaryText: { fontFamily: F.en.semiBold, fontSize: 12, color: C.ink900 },
  warnText: { fontFamily: F.en.medium, fontSize: 11.5, color: '#B07A1E', lineHeight: 17 },
  errorText: { fontFamily: F.en.medium, fontSize: 12, color: C.error },
  resultRow: { gap: 5, paddingVertical: 9, borderTopWidth: 1, borderTopColor: C.border },
  resultTop: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  resultName: { flex: 1, minWidth: 0, fontFamily: F.en.bold, fontSize: 13, color: C.ink900 },
  pill: { borderRadius: 999, paddingHorizontal: 9, paddingVertical: 3 },
  pillText: { fontFamily: F.en.bold, fontSize: 10, letterSpacing: 0.3 },
  quote: { fontFamily: F.en.regular, fontSize: 12.5, color: C.textSecondary, fontStyle: 'italic', lineHeight: 18 },
  reasoning: { fontFamily: F.en.medium, fontSize: 12, color: C.textSecondary, lineHeight: 17 },
  aiPill: { borderRadius: 999, paddingHorizontal: 9, paddingVertical: 3, backgroundColor: '#ECE7FB' },
  aiPillText: { fontFamily: F.en.bold, fontSize: 10, color: '#5B3DF0', letterSpacing: 0.3 },
  reviewPill: { borderRadius: 999, paddingHorizontal: 9, paddingVertical: 3, backgroundColor: 'rgba(176,122,30,0.12)' },
  reviewPillText: { fontFamily: F.en.bold, fontSize: 9.5, color: '#B07A1E' },
});
