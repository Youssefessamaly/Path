import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'expo-router';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { OnboardingStepHeader } from '@/components/onboarding/onboarding-step-header';
import { PathoraDetailHeader } from '@/components/pathora-detail-header';
import { PathoraOnboardingCard } from '@/components/pathora-onboarding-card';
import { PathoraButton } from '@/components/pathora-button';
import { ProfilePhotoEditor } from '@/components/profile-photo-editor';
import { PathoraColors as C, PathoraFonts as F } from '@/constants/pathora-theme';
import { useAuth } from '@/context/auth-context';
import { completeCompanyOnboarding, completeInternOnboarding, getCompanyProfileOrNull,
  getInternProfileOrNull, type CompanyProfile, type InternProfile } from '@/lib/api';
import { initialsFor } from '@/lib/initials';
import { describeOnboardingError, resolveCompanyPostAuthRoute } from '@/lib/onboarding';

type Props = { role: 'INTERN' | 'COMPANY' };

function initialsCompany(name?: string | null, email?: string) {
  const words = (name ?? '').trim().split(/\s+/).filter(Boolean);
  return words.length ? words.slice(0, 2).map((w) => w[0]).join('').toUpperCase()
    : (email?.slice(0, 1) || '?').toUpperCase();
}

export function OptionalPhotoStep({ role }: Props) {
  const router = useRouter();
  const { token, user } = useAuth();
  const company = role === 'COMPANY';
  const [profile, setProfile] = useState<InternProfile | CompanyProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!token) return;
    try {
      const data = company ? await getCompanyProfileOrNull(token) : await getInternProfileOrNull(token);
      setProfile(data);
    } catch { setError('We could not load your profile. Try again.'); }
    finally { setLoading(false); }
  }, [company, token]);
  useEffect(() => { void load(); }, [load]);

  const finish = async () => {
    if (!token || busy) return;
    setBusy(true); setError(null);
    try {
      if (company) {
        await completeCompanyOnboarding(token);
        router.replace(await resolveCompanyPostAuthRoute(token));
      } else {
        await completeInternOnboarding(token);
        router.replace('/intern');
      }
    } catch (e) { setError(describeOnboardingError(e)); }
    finally { setBusy(false); }
  };

  const requiresCv = !company && profile != null && !profile.onboardingCompleted &&
    !(profile as InternProfile).cvFileName;

  const photoUrl = company ? (profile as CompanyProfile | null)?.logoUrl : (profile as InternProfile | null)?.photoUrl;
  const initials = company ? initialsCompany((profile as CompanyProfile | null)?.companyName, user?.email)
    : initialsFor((profile as InternProfile | null)?.firstName, (profile as InternProfile | null)?.lastName, user?.email);

  return (
    <SafeAreaView style={styles.safeArea} edges={['top']}>
      <ScrollView style={styles.scroll} contentContainerStyle={styles.content}>
        <PathoraDetailHeader bottomGap={20} topInset={0}
          eyebrow={company ? 'COMPANY SETUP' : 'STUDENT SETUP'} title={company ? 'Company profile' : 'Your profile'}
          subtitle="One final touch — entirely optional"
          onBack={() => router.replace(company ? '/company/onboarding/contact' : '/onboarding/skills')} />
        <OnboardingStepHeader step={4} heading={company ? 'Make your brand recognizable' : 'Make your profile your own'} />
        <PathoraOnboardingCard>
          <View style={styles.cardContent}>
            <Text style={styles.optional}>OPTIONAL</Text>
            <Text style={styles.explainer}>{company
              ? 'Upload your company logo so interns can recognize you in their internship feed.'
              : 'Add a profile photo for your applications. You can always do this later.'}</Text>
            {loading || !profile ? <ActivityIndicator color={C.teal700} /> : token ? (
              <ProfilePhotoEditor role={role} token={token} initials={initials}
                photoUrl={photoUrl} size={112} onSaved={load} />
            ) : null}
            <Text style={styles.fallback}>Without an image, PATH will continue showing your initials.</Text>
          </View>
        </PathoraOnboardingCard>
        {requiresCv ? (
          <View style={styles.cvNotice}>
            <Text style={styles.cvNoticeText}>Upload your CV before finishing your student setup. Your profile photo remains optional.</Text>
            <Pressable accessibilityRole="button" onPress={() => router.replace('/onboarding/skills')} style={styles.cvReturn}>
              <Text style={styles.cvReturnText}>Go to CV upload</Text>
            </Pressable>
          </View>
        ) : null}
        {error ? <Text style={styles.error} accessibilityRole="alert">{error}</Text> : null}
        <PathoraButton variant="primary" label={busy ? 'Finishing…' : 'Finish setup'}
          disabled={busy || loading || !profile || requiresCv} onPress={() => void finish()} />
        <Pressable accessibilityRole="button" onPress={() => void finish()} disabled={busy || loading || !profile || requiresCv} style={styles.skip}>
          <Text style={styles.skipText}>Skip for now</Text>
        </Pressable>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: C.teal700 },
  scroll: { backgroundColor: C.paper },
  content: { paddingHorizontal: 24, paddingBottom: 50, gap: 16 },
  cardContent: { alignItems: 'center', gap: 20, paddingVertical: 12 },
  optional: { color: C.teal700, fontFamily: F.en.bold, fontSize: 11, letterSpacing: 1.3 },
  explainer: { fontFamily: F.en.medium, color: C.textSecondary, fontSize: 14, lineHeight: 22, textAlign: 'center' },
  fallback: { fontFamily: F.en.medium, fontSize: 12, color: C.textSecondary, textAlign: 'center' },
  error: { color: '#A33B37', fontFamily: F.en.medium, fontSize: 13 },
  cvNotice: { padding: 14, borderWidth: 1, borderColor: C.border, borderRadius: 12,
    backgroundColor: C.surface, gap: 10 },
  cvNoticeText: { color: C.textSecondary, fontFamily: F.en.medium, fontSize: 13, lineHeight: 20 },
  cvReturn: { alignSelf: 'flex-start', paddingVertical: 6, paddingRight: 12 },
  cvReturnText: { color: C.teal700, fontFamily: F.en.bold, fontSize: 13 },
  skip: { padding: 12, alignItems: 'center' },
  skipText: { fontFamily: F.en.bold, color: C.teal700, fontSize: 14 },
});
