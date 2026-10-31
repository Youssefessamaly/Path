import { useRouter, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Alert, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { DashboardWelcomeHeader } from '@/components/dashboard-welcome-header';
import { InternshipFeedCard } from '@/components/internship-feed-card';
import { PathoraMetricCard } from '@/components/pathora-metric-card';
import { PathoraSectionHeading } from '@/components/pathora-section-heading';
import { PathoraButton } from '@/components/pathora-button';
import { PathoraSearchBar } from '@/components/pathora-search-bar';
import { ProfileStrengthCard } from '@/components/profile-strength-card';
import { PathoraColors, PathoraFonts, PathoraRadius, PathoraSpacing } from '@/constants/pathora-theme';
import { useAuth } from '@/context/auth-context';
import {
  ApiError,
  favoriteInternship,
  getInternApplications,
  getInternInternships,
  getInternProfileOrNull,
  type InternInternship,
  type InternProfile,
  unfavoriteInternship,
} from '@/lib/api';
import { initialsFor } from '@/lib/initials';
import { describeOnboardingError, resolveFirstIncompleteOnboardingStep } from '@/lib/onboarding';
import { calculateProfileStrength } from '@/lib/profile-strength';

const RECOMMENDED_PREVIEW_COUNT = 3;

function InternHomeScreen() {
  const router = useRouter();
  const { user, token } = useAuth();

  const [internships, setInternships] = useState<InternInternship[] | null>(null);
  const [profile, setProfile] = useState<InternProfile | null>(null);
  const [applicationsCount, setApplicationsCount] = useState(0);
  const [acceptedCount, setAcceptedCount] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [updatingFavoriteIds, setUpdatingFavoriteIds] = useState<Set<string>>(new Set());

  const load = useCallback(async () => {
    if (!token) {
      return;
    }
    try {
      const data = await getInternInternships(token);
      setInternships(data);
      setError(null);
    } catch (loadError) {
      if (loadError instanceof ApiError && loadError.status === 403) {
        // Profile missing or onboarding incomplete — don't show a raw feed error,
        // send them to resume onboarding instead.
        const incompleteProfile = await getInternProfileOrNull(token).catch(() => null);
        const nextStep = resolveFirstIncompleteOnboardingStep(incompleteProfile);
        router.replace(nextStep ?? '/onboarding/personal');
        return;
      }
      setError(describeOnboardingError(loadError));
      return;
    }

    // Best-effort secondary data for the greeting/profile-strength/stats — never blocks the
    // feed (the one piece of this screen the intern actually came here for).
    const [profileResult, applicationsResult] = await Promise.allSettled([
      getInternProfileOrNull(token),
      getInternApplications(token),
    ]);
    setProfile(profileResult.status === 'fulfilled' ? profileResult.value : null);
    setApplicationsCount(applicationsResult.status === 'fulfilled' ? applicationsResult.value.length : 0);
    setAcceptedCount(applicationsResult.status === 'fulfilled'
      ? applicationsResult.value.filter((application) => application.status === 'ACCEPTED').length : 0);
  }, [token, router]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  const handleRefresh = async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  };

  const handleSearchPress = () => {
    router.push('/intern/internships');
  };

  const handleSeeAllPress = () => {
    router.push('/intern/internships');
  };

  const handleProfileStrengthPress = () => {
    const nextStep = resolveFirstIncompleteOnboardingStep(profile);
    router.push(nextStep ?? '/intern/profile');
  };

  const handleFavoritePress = async (internship: InternInternship) => {
    if (!token || updatingFavoriteIds.has(internship.id)) {
      return;
    }

    setUpdatingFavoriteIds((current) => new Set(current).add(internship.id));
    try {
      if (internship.favorited) {
        await unfavoriteInternship(token, internship.id);
      } else {
        await favoriteInternship(token, internship.id);
      }

      setInternships((current) =>
        current?.map((item) =>
          item.id === internship.id ? { ...item, favorited: !internship.favorited } : item,
        ) ?? null,
      );
    } catch {
      Alert.alert('Unable to update favorite', 'Please try again.');
    } finally {
      setUpdatingFavoriteIds((current) => {
        const next = new Set(current);
        next.delete(internship.id);
        return next;
      });
    }
  };

  const firstName = profile?.firstName?.trim();
  const strength = profile ? calculateProfileStrength(profile) : null;
  const savedCount = internships?.filter((internship) => internship.favorited).length ?? 0;

  return (
    <SafeAreaView style={styles.safeArea} edges={['top']}>
      {internships === null && !error && (
        <View style={[styles.centerContent, styles.fallbackContent]}>
          <ActivityIndicator color={PathoraColors.teal600} />
        </View>
      )}

      {error && (
        <View style={[styles.centerContent, styles.fallbackContent]}>
          <Text style={styles.errorText}>{error}</Text>
          <PathoraButton label="Try again" variant="secondary" onPress={load} style={styles.retryButton} />
        </View>
      )}

      {internships !== null && !error && (
        <ScrollView
          style={styles.scrollView}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />}>
          <DashboardWelcomeHeader
            workspaceLabel="STUDENT WORKSPACE"
            name={firstName || 'there'}
            subtitle="Let's find your next opportunity."
            avatarInitials={initialsFor(profile?.firstName ?? null, profile?.lastName ?? null, user?.email)}
            avatarAccessibilityLabel="Open your profile"
            onAvatarPress={() => router.push('/intern/profile')}
          />

          <PathoraSearchBar
            placeholder="Search internships"
            onPress={handleSearchPress}
            style={styles.searchBar}
          />

          {strength && strength.percentage < 100 && (
            <ProfileStrengthCard
              percentage={strength.percentage}
              isOnboardingComplete={strength.isOnboardingComplete}
              onActionPress={handleProfileStrengthPress}
            />
          )}

          <View style={styles.metricsRow}>
            <PathoraMetricCard
              label="Applications"
              value={applicationsCount}
              onPress={() => router.push('/intern/applications')}
            />
            <PathoraMetricCard
              label="Saved"
              value={savedCount}
              onPress={() => router.push('/intern/favorites')}
            />
            <PathoraMetricCard
              label="Accepted"
              value={acceptedCount}
              onPress={() => router.push('/intern/applications')}
            />
          </View>

          <PathoraSectionHeading
            eyebrow="YOUR OPPORTUNITIES"
            title="Recommended for you"
            actionLabel={internships.length > 0 ? 'See all' : undefined}
            onAction={handleSeeAllPress}
          />

          {internships.length === 0 ? (
            <View style={styles.emptyState}>
              <Text style={styles.emptyTitle}>No open internships yet</Text>
              <Text style={styles.emptyBody}>
                New opportunities will appear here when companies post them.
              </Text>
            </View>
          ) : (
            internships.slice(0, RECOMMENDED_PREVIEW_COUNT).map((internship) => (
              <InternshipFeedCard
                key={internship.id}
                item={internship}
                onPress={() =>
                  router.push({ pathname: '/intern/internships/[id]', params: { id: internship.id } })
                }
                onFavoritePress={() => handleFavoritePress(internship)}
                favoriteDisabled={updatingFavoriteIds.has(internship.id)}
              />
            ))
          )}
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

export default function InternHomeRoute() {
  return <InternHomeScreen />;
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: PathoraColors.teal700,
  },
  scrollView: { backgroundColor: PathoraColors.paper },
  scrollContent: {
    flexGrow: 1,
    paddingHorizontal: PathoraSpacing[24],
    paddingTop: 8,
    paddingBottom: 130,
    gap: PathoraSpacing[20],
  },
  searchBar: {
    marginTop: -4,
  },
  fallbackContent: { backgroundColor: PathoraColors.paper },
  centerContent: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: PathoraSpacing[24],
    gap: PathoraSpacing[16],
  },
  errorText: {
    fontFamily: PathoraFonts.en.medium,
    fontSize: 14,
    color: PathoraColors.error,
    textAlign: 'center',
  },
  retryButton: {
    minWidth: 140,
  },
  metricsRow: { flexDirection: 'row', gap: PathoraSpacing[8] },
  emptyState: {
    borderRadius: PathoraRadius[14],
    backgroundColor: PathoraColors.surface,
    borderWidth: 1,
    borderColor: PathoraColors.border,
    paddingVertical: PathoraSpacing[24],
    paddingHorizontal: PathoraSpacing[20],
    alignItems: 'center',
    gap: 6,
  },
  emptyTitle: {
    fontFamily: PathoraFonts.en.extraBold,
    fontSize: 16,
    color: PathoraColors.ink900,
    textAlign: 'center',
  },
  emptyBody: {
    fontFamily: PathoraFonts.en.regular,
    fontSize: 13.5,
    lineHeight: 19,
    color: PathoraColors.textSecondary,
    textAlign: 'center',
  },
});
