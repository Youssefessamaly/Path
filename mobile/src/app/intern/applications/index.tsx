import { useFocusEffect, useRouter } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { useCallback, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Animated, { FadeInDown, ReduceMotion } from 'react-native-reanimated';

import { ApplicationFilter, ApplicationMetric } from '@/components/application-polish';
import { ApplicationStatusBadge } from '@/components/application-status-badge';
import { PathoraButton } from '@/components/pathora-button';
import { pathoraCardSurface, pathoraCardPressed } from '@/components/pathora-card-styles';
import { PathoraSearchInput } from '@/components/pathora-search-input';
import { PathoraPageHeader } from '@/components/pathora-page-header';
import { PathoraSectionHeading } from '@/components/pathora-section-heading';
import { PathoraColors, PathoraFonts } from '@/constants/pathora-theme';
import { useAuth } from '@/context/auth-context';
import { getInternApplications, type InternApplication, type InternApplicationStatus } from '@/lib/api';
import { formatDisplayDate } from '@/lib/date';
import { initialsForName } from '@/lib/initials';
import { describeOnboardingError } from '@/lib/onboarding';

type Filter = 'ALL' | InternApplicationStatus;
const FILTERS: { key: Filter; label: string }[] = [
  { key: 'ALL', label: 'All' },
  { key: 'APPLIED', label: 'Applied' },
  { key: 'ACCEPTED', label: 'Accepted' },
  { key: 'REJECTED', label: 'Rejected' },
];

function ApplicationCard({ application, index, onPress }: { application: InternApplication; index: number; onPress: () => void }) {
  const company = application.companyName ?? 'Company unavailable';
  return (
    <Animated.View entering={FadeInDown.duration(300).delay(Math.min(index, 7) * 35).reduceMotion(ReduceMotion.System)}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`View application for ${application.internshipTitle ?? 'internship'}, ${application.status.toLowerCase()}`}
        onPress={onPress}
        style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}>
        <View style={styles.cardTop}>
          <View style={styles.avatar}><Text style={styles.avatarText}>{initialsForName(company)}</Text></View>
          <View style={styles.cardIdentity}>
            <Text numberOfLines={2} style={styles.roleTitle}>{application.internshipTitle ?? 'Internship no longer available'}</Text>
            <Text numberOfLines={1} style={styles.companyName}>{company}</Text>
          </View>
          <ApplicationStatusBadge status={application.status} />
        </View>
        <View style={styles.cardBottom}>
          <View style={styles.dateRow}>
            <SymbolView name={{ ios: 'calendar', android: 'event', web: 'event' }} size={14} tintColor={PathoraColors.teal600} />
            <Text style={styles.dateText}>Applied {formatDisplayDate(application.appliedAt)}</Text>
          </View>
          <View style={styles.viewRow}>
            <Text style={styles.viewText}>Details</Text>
            <SymbolView name={{ ios: 'arrow.right', android: 'arrow_forward', web: 'arrow_forward' }} size={15} tintColor={PathoraColors.teal700} />
          </View>
        </View>
      </Pressable>
    </Animated.View>
  );
}

/** Only GET /intern/applications supplies the student application data and actual statuses. */
export default function ApplicationsRoute() {
  const router = useRouter();
  const { token } = useAuth();
  const requestId = useRef(0);
  const [applications, setApplications] = useState<InternApplication[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [filter, setFilter] = useState<Filter>('ALL');
  const [query, setQuery] = useState('');

  const load = useCallback(async () => {
    if (!token) return;
    const current = ++requestId.current;
    try {
      const data = await getInternApplications(token);
      if (current !== requestId.current) return;
      setApplications(data);
      setError(null);
    } catch (err) {
      if (current === requestId.current) setError(describeOnboardingError(err));
    }
  }, [token]);

  useFocusEffect(useCallback(() => {
    void load();
    return () => { requestId.current += 1; };
  }, [load]));

  const refresh = async () => {
    setRefreshing(true);
    try { await load(); } finally { setRefreshing(false); }
  };
  const all = applications ?? [];
  const counts = {
    ALL: all.length,
    APPLIED: all.filter((a) => a.status === 'APPLIED').length,
    ACCEPTED: all.filter((a) => a.status === 'ACCEPTED').length,
    REJECTED: all.filter((a) => a.status === 'REJECTED').length,
  };
  const visible = useMemo(() => {
    const search = query.trim().toLowerCase();
    return all.filter((application) =>
      (filter === 'ALL' || application.status === filter) &&
      (!search || [application.internshipTitle, application.companyName].some((value) => value?.toLowerCase().includes(search))),
    );
  }, [applications, filter, query]);

  return (
    <SafeAreaView style={styles.safeArea} edges={['top']}>
      <ScrollView
        style={{ backgroundColor: PathoraColors.paper }}
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void refresh()} tintColor={PathoraColors.teal600} />}>
        <PathoraPageHeader eyebrow="STUDENT WORKSPACE" title="My applications" subtitle="All your applications, in one place." />

        {applications === null && !error ? (
          <View style={styles.center}><ActivityIndicator size="large" color={PathoraColors.teal600} /></View>
        ) : error ? (
          <View style={styles.emptyCard}>
            <Text style={styles.emptyTitle}>We couldn't load your applications</Text>
            <Text style={styles.emptyBody}>{error}</Text>
            <PathoraButton label="Try again" variant="secondary" onPress={() => void load()} style={styles.retry} />
          </View>
        ) : (
          <>
            <PathoraSectionHeading eyebrow="AT A GLANCE" title="Your progress" />
            <View style={styles.metrics}>
              <ApplicationMetric label="Applications" value={counts.ALL} />
              <ApplicationMetric label="Awaiting review" value={counts.APPLIED} tint="amber" />
              <ApplicationMetric label="Accepted" value={counts.ACCEPTED} />
            </View>

            <PathoraSectionHeading eyebrow="APPLICATION HISTORY" title="Explore your submissions" />
            <PathoraSearchInput value={query} onChangeText={setQuery} placeholder="Search roles or companies" />
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filters}>
              {FILTERS.map((item) => <ApplicationFilter key={item.key} title={item.label}
                count={counts[item.key]} selected={filter === item.key} onPress={() => setFilter(item.key)} />)}
            </ScrollView>
            <View style={styles.resultsHead}>
              <Text style={styles.resultsText}>{visible.length} {visible.length === 1 ? 'application' : 'applications'}</Text>
              <Text style={styles.sortText}>Newest first</Text>
            </View>
            {visible.length === 0 ? (
              <View style={styles.emptyCard}>
                <View style={styles.emptyIcon}>
                  <SymbolView name={{ ios: 'doc.text', android: 'description', web: 'description' }} size={26} tintColor={PathoraColors.teal700} />
                </View>
                <Text style={styles.emptyTitle}>{all.length ? 'No matching applications' : 'Your journey starts with an application'}</Text>
                <Text style={styles.emptyBody}>{all.length ? 'Try another search or status filter.' : 'Once you apply to an internship through Path, it will appear here.'}</Text>
                {all.length > 0 && <Pressable onPress={() => { setQuery(''); setFilter('ALL'); }}><Text style={styles.clearText}>Clear filters →</Text></Pressable>}
              </View>
            ) : visible.map((application, index) => (
              <ApplicationCard key={application.applicationId} application={application} index={index}
                onPress={() => router.push({ pathname: '/intern/applications/[id]', params: { id: application.applicationId } })} />
            ))}
          </>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: PathoraColors.teal700 },
  content: { flexGrow: 1, paddingHorizontal: 24, paddingTop: 12, paddingBottom: 155, gap: 16 },
  metrics: { flexDirection: 'row', gap: 9, marginBottom: 6 },
  filters: { gap: 9, paddingVertical: 2 },
  resultsHead: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 3 },
  resultsText: { fontFamily: PathoraFonts.en.bold, color: PathoraColors.ink900, fontSize: 13 },
  sortText: { fontFamily: PathoraFonts.en.medium, color: PathoraColors.textTertiary, fontSize: 11.5 },
  card: { ...pathoraCardSurface, padding: 16, gap: 14 },
  cardPressed: { ...pathoraCardPressed },
  cardTop: { flexDirection: 'row', alignItems: 'flex-start', gap: 10 },
  avatar: { width: 46, height: 46, borderRadius: 15, alignItems: 'center', justifyContent: 'center', backgroundColor: PathoraColors.teal100 },
  avatarText: { color: PathoraColors.teal700, fontFamily: PathoraFonts.en.extraBold, fontSize: 16 },
  cardIdentity: { flex: 1, minWidth: 0, gap: 4 },
  roleTitle: { color: PathoraColors.ink900, fontFamily: PathoraFonts.en.bold, fontSize: 14.5, lineHeight: 20 },
  companyName: { color: PathoraColors.textSecondary, fontFamily: PathoraFonts.en.medium, fontSize: 12 },
  cardBottom: { borderTopWidth: 1, borderTopColor: '#F0EEE9', paddingTop: 12, flexDirection: 'row',
    justifyContent: 'space-between', alignItems: 'center', gap: 8 },
  dateRow: { flexDirection: 'row', alignItems: 'center', gap: 6, flexShrink: 1 },
  dateText: { color: PathoraColors.textSecondary, fontFamily: PathoraFonts.en.medium, fontSize: 11.5 },
  viewRow: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  viewText: { color: PathoraColors.teal700, fontFamily: PathoraFonts.en.bold, fontSize: 11.5 },
  center: { minHeight: 280, alignItems: 'center', justifyContent: 'center' },
  emptyCard: { borderRadius: 20, backgroundColor: PathoraColors.surface, borderColor: PathoraColors.border,
    borderWidth: 1, padding: 24, alignItems: 'center', gap: 12 },
  emptyIcon: { backgroundColor: PathoraColors.teal100, borderRadius: 20, height: 58, width: 58, alignItems: 'center', justifyContent: 'center' },
  emptyTitle: { color: PathoraColors.ink900, fontFamily: PathoraFonts.en.extraBold, fontSize: 16, textAlign: 'center' },
  emptyBody: { color: PathoraColors.textSecondary, fontFamily: PathoraFonts.en.medium, fontSize: 12.5, lineHeight: 20, textAlign: 'center' },
  clearText: { color: PathoraColors.teal700, fontFamily: PathoraFonts.en.bold, fontSize: 12.5 },
  retry: { minWidth: 160 },
});
