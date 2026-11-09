import { useRouter } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import { PathoraButton } from '@/components/pathora-button';
import { PathoraLogo } from '@/components/pathora-logo';
import { PathoraTopOverscrollFill } from '@/components/pathora-top-overscroll-fill';
import { PathoraColors as C, PathoraFonts as F, PathoraRadius as R, PathoraSpacing as S } from '@/constants/pathora-theme';

type Benefit = {
  icon: SymbolViewProps['name'];
  title: string;
  description: string;
};

const BENEFITS: Benefit[] = [
  {
    icon: { ios: 'checkmark.shield.fill', android: 'verified_user', web: 'verified_user' },
    title: 'Reviewed companies',
    description: 'Explore internships from companies reviewed by Path.',
  },
  {
    icon: { ios: 'sparkles', android: 'auto_awesome', web: 'auto_awesome' },
    title: 'Opportunities that fit',
    description: 'See how each role matches your profile and interests.',
  },
  {
    icon: { ios: 'list.clipboard.fill', android: 'assignment', web: 'assignment' },
    title: 'Every application in one place',
    description: 'Stay up to date as your applications progress.',
  },
];

function BenefitRow({ icon, title, description }: Benefit) {
  return (
    <View style={styles.benefitRow}>
      <View style={styles.benefitIcon}>
        <SymbolView name={icon} size={20} tintColor={C.teal700} />
      </View>
      <View style={styles.benefitCopy}>
        <Text style={styles.benefitTitle}>{title}</Text>
        <Text style={styles.benefitDescription}>{description}</Text>
      </View>
    </View>
  );
}

export default function WelcomeScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();

  return (
    <SafeAreaView style={styles.safeArea} edges={['top']}>
      {/* The global status bar is dark on non-auth routes; this screen has a teal top. */}
      <StatusBar style="light" />
      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={[styles.scrollContent, { paddingBottom: Math.max(insets.bottom, S[16]) + S[16] }]}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled">
        <PathoraTopOverscrollFill />
        <View style={styles.hero}>
          <View pointerEvents="none" style={styles.heroCircle} />
          <View pointerEvents="none" style={styles.heroCircleSmall} />

          <View style={styles.heroTopRow}>
            <PathoraLogo onDark size={34} wordmarkSize={23} />
            <View style={styles.locationTag}>
              <View style={styles.locationDot} />
              <Text style={styles.locationLabel}>EGYPT</Text>
            </View>
          </View>

          <View style={styles.heroCopy}>
            <Text style={styles.heroEyebrow}>YOUR NEXT CHAPTER</Text>
            <Text style={styles.heroTitle}>
              Find your next{'\n'}
              <Text style={styles.heroTitleAccent}>opportunity.</Text>
            </Text>
            <Text style={styles.heroDescription}>
              Discover internships, find your fit, and keep every application moving forward.
            </Text>
          </View>

          <View style={styles.heroFoot}>
            <View style={styles.heroAccent} />
            <Text style={styles.heroFootText}>DISCOVER  ·  APPLY  ·  GROW</Text>
          </View>
        </View>

        <View style={styles.main}>
          <View style={styles.sectionHeading}>
            <Text style={styles.sectionEyebrow}>WHY PATHORA</Text>
            <Text style={styles.sectionTitle}>A simpler way to get started</Text>
          </View>

          <View style={styles.benefitsCard}>
            {BENEFITS.map((benefit, index) => (
              <View key={benefit.title}>
                {index > 0 ? <View style={styles.benefitDivider} /> : null}
                <BenefitRow {...benefit} />
              </View>
            ))}
          </View>

          <View style={styles.actions}>
            <PathoraButton
              label="Create account"
              variant="primary"
              onPress={() => router.push('/account-type')}
              style={styles.primaryAction}
            />
            <PathoraButton
              label="I already have an account"
              variant="secondary"
              onPress={() => router.push('/login')}
              style={styles.secondaryAction}
            />
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: C.teal700,
  },
  scrollView: {
    flex: 1,
    backgroundColor: C.paper,
  },
  scrollContent: {
    flexGrow: 1,
    backgroundColor: C.paper,
  },
  hero: {
    position: 'relative',
    overflow: 'hidden',
    backgroundColor: C.teal700,
    borderBottomLeftRadius: 28,
    borderBottomRightRadius: 28,
    paddingHorizontal: S[24],
    paddingTop: S[20],
    paddingBottom: S[24],
    gap: S[32],
  },
  heroCircle: {
    position: 'absolute',
    width: 245,
    height: 245,
    right: -126,
    top: 62,
    borderRadius: 140,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.11)',
    backgroundColor: 'rgba(255,255,255,0.035)',
  },
  heroCircleSmall: {
    position: 'absolute',
    width: 86,
    height: 86,
    right: 18,
    bottom: -55,
    borderRadius: 50,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.10)',
  },
  heroTopRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: S[12],
  },
  locationTag: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    borderColor: 'rgba(255,255,255,0.24)',
    borderWidth: 1,
    borderRadius: R[999],
    backgroundColor: 'rgba(255,255,255,0.08)',
    paddingHorizontal: 12,
    paddingVertical: 9,
  },
  locationDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: C.amber500,
  },
  locationLabel: {
    fontFamily: F.en.bold,
    color: C.surface,
    fontSize: 10,
    letterSpacing: 1.1,
  },
  heroCopy: {
    gap: S[12],
    paddingRight: S[8],
  },
  heroEyebrow: {
    fontFamily: F.en.bold,
    color: '#B9DAD2',
    fontSize: 10,
    letterSpacing: 2,
  },
  heroTitle: {
    color: C.surface,
    fontFamily: F.en.extraBold,
    fontSize: 34,
    lineHeight: 44,
    letterSpacing: -1.15,
  },
  heroTitleAccent: {
    color: '#BFE4DB',
  },
  heroDescription: {
    color: '#D5E8E3',
    fontFamily: F.en.medium,
    fontSize: 13,
    lineHeight: 21,
    maxWidth: 310,
  },
  heroFoot: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  heroAccent: {
    width: 24,
    height: 3,
    backgroundColor: C.amber500,
    borderRadius: R[999],
  },
  heroFootText: {
    color: '#BBD9D2',
    fontFamily: F.en.bold,
    fontSize: 9.5,
    letterSpacing: 1.3,
  },
  main: {
    flexGrow: 1,
    paddingHorizontal: S[24],
    paddingTop: S[24],
    gap: S[16],
  },
  sectionHeading: {
    gap: 5,
  },
  sectionEyebrow: {
    color: C.teal600,
    fontFamily: F.en.bold,
    fontSize: 10,
    letterSpacing: 1.5,
  },
  sectionTitle: {
    color: C.ink900,
    fontFamily: F.en.extraBold,
    fontSize: 19,
    lineHeight: 26,
    letterSpacing: -0.45,
  },
  benefitsCard: {
    backgroundColor: C.surface,
    borderWidth: 1,
    borderColor: C.border,
    borderRadius: R[20],
    paddingHorizontal: S[16],
    paddingVertical: 3,
  },
  benefitRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: S[12],
    paddingVertical: 12,
  },
  benefitIcon: {
    width: 42,
    height: 42,
    borderRadius: R[14],
    backgroundColor: C.teal100,
    alignItems: 'center',
    justifyContent: 'center',
  },
  benefitCopy: {
    flex: 1,
    gap: 3,
  },
  benefitTitle: {
    color: C.ink900,
    fontFamily: F.en.bold,
    fontSize: 13.5,
    lineHeight: 19,
  },
  benefitDescription: {
    color: C.textSecondary,
    fontFamily: F.en.regular,
    fontSize: 11.5,
    lineHeight: 17,
  },
  benefitDivider: {
    height: 1,
    backgroundColor: '#F0EEE9',
    marginLeft: 54,
  },
  actions: {
    gap: S[12],
    marginTop: 'auto',
    paddingTop: S[12],
  },
  primaryAction: {
    minHeight: 52,
    borderRadius: R[14],
  },
  secondaryAction: {
    minHeight: 52,
    borderRadius: R[14],
    backgroundColor: C.surface,
  },
});
