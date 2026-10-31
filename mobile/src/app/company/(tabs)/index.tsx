import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ApplicantPreviewRow } from '@/components/applicant-preview-row';
import { DashboardWelcomeHeader } from '@/components/dashboard-welcome-header';
import { PathoraButton } from '@/components/pathora-button';
import { PathoraLogo } from '@/components/pathora-logo';
import { PathoraMetricCard } from '@/components/pathora-metric-card';
import { PathoraSectionHeading } from '@/components/pathora-section-heading';
import { PathoraColors, PathoraFonts, PathoraRadius, PathoraSpacing } from '@/constants/pathora-theme';
import { useAuth } from '@/context/auth-context';
import {
  getCompanyInternships,
  getCompanyProfileOrNull,
  type CompanyInternship,
  type CompanyProfile,
} from '@/lib/api';
import { getAllCompanyApplications, type CompanyApplicantEntry } from '@/lib/company-applicants';
import { formatDisplayDate } from '@/lib/date';
import { initialsFor } from '@/lib/initials';
import { WORK_MODE_LABELS } from '@/lib/internship-options';
import { describeOnboardingError } from '@/lib/onboarding';

/** Keep dashboard previews compact. Full lists remain available in their own tabs. */
const PREVIEW_LIMIT = 2;

function companyInitials(companyName: string | null | undefined, email: string | undefined) {
  const name = companyName?.trim();
  if (!name) return initialsFor(null, null, email);
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0])
    .join('')
    .toUpperCase();
}

function ApplicantPreview({
  applications,
  isLoading,
  isUnavailable,
  onApplicantPress,
  emptyTitle,
  emptyDescription,
}: {
  applications: CompanyApplicantEntry[];
  isLoading: boolean;
  isUnavailable: boolean;
  onApplicantPress: (application: CompanyApplicantEntry) => void;
  emptyTitle: string;
  emptyDescription: string;
}) {
  if (isUnavailable) {
    return (
      <View style={[styles.whiteCard, styles.emptyCard]}>
        <View style={styles.emptyTextWrap}>
          <Text style={styles.emptyTitle}>Applicants unavailable</Text>
          <Text style={styles.emptyDescription}>Pull down to retry loading applications.</Text>
        </View>
      </View>
    );
  }

  if (isLoading) {
    return (
      <View style={[styles.whiteCard, styles.loadingCard]}>
        <ActivityIndicator color={PathoraColors.teal600} />
        <Text style={styles.loadingCardLabel}>Loading applicants…</Text>
      </View>
    );
  }

  if (applications.length === 0) {
    return (
      <View style={[styles.whiteCard, styles.emptyCard]}>
        <View style={styles.emptyIconCircle}>
          <Text style={styles.emptyIcon}>✓</Text>
        </View>
        <View style={styles.emptyTextWrap}>
          <Text style={styles.emptyTitle}>{emptyTitle}</Text>
          <Text style={styles.emptyDescription}>{emptyDescription}</Text>
        </View>
      </View>
    );
  }

  return (
    <View style={styles.applicantsCard}>
      {applications.slice(0, PREVIEW_LIMIT).map((application, index) => (
        <View
          key={application.applicationId}
          style={index !== 0 ? styles.applicantDivider : undefined}>
          <ApplicantPreviewRow
            applicantName={application.applicantName}
            internshipTitle={application.internshipTitle}
            appliedAt={application.appliedAt}
            status={application.status}
            tintIndex={index}
            onPress={() => onApplicantPress(application)}
          />
        </View>
      ))}
    </View>
  );
}

function InternshipPreview({
  internship,
  onPress,
}: {
  internship: CompanyInternship;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`View internship ${internship.title}`}
      style={({ pressed }) => [styles.internshipRow, pressed && styles.pressed]}>
      <View style={styles.roleIconBox}>
        <PathoraLogo variant="mark" size={23} />
      </View>
      <View style={styles.internshipInfo}>
        <Text style={styles.internshipTitle} numberOfLines={1}>{internship.title}</Text>
        <Text style={styles.internshipMeta} numberOfLines={1}>
          {WORK_MODE_LABELS[internship.workMode]} · {internship.location}
        </Text>
        <Text style={styles.internshipDeadline} numberOfLines={1}>
          Apply by {formatDisplayDate(internship.applicationDeadline)}
        </Text>
      </View>
      <Text style={styles.chevron}>›</Text>
    </Pressable>
  );
}

/**
 * All dashboard numbers come from existing company endpoints. The backend currently supports
 * OPEN and CLOSED internships only; it does not support drafts, pauses, or interviews.
 * Applications are merged client-side from the per-internship endpoint. This existing helper
 * is best-effort: individual failed internship requests can cause an undercount.
 */
function CompanyHomeScreen() {
  const router = useRouter();
  const { user, token } = useAuth();

  const [internships, setInternships] = useState<CompanyInternship[] | null>(null);
  const [companyProfile, setCompanyProfile] = useState<CompanyProfile | null>(null);
  const [applications, setApplications] = useState<CompanyApplicantEntry[] | null>(null);
  const [applicantsUnavailable, setApplicantsUnavailable] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    if (!token) return;

    let internshipsData: CompanyInternship[];
    try {
      internshipsData = await getCompanyInternships(token);
      setInternships(internshipsData);
      setError(null);
    } catch (loadError) {
      setError(describeOnboardingError(loadError));
      return;
    }

    // Secondary requests should not prevent the company from viewing its active listings.
    const [profileResult, applicationsResult] = await Promise.all([
      getCompanyProfileOrNull(token).catch(() => null),
      getAllCompanyApplications(token, internshipsData).catch(() => null),
    ]);
    setCompanyProfile(profileResult);
    setApplications(applicationsResult);
    setApplicantsUnavailable(applicationsResult === null);
  }, [token]);

  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );

  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      await load();
    } finally {
      setRefreshing(false);
    }
  };

  const handleAvatarPress = () => router.push('/company/profile');

  const goToApplicantDetail = (application: CompanyApplicantEntry) => {
    router.push({
      pathname: '/company/applicants/[applicationId]',
      params: { id: application.internshipId, applicationId: application.applicationId },
    });
  };

  if (internships === null && !error) {
    return (
      <SafeAreaView style={styles.safeArea} edges={['top']}>
        <View style={[styles.centerContent, styles.fallbackContent]}>
          <PathoraLogo size={34} wordmarkSize={21} />
          <ActivityIndicator color={PathoraColors.teal600} style={styles.startupSpinner} />
          <Text style={styles.loadingCardLabel}>Preparing your company workspace…</Text>
        </View>
      </SafeAreaView>
    );
  }

  if (error) {
    return (
      <SafeAreaView style={styles.safeArea} edges={['top']}>
        <View style={[styles.centerContent, styles.fallbackContent]}>
          <Text style={styles.errorTitle}>We could not load your dashboard</Text>
          <Text style={styles.errorText}>{error}</Text>
          <PathoraButton label="Try again" variant="secondary" onPress={() => void load()} style={styles.retryButton} />
        </View>
      </SafeAreaView>
    );
  }

  const openInternships = (internships ?? [])
    .filter((internship) => internship.status === 'OPEN')
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const reviewApplications = (applications ?? []).filter((application) => application.status === 'APPLIED');
  const companyName = companyProfile?.companyName?.trim() || 'Your company';

  return (
    <SafeAreaView style={styles.safeArea} edges={['top']}>
      <ScrollView
        style={styles.scrollView}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={handleRefresh}
            tintColor={PathoraColors.teal600}
            colors={[PathoraColors.teal600]}
          />
        }>
        <DashboardWelcomeHeader
          workspaceLabel="COMPANY WORKSPACE"
          name={companyName}
          subtitle="Here's what's happening with your hiring."
          avatarInitials={companyInitials(companyProfile?.companyName, user?.email)}
          avatarAccessibilityLabel="Open company profile"
          onAvatarPress={handleAvatarPress}
        />

        <View style={styles.section}>
          <PathoraSectionHeading eyebrow="AT A GLANCE" title="Your hiring pulse" />
          <View style={styles.metricsRow}>
            <PathoraMetricCard label="Open roles" value={openInternships.length} onPress={() => router.push('/company/internships')} />
            <PathoraMetricCard
              label="To review"
              value={applications ? reviewApplications.length : null}
              tone={reviewApplications.length > 0 ? 'amber' : 'teal'}
              unavailable={applicantsUnavailable}
              onPress={() => router.push({ pathname: '/company/applicants', params: { status: 'APPLIED' } })}
            />
            <PathoraMetricCard label="Applicants" value={applications?.length ?? null} unavailable={applicantsUnavailable} onPress={() => router.push('/company/applicants')} />
          </View>
        </View>

        <View style={styles.section}>
          <PathoraSectionHeading
            eyebrow="STAY ON TOP"
            title="Needs your attention"
            actionLabel="View all"
            onAction={() => router.push({ pathname: '/company/applicants', params: { status: 'APPLIED' } })}
          />
          <ApplicantPreview
            applications={reviewApplications}
            isLoading={applications === null && !applicantsUnavailable}
            isUnavailable={applicantsUnavailable}
            emptyTitle="You're all caught up"
            emptyDescription="New applications waiting for review will appear here."
            onApplicantPress={goToApplicantDetail}
          />
        </View>

        <View style={styles.section}>
          <PathoraSectionHeading
            eyebrow="YOUR OPPORTUNITIES"
            title="Open internships"
            actionLabel="View all"
            onAction={() => router.push('/company/internships')}
          />
          {openInternships.length === 0 ? (
            <View style={[styles.whiteCard, styles.emptyInternshipsCard]}>
              <Text style={styles.emptyTitle}>Your first opportunity starts here</Text>
              <Text style={styles.emptyDescription}>
                Publish an internship to make your company visible to students.
              </Text>
              <Pressable
                onPress={() => router.push('/company/internships/new')}
                accessibilityRole="button"
                style={({ pressed }) => [styles.textActionTouch, pressed && styles.pressed]}>
                <Text style={styles.inlineAction}>Create an internship  →</Text>
              </Pressable>
            </View>
          ) : (
            <View style={styles.internshipCard}>
              {openInternships.slice(0, PREVIEW_LIMIT).map((internship, index) => (
                <View key={internship.id} style={index > 0 ? styles.applicantDivider : undefined}>
                  <InternshipPreview
                    internship={internship}
                    onPress={() => router.push({ pathname: '/company/internships/[id]', params: { id: internship.id } })}
                  />
                </View>
              ))}
            </View>
          )}
        </View>


      </ScrollView>
    </SafeAreaView>
  );
}

export default function CompanyHomeRoute() {
  return <CompanyHomeScreen />;
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: PathoraColors.teal700 },
  scrollView: { backgroundColor: PathoraColors.paper },
  scrollContent: {
    flexGrow: 1,
    paddingHorizontal: PathoraSpacing[24],
    paddingTop: PathoraSpacing[8],
    paddingBottom: 130,
    gap: PathoraSpacing[20],
  },
  centerContent: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: PathoraSpacing[24],
    gap: PathoraSpacing[12],
  },
  startupSpinner: { marginTop: PathoraSpacing[16] },
  fallbackContent: { backgroundColor: PathoraColors.paper },
  pressed: { opacity: 0.72 },
  section: { gap: PathoraSpacing[12] },
  metricsRow: { flexDirection: 'row', gap: PathoraSpacing[8] },
  whiteCard: {
    borderRadius: PathoraRadius[14],
    backgroundColor: PathoraColors.surface,
    borderColor: PathoraColors.border,
    borderWidth: 1,
  },
  loadingCard: {
    paddingVertical: PathoraSpacing[24],
    paddingHorizontal: PathoraSpacing[20],
    alignItems: 'center',
    gap: PathoraSpacing[8],
  },
  loadingCardLabel: { fontFamily: PathoraFonts.en.medium, color: PathoraColors.textSecondary, fontSize: 12.5, textAlign: 'center' },
  applicantsCard: {
    backgroundColor: PathoraColors.surface,
    borderColor: PathoraColors.border,
    borderWidth: 1,
    borderRadius: PathoraRadius[14],
    paddingVertical: PathoraSpacing[4],
    paddingHorizontal: PathoraSpacing[16],
  },
  applicantDivider: { borderTopWidth: 1, borderTopColor: PathoraColors.border },
  emptyCard: {
    padding: PathoraSpacing[16],
    flexDirection: 'row',
    alignItems: 'center',
    gap: PathoraSpacing[12],
  },
  emptyIconCircle: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: PathoraColors.teal100,
  },
  emptyIcon: { fontFamily: PathoraFonts.en.extraBold, fontSize: 20, color: PathoraColors.teal700 },
  emptyTextWrap: { flex: 1, gap: 3 },
  emptyTitle: { fontFamily: PathoraFonts.en.bold, fontSize: 13.5, color: PathoraColors.ink900 },
  emptyDescription: { fontFamily: PathoraFonts.en.regular, fontSize: 12.5, lineHeight: 18, color: PathoraColors.textSecondary },
  emptyInternshipsCard: { padding: PathoraSpacing[20], gap: PathoraSpacing[8] },
  textActionTouch: { alignSelf: 'flex-start', paddingVertical: 5 },
  inlineAction: { fontFamily: PathoraFonts.en.bold, color: PathoraColors.teal600, fontSize: 12.5 },
  internshipCard: {
    backgroundColor: PathoraColors.surface,
    borderWidth: 1,
    borderColor: PathoraColors.border,
    borderRadius: PathoraRadius[14],
    paddingHorizontal: PathoraSpacing[16],
    paddingVertical: PathoraSpacing[4],
  },
  internshipRow: { flexDirection: 'row', alignItems: 'center', gap: PathoraSpacing[12], paddingVertical: PathoraSpacing[16] },
  roleIconBox: { width: 41, height: 41, borderRadius: PathoraRadius[12], backgroundColor: PathoraColors.teal100, alignItems: 'center', justifyContent: 'center' },
  internshipInfo: { flex: 1, gap: 3 },
  internshipTitle: { fontFamily: PathoraFonts.en.bold, fontSize: 14, color: PathoraColors.ink900 },
  internshipMeta: { fontFamily: PathoraFonts.en.medium, color: PathoraColors.textSecondary, fontSize: 12 },
  internshipDeadline: { fontFamily: PathoraFonts.en.medium, color: PathoraColors.teal600, fontSize: 11.5 },
  chevron: { fontFamily: PathoraFonts.en.medium, fontSize: 26, color: PathoraColors.textTertiary },
  errorTitle: { textAlign: 'center', fontFamily: PathoraFonts.en.bold, fontSize: 18, color: PathoraColors.ink900 },
  errorText: { textAlign: 'center', fontFamily: PathoraFonts.en.medium, fontSize: 13.5, color: PathoraColors.error },
  retryButton: { maxWidth: 170, marginTop: PathoraSpacing[12] },
});
