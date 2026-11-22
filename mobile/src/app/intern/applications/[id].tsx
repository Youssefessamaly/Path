import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ApplicationDetailCard, ApplicationDetailText } from '@/components/application-polish';
import { ApplicationStatusBadge } from '@/components/application-status-badge';
import { ProfileAvatar } from '@/components/profile-avatar';
import { AuthBackButton } from '@/components/auth-back-button';
import { PathoraDetailHeader } from '@/components/pathora-detail-header';
import { OnboardingGuard } from '@/components/onboarding/onboarding-guard';
import { PathoraButton } from '@/components/pathora-button';
import { PathoraColors, PathoraFonts } from '@/constants/pathora-theme';
import { useAuth } from '@/context/auth-context';
import { getInternApplication, withdrawAiAnalysisConsent, type InternApplication } from '@/lib/api';
import { formatDisplayDate } from '@/lib/date';
import { initialsForName } from '@/lib/initials';
import { describeOnboardingError } from '@/lib/onboarding';

/** Only shows backend-supplied application status and immutable submitted answer snapshots. */
function ApplicationDetailsScreen({ applicationId }: { applicationId: string }) {
  const router = useRouter();
  const { token } = useAuth();
  const [application, setApplication] = useState<InternApplication | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isWithdrawingConsent, setIsWithdrawingConsent] = useState(false);

  const load = useCallback(async () => {
    if (!token) return;
    try {
      const data = await getInternApplication(token, applicationId);
      setApplication(data); setError(null);
    } catch (err) { setError(describeOnboardingError(err)); }
  }, [token, applicationId]);

  useFocusEffect(useCallback(() => { void load(); }, [load]));

  const confirmWithdrawAiConsent = useCallback(() => {
    if (!token) return;
    Alert.alert(
      'Withdraw AI analysis permission?',
      'This company will no longer be able to request AI-assisted analysis of this application’s CV. '
        + 'Your application and its standard automatic screening are not affected either way.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Withdraw',
          style: 'destructive',
          onPress: async () => {
            setIsWithdrawingConsent(true);
            try {
              const updated = await withdrawAiAnalysisConsent(token, applicationId);
              setApplication(updated);
            } catch (err) {
              Alert.alert('Unable to withdraw permission', describeOnboardingError(err));
            } finally {
              setIsWithdrawingConsent(false);
            }
          },
        },
      ],
    );
  }, [token, applicationId]);

  if (error || !application) {
    return (
      <SafeAreaView style={styles.safeArea} edges={['top']}>
        <AuthBackButton onPress={() => router.back()} style={styles.backButton} />
        <View style={styles.center}>
          {error ? <>
            <Text style={styles.stateTitle}>Unable to load application</Text>
            <Text style={styles.stateBody}>{error}</Text>
            <PathoraButton label="Try again" variant="secondary" onPress={() => void load()} style={styles.retry} />
          </> : <ActivityIndicator size="large" color={PathoraColors.teal600} />}
        </View>
      </SafeAreaView>
    );
  }

  const companyName = application.companyName ?? 'Company unavailable';
  const statusExplanation = application.status === 'APPLIED'
    ? 'Your application has been submitted. Its status will update when the company makes a decision.'
    : application.status === 'ACCEPTED'
      ? 'The company has accepted your application.'
      : 'The company has rejected your application.';

  return (
    <SafeAreaView style={styles.safeArea} edges={['top']}>
      <ScrollView
        style={{ backgroundColor: PathoraColors.paper }} contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <PathoraDetailHeader horizontalInset={24} onBack={() => router.back()} eyebrow="APPLICATION HISTORY" title="Application details" subtitle="A record of what you submitted through Path." />

        <View style={styles.profileCard}>
          <View style={styles.profileTop}>
            <ProfileAvatar company size={56} initials={initialsForName(companyName)} photoUrl={application.companyLogoUrl} />
            <View style={styles.profileIdentity}>
              <Text style={styles.roleTitle}>{application.internshipTitle ?? 'Internship no longer available'}</Text>
              <Text style={styles.companyName}>{companyName}</Text>
            </View>
          </View>
          <View style={styles.statusRow}>
            <ApplicationStatusBadge status={application.status} />
            <Text style={styles.dateText}>Applied {formatDisplayDate(application.appliedAt)}</Text>
          </View>
        </View>

        <ApplicationDetailCard label="Current status">
          <View style={styles.statusMessage}>
            <SymbolView name={{ ios: 'info.circle', android: 'info', web: 'info' }} size={20} tintColor={PathoraColors.teal600} />
            <Text style={styles.explanation}>{statusExplanation}</Text>
          </View>
        </ApplicationDetailCard>

        <ApplicationDetailCard label="Submitted CV">
          <View style={styles.cvRow}>
            <View style={styles.cvIcon}>
              <SymbolView name={{ ios: 'doc.text', android: 'description', web: 'description' }} size={23} tintColor={PathoraColors.teal700} />
            </View>
            <View style={styles.cvText}>
              <Text style={styles.cvName} numberOfLines={2}>{application.cvFileName}</Text>
              <Text style={styles.cvCaption}>CV attached when you applied</Text>
            </View>
          </View>
        </ApplicationDetailCard>

        <ApplicationDetailCard label="AI-assisted CV analysis">
          <View style={styles.statusMessage}>
            <SymbolView name={{ ios: 'sparkles', android: 'auto_awesome', web: 'auto_awesome' }} size={20} tintColor={PathoraColors.teal600} />
            <Text style={styles.explanation}>
              {application.aiAnalysisConsent
                ? 'You authorized this company to request AI-assisted analysis of this application’s CV, using Claude, a third-party AI model from Anthropic. You can withdraw this at any time below.'
                : 'You have not authorized AI-assisted analysis of this application’s CV. This never affects your application or its standard automatic screening.'}
            </Text>
          </View>
          {application.aiAnalysisConsent ? (
            <PathoraButton
              label={isWithdrawingConsent ? 'Withdrawing…' : 'Withdraw AI analysis permission'}
              variant="destructive"
              onPress={confirmWithdrawAiConsent}
              disabled={isWithdrawingConsent}
              style={styles.withdrawButton}
            />
          ) : null}
        </ApplicationDetailCard>

        <ApplicationDetailCard label="Cover letter">
          <ApplicationDetailText muted={!application.coverLetter}>{application.coverLetter || 'No cover letter provided'}</ApplicationDetailText>
        </ApplicationDetailCard>

        {((application.linkedinUrl || application.portfolioUrl)) ? (
          <ApplicationDetailCard label="Provided links">
            {(application.linkedinUrl) ? (<>
              <Text style={styles.minorHeading}>LinkedIn</Text>
              <ApplicationDetailText>{application.linkedinUrl}</ApplicationDetailText>
            </>) : null}
            {(application.portfolioUrl) ? (<>
              <Text style={styles.minorHeading}>Portfolio</Text>
              <ApplicationDetailText>{application.portfolioUrl}</ApplicationDetailText>
            </>) : null}
          </ApplicationDetailCard>
        ) : null}

        {application.answers.length > 0 && (
          <ApplicationDetailCard label="Your answers">
            {application.answers.map((answer, index) => (
              <View key={answer.questionId} style={[styles.answer, index > 0 && styles.answerDivider]}>
                <View style={styles.questionRow}>
                  <Text style={styles.question}>{answer.questionText}</Text>
                  <Text style={styles.required}>{answer.required ? 'Required' : 'Optional'}</Text>
                </View>
                <ApplicationDetailText muted>{answer.answer || 'Not provided'}</ApplicationDetailText>
              </View>
            ))}
          </ApplicationDetailCard>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

export default function ApplicationDetailsRoute() {
  const { id } = useLocalSearchParams<{ id: string }>();
  return <OnboardingGuard>{id ? <ApplicationDetailsScreen applicationId={id} /> : null}</OnboardingGuard>;
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: PathoraColors.teal700 },
  content: { flexGrow: 1, paddingHorizontal: 24, paddingTop: 12, paddingBottom: 158, gap: 16 },
  backButton: { alignSelf: 'flex-start', marginLeft: -5 },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center', paddingHorizontal: 24, gap: 14 },
  stateTitle: { color: PathoraColors.ink900, fontFamily: PathoraFonts.en.extraBold, fontSize: 18, textAlign: 'center' },
  stateBody: { color: PathoraColors.textSecondary, fontFamily: PathoraFonts.en.medium, fontSize: 13, lineHeight: 21, textAlign: 'center' },
  retry: { minWidth: 160 },
  profileCard: { borderRadius: 20, borderWidth: 1, borderColor: PathoraColors.border,
    padding: 19, gap: 17, backgroundColor: PathoraColors.surface },
  profileTop: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  avatar: { width: 56, height: 56, borderRadius: 17, backgroundColor: PathoraColors.teal100,
    alignItems: 'center', justifyContent: 'center' },
  avatarText: { fontFamily: PathoraFonts.en.extraBold, fontSize: 20, color: PathoraColors.teal700 },
  profileIdentity: { flex: 1, gap: 5 },
  roleTitle: { fontFamily: PathoraFonts.en.extraBold, fontSize: 17, lineHeight: 23, color: PathoraColors.ink900 },
  companyName: { fontFamily: PathoraFonts.en.medium, fontSize: 12.5, color: PathoraColors.textSecondary },
  statusRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    gap: 8, borderTopWidth: 1, borderTopColor: '#F0EEE9', paddingTop: 13 },
  dateText: { color: PathoraColors.textSecondary, fontFamily: PathoraFonts.en.medium, fontSize: 11.5 },
  statusMessage: { flexDirection: 'row', alignItems: 'flex-start', gap: 9 },
  explanation: { flex: 1, color: PathoraColors.textSecondary, fontFamily: PathoraFonts.en.medium, fontSize: 13, lineHeight: 21 },
  withdrawButton: { marginTop: 13 },
  cvRow: { flexDirection: 'row', alignItems: 'center', gap: 11 },
  cvIcon: { width: 44, height: 44, borderRadius: 12, backgroundColor: PathoraColors.teal100,
    justifyContent: 'center', alignItems: 'center' },
  cvText: { flex: 1, gap: 4 },
  cvName: { color: PathoraColors.ink900, fontFamily: PathoraFonts.en.bold, fontSize: 13.5 },
  cvCaption: { color: PathoraColors.textSecondary, fontFamily: PathoraFonts.en.medium, fontSize: 11.5 },
  minorHeading: { color: PathoraColors.ink900, fontFamily: PathoraFonts.en.bold, fontSize: 12.5 },
  answer: { gap: 7 },
  answerDivider: { borderTopWidth: 1, borderTopColor: '#F0EEE9', paddingTop: 12, marginTop: 5 },
  questionRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 },
  question: { fontFamily: PathoraFonts.en.bold, color: PathoraColors.ink900, fontSize: 13, lineHeight: 20, flex: 1 },
  required: { fontFamily: PathoraFonts.en.bold, fontSize: 10, color: PathoraColors.textTertiary, marginTop: 2 },
});
