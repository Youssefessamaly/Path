import { useFocusEffect } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { useCallback, useState } from 'react';
import { Alert, StyleSheet, Text, View } from 'react-native';

import { ApplicationDetailCard, ApplicationDetailText } from '@/components/application-polish';
import { PathoraButton } from '@/components/pathora-button';
import { PathoraColors as C, PathoraFonts as F } from '@/constants/pathora-theme';
import { useAuth } from '@/context/auth-context';
import {
  advanceApplicationStage,
  getCandidateHistory,
  getCandidateStage,
  updateCompanyApplicationStatus,
  type CandidateStage,
  type CompanyApplicationDecision,
  type StageEvent,
} from '@/lib/api';
import { formatDisplayDate } from '@/lib/date';
import { describeOnboardingError } from '@/lib/onboarding';

/**
 * Recruitment-pipeline progression + decision controls — the Application → CV Screening
 * → recruiter decision → next configured stage workflow, replacing the old plain
 * Accept/Reject-only card. "Advance" always uses this internship's own persisted, ordered
 * stage list (never a hardcoded sequence); Accept/Reject reuse the existing, unmodified
 * status-decision endpoint. Neither Smart ATS screening nor AI output can trigger any of
 * these — every transition requires an explicit tap and a confirmation dialog.
 */
export function PipelineProgressSection({
  internshipId,
  applicationId,
  onChanged,
}: {
  internshipId: string;
  applicationId: string;
  onChanged: () => void;
}) {
  const { token } = useAuth();
  const [stage, setStage] = useState<CandidateStage | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [history, setHistory] = useState<StageEvent[] | null>(null);
  const [showHistory, setShowHistory] = useState(false);

  const [isAdvancing, setIsAdvancing] = useState(false);
  const [isDeciding, setIsDeciding] = useState(false);

  const load = useCallback(async () => {
    if (!token) return;
    try {
      const loaded = await getCandidateStage(token, applicationId);
      setStage(loaded);
      setLoadError(null);
    } catch (err) {
      setLoadError(describeOnboardingError(err));
    }
  }, [token, applicationId]);

  useFocusEffect(useCallback(() => { void load(); }, [load]));

  const toggleHistory = async () => {
    if (showHistory) {
      setShowHistory(false);
      return;
    }
    setShowHistory(true);
    if (history || !token) return;
    try {
      setHistory(await getCandidateHistory(token, applicationId));
    } catch {
      setHistory([]);
    }
  };

  const handleAdvance = () => {
    if (!stage?.nextStageName || !token) return;
    Alert.alert('Advance applicant?', `Move this applicant to "${stage.nextStageName}"?`, [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Advance',
        onPress: async () => {
          setIsAdvancing(true);
          try {
            const updated = await advanceApplicationStage(token, applicationId);
            setStage(updated);
            setHistory(null);
            setShowHistory(false);
            onChanged();
          } catch (err) {
            Alert.alert('Something went wrong', describeOnboardingError(err));
            void load();
          } finally {
            setIsAdvancing(false);
          }
        },
      },
    ]);
  };

  const handleDecision = (decision: CompanyApplicationDecision) => {
    if (!token) return;
    const isAccept = decision === 'ACCEPTED';
    Alert.alert(
      isAccept ? 'Accept applicant?' : 'Reject applicant?',
      'This cannot be undone.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: isAccept ? 'Accept' : 'Reject',
          style: isAccept ? 'default' : 'destructive',
          onPress: async () => {
            setIsDeciding(true);
            try {
              await updateCompanyApplicationStatus(token, internshipId, applicationId, decision);
              await load();
              onChanged();
            } catch (err) {
              Alert.alert('Something went wrong', describeOnboardingError(err));
              void load();
            } finally {
              setIsDeciding(false);
            }
          },
        },
      ],
    );
  };

  if (loadError) {
    return (
      <ApplicationDetailCard label="Recruitment pipeline">
        <ApplicationDetailText muted>{loadError}</ApplicationDetailText>
        <PathoraButton label="Try again" variant="secondary" onPress={() => void load()} />
      </ApplicationDetailCard>
    );
  }

  if (!stage) {
    return (
      <ApplicationDetailCard label="Recruitment pipeline">
        <ApplicationDetailText muted>Loading…</ApplicationDetailText>
      </ApplicationDetailCard>
    );
  }

  const isDecided = stage.applicationStatus !== 'APPLIED';
  const hasPipeline = stage.pipelineId !== null;

  return (
    <ApplicationDetailCard label="Recruitment pipeline">
      {!hasPipeline ? (
        <ApplicationDetailText muted>
          This internship has no recruitment pipeline assigned yet.
        </ApplicationDetailText>
      ) : (
        <>
          <Text style={styles.pipelineName}>{stage.pipelineName}</Text>

          {stage.currentStageName ? (
            <>
              <View style={styles.stageRow}>
                {stage.stages?.map((s) => (
                  <View key={s.id} style={[styles.stagePill, s.id === stage.currentStageId && styles.stagePillActive]}>
                    <Text style={[styles.stagePillText, s.id === stage.currentStageId && styles.stagePillTextActive]}>
                      {s.name}
                    </Text>
                  </View>
                ))}
              </View>
              <ApplicationDetailText muted>
                Currently at {stage.currentStageName}
                {stage.enteredCurrentStageAt ? ` since ${formatDisplayDate(stage.enteredCurrentStageAt)}` : ''}.
                {stage.nextStageName ? ` Next: ${stage.nextStageName}.` : ' This is the final stage.'}
              </ApplicationDetailText>
            </>
          ) : (
            <ApplicationDetailText muted>
              Not yet tracked in the pipeline — expected for an application decided before a pipeline existed.
            </ApplicationDetailText>
          )}

          {!isDecided && (
            <View style={styles.actions}>
              {stage.nextStageName ? (
                <View style={styles.actionButton}>
                  <PathoraButton
                    label={isAdvancing ? 'Advancing…' : `Advance to ${stage.nextStageName}`}
                    onPress={handleAdvance}
                    disabled={isAdvancing || isDeciding}
                  />
                </View>
              ) : stage.currentStageName ? (
                <View style={styles.actionButton}>
                  <PathoraButton
                    label="Accept"
                    variant="success"
                    onPress={() => handleDecision('ACCEPTED')}
                    disabled={isAdvancing || isDeciding}
                  />
                </View>
              ) : null}
              <View style={styles.actionButton}>
                <PathoraButton
                  label="Reject"
                  variant="destructive"
                  onPress={() => handleDecision('REJECTED')}
                  disabled={isAdvancing || isDeciding}
                />
              </View>
            </View>
          )}

          {isDecided && (
            <View style={styles.decisionDone}>
              <SymbolView
                name={{
                  ios: stage.applicationStatus === 'ACCEPTED' ? 'checkmark.circle.fill' : 'xmark.circle.fill',
                  android: stage.applicationStatus === 'ACCEPTED' ? 'check_circle' : 'cancel',
                  web: stage.applicationStatus === 'ACCEPTED' ? 'check_circle' : 'cancel',
                }}
                size={20}
                tintColor={stage.applicationStatus === 'ACCEPTED' ? C.success : C.error}
              />
              <Text style={styles.decisionDoneText}>
                {stage.applicationStatus === 'ACCEPTED' ? 'You accepted this applicant' : 'You rejected this applicant'}
              </Text>
            </View>
          )}

          <PathoraButton
            label={showHistory ? 'Hide stage history' : 'Show stage history'}
            variant="secondary"
            onPress={() => void toggleHistory()}
          />

          {showHistory && history?.map((event) => (
            <View key={event.id} style={styles.historyRow}>
              <Text style={styles.historyKind}>{event.eventKind}</Text>
              <Text style={styles.historyText}>
                {event.fromStageName ? `${event.fromStageName} → ${event.toStageName}` : event.toStageName}
              </Text>
              <Text style={styles.historyDate}>{formatDisplayDate(event.occurredAt)}</Text>
            </View>
          ))}
        </>
      )}
    </ApplicationDetailCard>
  );
}

const styles = StyleSheet.create({
  pipelineName: { fontFamily: F.en.bold, fontSize: 13.5, color: C.ink900 },
  stageRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginTop: 4 },
  stagePill: { borderRadius: 999, paddingHorizontal: 10, paddingVertical: 6, backgroundColor: '#F0F2F2' },
  stagePillActive: { backgroundColor: C.teal100 },
  stagePillText: { fontFamily: F.en.semiBold, fontSize: 11, color: C.textSecondary },
  stagePillTextActive: { color: C.teal700 },
  actions: { flexDirection: 'row', gap: 10, marginTop: 4 },
  actionButton: { flex: 1 },
  decisionDone: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  decisionDoneText: { fontFamily: F.en.bold, fontSize: 13.5, color: C.ink900, flex: 1 },
  historyRow: { borderTopWidth: 1, borderTopColor: C.border, paddingTop: 8, gap: 2 },
  historyKind: { fontFamily: F.en.bold, fontSize: 10, color: C.teal700 },
  historyText: { fontFamily: F.en.semiBold, fontSize: 12.5, color: C.ink900 },
  historyDate: { fontFamily: F.en.medium, fontSize: 10.5, color: C.textTertiary },
});
