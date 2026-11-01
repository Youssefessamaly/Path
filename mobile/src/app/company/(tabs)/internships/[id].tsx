import { useLocalSearchParams, useFocusEffect, useRouter } from 'expo-router';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { useCallback, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { PathoraDetailHeader } from '@/components/pathora-detail-header';
import { OnboardingGuard } from '@/components/onboarding/onboarding-guard';
import { PathoraButton } from '@/components/pathora-button';
import { PathoraSectionHeading } from '@/components/pathora-section-heading';
import { PathoraColors as C, PathoraFonts as F } from '@/constants/pathora-theme';
import { useAuth } from '@/context/auth-context';
import { closeCompanyInternship, getCompanyInternship, type CompanyInternship } from '@/lib/api';
import { formatDisplayDate } from '@/lib/date';
import {
  APPLICATION_METHOD_LABELS,
  formatMinimumAcademicYearLabel,
  WORK_MODE_LABELS,
} from '@/lib/internship-options';
import { describeOnboardingError } from '@/lib/onboarding';

type SymbolName = SymbolViewProps['name'];

function Icon({ name, size = 19, color = C.teal700 }: {
  name: SymbolName;
  size?: number;
  color?: string;
}) {
  return <SymbolView name={name} size={size} tintColor={color} />;
}

function InfoRow({ label, value, last = false }: { label: string; value: string; last?: boolean }) {
  return (
    <View style={[styles.infoRow, !last && styles.infoRowBorder]}>
      <Text style={styles.infoLabel}>{label}</Text>
      <Text style={styles.infoValue} selectable={false}>{value}</Text>
    </View>
  );
}

function Chip({ label }: { label: string }) {
  return (
    <View style={styles.chip}>
      <Text style={styles.chipText}>{label}</Text>
    </View>
  );
}

function ActionTile({ icon, title, caption, onPress }: {
  icon: SymbolName;
  title: string;
  caption: string;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      onPress={onPress}
      style={({ pressed }) => [styles.actionTile, pressed && styles.pressed]}>
      <View style={styles.tileIcon}>
        <Icon name={icon} size={21} />
      </View>
      <Text style={styles.actionTileTitle}>{title}</Text>
      <Text style={styles.actionTileCaption}>{caption}</Text>
    </Pressable>
  );
}

function InternshipDetailsScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const { token } = useAuth();

  const [internship, setInternship] = useState<CompanyInternship | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isClosing, setIsClosing] = useState(false);

  const load = useCallback(async () => {
    if (!token || !id) {
      return;
    }
    try {
      const data = await getCompanyInternship(token, id);
      setInternship(data);
      setError(null);
    } catch (loadError) {
      setError(describeOnboardingError(loadError));
    }
  }, [token, id]);

  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );

  const handleClose = () => {
    Alert.alert('Close this internship?', 'Interns will no longer be able to see or apply to it.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Close internship',
        style: 'destructive',
        onPress: async () => {
          if (!token || !id) {
            return;
          }
          setIsClosing(true);
          try {
            const updated = await closeCompanyInternship(token, id);
            setInternship(updated);
          } catch (closeError) {
            Alert.alert('Something went wrong', describeOnboardingError(closeError));
          } finally {
            setIsClosing(false);
          }
        },
      },
    ]);
  };

  const openEdit = () => {
    if (!internship) return;
    router.push({ pathname: '/company/internships/[id]/edit', params: { id: internship.id } });
  };

  const openApplicants = () => {
    if (!internship) return;
    router.push({ pathname: '/company/internships/[id]/applicants', params: { id: internship.id } });
  };

  const openQuestions = () => {
    if (!internship) return;
    router.push({ pathname: '/company/internships/[id]/application-questions', params: { id: internship.id } });
  };

  const isOpen = internship?.status === 'OPEN';
  const isInternal = internship?.applicationMethod === 'INTERNAL';

  return (
    <View style={styles.screen}>
      <SafeAreaView style={styles.topSafeArea} edges={['top']}>
        <ScrollView
          style={styles.scrollView}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}>
          <PathoraDetailHeader
            topInset={0}
            onBack={() => router.back()}
            eyebrow="COMPANY WORKSPACE"
            title="Internship details"
          />

          {error ? (
            <View style={styles.feedbackCard}>
              <Text style={styles.feedbackTitle}>Unable to load internship</Text>
              <Text style={styles.feedbackBody}>{error}</Text>
              <PathoraButton label="Try again" variant="secondary" onPress={() => void load()} />
            </View>
          ) : !internship ? (
            <View style={styles.loadingCard}>
              <ActivityIndicator color={C.teal600} size="large" />
              <Text style={styles.feedbackBody}>Loading internship details…</Text>
            </View>
          ) : (
            <>
              {/* Summary stays separate from the teal header to give the title room to breathe. */}
              <View style={styles.summaryCard}>
                <View style={styles.summaryTitleRow}>
                  <Text style={styles.internshipTitle}>{internship.title}</Text>
                  <View style={[styles.statusBadge, isOpen ? styles.statusOpen : styles.statusClosed]}>
                    <View style={[styles.statusDot, !isOpen && styles.statusDotClosed]} />
                    <Text style={[styles.statusText, !isOpen && styles.statusTextClosed]}>
                      {isOpen ? 'OPEN' : 'CLOSED'}
                    </Text>
                  </View>
                </View>
                <Text style={styles.companyName}>{internship.companyName}</Text>
                <View style={styles.divider} />
                <Text style={styles.summaryEyebrow}>ABOUT THE ROLE</Text>
                <Text style={styles.description}>{internship.description}</Text>
              </View>

              <View style={styles.section}>
                <PathoraSectionHeading eyebrow="ROLE INFORMATION" title="At a glance" />
                <View style={styles.infoCard}>
                  <InfoRow label="Location" value={internship.location} />
                  <InfoRow label="Work mode" value={WORK_MODE_LABELS[internship.workMode]} />
                  <InfoRow label="Apply by" value={formatDisplayDate(internship.applicationDeadline)} />
                  {internship.startDate ? (
                    <InfoRow label="Start date" value={formatDisplayDate(internship.startDate)} />
                  ) : null}
                  {internship.durationMonths ? (
                    <InfoRow
                      label="Duration"
                      value={`${internship.durationMonths} month${internship.durationMonths === 1 ? '' : 's'}`}
                    />
                  ) : null}
                  {internship.minimumAcademicYear !== null ? (
                    <InfoRow
                      label="Minimum academic year"
                      value={formatMinimumAcademicYearLabel(internship.minimumAcademicYear)}
                    />
                  ) : null}
                  <InfoRow
                    label="Application method"
                    value={APPLICATION_METHOD_LABELS[internship.applicationMethod]}
                    last={!internship.externalApplicationUrl || internship.applicationMethod !== 'EXTERNAL'}
                  />
                  {internship.applicationMethod === 'EXTERNAL' && internship.externalApplicationUrl ? (
                    <InfoRow label="Application URL" value={internship.externalApplicationUrl} last />
                  ) : null}
                </View>
              </View>

              <View style={styles.section}>
                <PathoraSectionHeading eyebrow="CANDIDATE FIT" title="Requirements" />
                <View style={styles.requirementsCard}>
                  <Text style={styles.requirementLabel}>Required skills</Text>
                  {internship.skills.length > 0 ? (
                    <View style={styles.chipsWrap}>
                      {internship.skills.map((skill) => <Chip key={skill.id} label={skill.name} />)}
                    </View>
                  ) : (
                    <Text style={styles.emptyRequirement}>No specific skills listed.</Text>
                  )}
                  {internship.targetMajors.length > 0 ? (
                    <>
                      <View style={styles.divider} />
                      <Text style={styles.requirementLabel}>Target majors</Text>
                      <View style={styles.chipsWrap}>
                        {internship.targetMajors.map((major) => <Chip key={major} label={major} />)}
                      </View>
                    </>
                  ) : null}
                </View>
              </View>

              <View style={styles.section}>
                <PathoraSectionHeading eyebrow="YOUR WORKSPACE" title="Manage internship" />
                <View style={styles.manageCard}>
                  {isInternal ? (
                    <>
                      <Pressable
                        accessibilityRole="button"
                        accessibilityLabel="Review applicants"
                        onPress={openApplicants}
                        style={({ pressed }) => [styles.primaryAction, pressed && styles.primaryPressed]}>
                        <View style={styles.primaryIcon}>
                          <Icon name={{ ios: 'person.2.fill', android: 'group', web: 'group' }} color={C.surface} size={21} />
                        </View>
                        <View style={styles.primaryActionCopy}>
                          <Text style={styles.primaryTitle}>Review applicants</Text>
                          <Text style={styles.primaryCaption}>See who has applied</Text>
                        </View>
                        <Icon name={{ ios: 'chevron.right', android: 'chevron_right', web: 'chevron_right' }} color={C.surface} size={17} />
                      </Pressable>
                      <View style={styles.actionGrid}>
                        <ActionTile
                          icon={{ ios: 'square.and.pencil', android: 'edit', web: 'edit' }}
                          title="Edit listing"
                          caption="Update details"
                          onPress={openEdit}
                        />
                        <ActionTile
                          icon={{ ios: 'list.bullet.rectangle', android: 'list_alt', web: 'list_alt' }}
                          title="Questions"
                          caption="Manage the form"
                          onPress={openQuestions}
                        />
                      </View>
                    </>
                  ) : (
                    <Pressable
                      accessibilityRole="button"
                      accessibilityLabel="Edit internship"
                      onPress={openEdit}
                      style={({ pressed }) => [styles.primaryAction, pressed && styles.primaryPressed]}>
                      <View style={styles.primaryIcon}>
                        <Icon name={{ ios: 'square.and.pencil', android: 'edit', web: 'edit' }} color={C.surface} size={21} />
                      </View>
                      <View style={styles.primaryActionCopy}>
                        <Text style={styles.primaryTitle}>Edit internship</Text>
                        <Text style={styles.primaryCaption}>Update your listing</Text>
                      </View>
                      <Icon name={{ ios: 'chevron.right', android: 'chevron_right', web: 'chevron_right' }} color={C.surface} size={17} />
                    </Pressable>
                  )}

                  {isOpen ? (
                    <View style={styles.closeArea}>
                      <Pressable
                        accessibilityRole="button"
                        accessibilityLabel="Close internship"
                        accessibilityState={{ disabled: isClosing }}
                        disabled={isClosing}
                        onPress={handleClose}
                        style={({ pressed }) => [styles.closeAction, pressed && styles.pressed, isClosing && styles.disabled]}>
                        <Icon name={{ ios: 'xmark.circle', android: 'cancel', web: 'cancel' }} color={C.error} size={18} />
                        <Text style={styles.closeText}>{isClosing ? 'Closing…' : 'Close internship'}</Text>
                      </Pressable>
                      <Text style={styles.closeHint}>This removes the opportunity from new applications.</Text>
                    </View>
                  ) : null}
                </View>
              </View>
            </>
          )}
        </ScrollView>
      </SafeAreaView>
      {/* Bottom safe area should match the warm paper background, not the teal header. */}
      <SafeAreaView edges={['bottom']} style={styles.bottomSafeArea} />
    </View>
  );
}

export default function InternshipDetailsRoute() {
  return (
    <OnboardingGuard role="COMPANY">
      <InternshipDetailsScreen />
    </OnboardingGuard>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.paper },
  topSafeArea: { flex: 1, backgroundColor: C.teal700 },
  bottomSafeArea: { backgroundColor: C.paper },
  scrollView: { flex: 1, backgroundColor: C.paper },
  // Bottom breathing room keeps the last action fully above the floating company tab bar.
  scrollContent: { flexGrow: 1, paddingHorizontal: 24, paddingBottom: 175, gap: 20 },
  section: { gap: 12 },
  summaryCard: { backgroundColor: C.surface, borderWidth: 1, borderColor: C.border, borderRadius: 20, padding: 20, gap: 10 },
  summaryTitleRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 10, justifyContent: 'space-between' },
  internshipTitle: { flex: 1, minWidth: 0, fontFamily: F.en.extraBold, fontSize: 23, lineHeight: 31, color: C.ink900, letterSpacing: -0.4 },
  companyName: { color: C.textSecondary, fontFamily: F.en.medium, fontSize: 13.5 },
  statusBadge: { flexDirection: 'row', alignItems: 'center', gap: 6, borderRadius: 999, paddingHorizontal: 10, paddingVertical: 7, marginTop: 3 },
  statusOpen: { backgroundColor: C.teal100 },
  statusClosed: { backgroundColor: '#F1EFEB' },
  statusDot: { height: 6, width: 6, borderRadius: 3, backgroundColor: C.teal600 },
  statusDotClosed: { backgroundColor: C.textSecondary },
  statusText: { fontFamily: F.en.bold, fontSize: 10.5, color: C.teal700, letterSpacing: 0.5 },
  statusTextClosed: { color: C.textSecondary },
  divider: { height: 1, backgroundColor: C.border, marginVertical: 5 },
  summaryEyebrow: { color: C.teal600, fontFamily: F.en.bold, fontSize: 10.5, letterSpacing: 1.1 },
  description: { color: C.ink900, fontFamily: F.en.regular, fontSize: 14, lineHeight: 23 },
  infoCard: { backgroundColor: C.surface, borderWidth: 1, borderColor: C.border, borderRadius: 20, paddingHorizontal: 17, paddingVertical: 5 },
  infoRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16, paddingVertical: 13 },
  infoRowBorder: { borderBottomWidth: 1, borderBottomColor: C.border },
  infoLabel: { flex: 1, color: C.textSecondary, fontFamily: F.en.medium, fontSize: 12.5, lineHeight: 20 },
  infoValue: { flex: 1.15, textAlign: 'right', color: C.ink900, fontFamily: F.en.semiBold, fontSize: 12.5, lineHeight: 20 },
  requirementsCard: { backgroundColor: C.surface, borderWidth: 1, borderColor: C.border, borderRadius: 20, padding: 18, gap: 12 },
  requirementLabel: { color: C.ink900, fontFamily: F.en.bold, fontSize: 13 },
  emptyRequirement: { color: C.textSecondary, fontFamily: F.en.medium, fontSize: 12.5 },
  chipsWrap: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: { borderRadius: 999, paddingHorizontal: 12, paddingVertical: 8, backgroundColor: C.teal100 },
  chipText: { color: C.teal700, fontFamily: F.en.semiBold, fontSize: 12.5 },
  manageCard: { backgroundColor: C.surface, borderWidth: 1, borderColor: C.border, borderRadius: 20, padding: 16, gap: 12 },
  primaryAction: { flexDirection: 'row', alignItems: 'center', backgroundColor: C.teal600, borderRadius: 14, paddingHorizontal: 15, paddingVertical: 15, minHeight: 76, gap: 11 },
  primaryIcon: { height: 42, width: 42, borderRadius: 12, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.14)' },
  primaryActionCopy: { flex: 1, minWidth: 0, gap: 4 },
  primaryTitle: { color: C.surface, fontFamily: F.en.bold, fontSize: 14.5 },
  primaryCaption: { color: '#D1E9E4', fontFamily: F.en.medium, fontSize: 11.5 },
  actionGrid: { flexDirection: 'row', gap: 10 },
  actionTile: { flex: 1, minWidth: 0, alignItems: 'flex-start', borderWidth: 1, borderColor: C.border, backgroundColor: C.paper, borderRadius: 14, paddingHorizontal: 14, paddingVertical: 14, gap: 7, minHeight: 116 },
  tileIcon: { width: 34, height: 34, borderRadius: 10, backgroundColor: C.teal100, alignItems: 'center', justifyContent: 'center' },
  actionTileTitle: { fontFamily: F.en.bold, color: C.ink900, fontSize: 13 },
  actionTileCaption: { fontFamily: F.en.medium, color: C.textSecondary, fontSize: 11, lineHeight: 16 },
  closeArea: { borderTopWidth: 1, borderTopColor: C.border, marginTop: 3, paddingTop: 9, alignItems: 'center', gap: 1 },
  closeAction: { minHeight: 45, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, paddingHorizontal: 16 },
  closeText: { color: C.error, fontFamily: F.en.bold, fontSize: 12.5 },
  closeHint: { fontFamily: F.en.medium, color: C.textTertiary, fontSize: 10.5, textAlign: 'center', lineHeight: 16 },
  pressed: { opacity: 0.75 },
  primaryPressed: { opacity: 0.85 },
  disabled: { opacity: 0.5 },
  feedbackCard: { backgroundColor: C.surface, borderWidth: 1, borderColor: C.border, borderRadius: 20, padding: 22, gap: 14, alignItems: 'center' },
  feedbackTitle: { fontFamily: F.en.bold, color: C.ink900, fontSize: 17 },
  feedbackBody: { fontFamily: F.en.medium, color: C.textSecondary, fontSize: 13, textAlign: 'center', lineHeight: 20 },
  loadingCard: { minHeight: 240, alignItems: 'center', justifyContent: 'center', gap: 14 },
});
