import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { PathoraDetailHeader } from '@/components/pathora-detail-header';
import { OnboardingGuard } from '@/components/onboarding/onboarding-guard';
import { OnboardingStepHeader } from '@/components/onboarding/onboarding-step-header';
import { PathoraOnboardingCard } from '@/components/pathora-onboarding-card';
import { PathoraInlineNotice } from '@/components/pathora-inline-notice';
import { PathoraButton } from '@/components/pathora-button';
import { PathoraInput } from '@/components/pathora-input';
import { PathoraColors, PathoraSpacing } from '@/constants/pathora-theme';
import { useAuth } from '@/context/auth-context';
import { getCompanyProfileOrNull, updateCompanyProfile } from '@/lib/api';
import { describeOnboardingError } from '@/lib/onboarding';

function CompanyDetailsScreen() {
  const router = useRouter();
  const { token } = useAuth();

  const [location, setLocation] = useState('');
  const [website, setWebsite] = useState('');
  const [description, setDescription] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      if (!token) {
        return;
      }
      try {
        const profile = await getCompanyProfileOrNull(token);
        if (cancelled || !profile) {
          return;
        }
        setLocation(profile.location ?? '');
        setWebsite(profile.website ?? '');
        setDescription(profile.description ?? '');
      } catch {
        // Best-effort prefill only — leave fields blank if it fails.
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [token]);

  const canContinue = location.trim().length > 0 && description.trim().length > 0;

  const handleBack = () => {
    router.replace('/company/onboarding/company');
  };

  const handleContinue = async () => {
    if (!canContinue || isSubmitting || !token) {
      return;
    }

    setSubmitError(null);
    setIsSubmitting(true);

    try {
      await updateCompanyProfile(token, {
        location: location.trim(),
        description: description.trim(),
        ...(website.trim() ? { website: website.trim() } : {}),
      });
      router.push('/company/onboarding/contact');
    } catch (error) {
      setSubmitError(describeOnboardingError(error));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <SafeAreaView style={styles.safeArea} edges={['top']}>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView
          style={{ backgroundColor: PathoraColors.paper }}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
          keyboardShouldPersistTaps="handled">
          <PathoraDetailHeader bottomGap={20} topInset={0} eyebrow="COMPANY SETUP" title="Company profile" subtitle="Set up your company workspace" onBack={handleBack} />

          <OnboardingStepHeader step={2} heading="Company details" />

          <PathoraOnboardingCard>
            <View style={styles.fields}>
              <PathoraInput
                label="Location"
                value={location}
                onChangeText={setLocation}
                placeholder="Cairo, Egypt"
                autoCapitalize="words"
              />

              <PathoraInput
                label="Website (optional)"
                value={website}
                onChangeText={setWebsite}
                placeholder="https://yourcompany.com"
                autoCapitalize="none"
                keyboardType="url"
              />

              <PathoraInput
                label="Company description"
                value={description}
                onChangeText={setDescription}
                placeholder="Tell candidates what your company does"
                multiline
                numberOfLines={4}
              />
            </View>
          </PathoraOnboardingCard>

          <PathoraInlineNotice message={submitError ?? ''} />

          <PathoraButton
            label={isSubmitting ? 'Saving…' : 'Continue'}
            variant="primary"
            onPress={handleContinue}
            disabled={!canContinue || isSubmitting}
            style={styles.continueButton}
          />
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

export default function CompanyDetailsRoute() {
  return (
    <OnboardingGuard role="COMPANY">
      <CompanyDetailsScreen />
    </OnboardingGuard>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: PathoraColors.teal700,
  },
  flex: {
    flex: 1,
    backgroundColor: PathoraColors.paper,
  },
  scrollContent: {
    flexGrow: 1,
    paddingHorizontal: PathoraSpacing[24],
    paddingBottom: PathoraSpacing[40],
  },
  fields: {
    gap: PathoraSpacing[20],
  },
  continueButton: {
    marginBottom: PathoraSpacing[24],
  },
});
