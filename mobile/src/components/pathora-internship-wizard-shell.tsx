import { useEffect, useRef, type ReactNode } from 'react';
import { Keyboard, KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View, type ScrollViewInstance } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import { PathoraButton } from '@/components/pathora-button';
import { PathoraDetailHeader } from '@/components/pathora-detail-header';
import { PathoraInlineNotice } from '@/components/pathora-inline-notice';
import { PathoraColors as C, PathoraFonts as F } from '@/constants/pathora-theme';

export type InternshipWizardStep = 1 | 2 | 3 | 4;

const STEP_LABELS = [
  { eyebrow: 'THE OPPORTUNITY', title: 'Tell us about the role', description: 'Start with the details interns will see.' },
  { eyebrow: 'SCHEDULE & FORMAT', title: 'Set the schedule', description: 'When and where will the internship take place?' },
  { eyebrow: 'CANDIDATE REQUIREMENTS', title: 'Find the right candidates', description: 'Choose the skills and background you need.' },
  { eyebrow: 'APPLICATION & REVIEW', title: 'Ready to publish?', description: 'Choose an application method and review the details.' },
] as const;

type Props = {
  mode: 'create' | 'edit';
  step: InternshipWizardStep;
  onBack: () => void;
  onContinue: () => void;
  onFinish: () => void;
  isSubmitting: boolean;
  error?: string | null;
  children: ReactNode;
};

/** Presentation only; each form retains its own field state, validation and API payload. */
export function PathoraInternshipWizardShell({
  mode, step, onBack, onContinue, onFinish, isSubmitting, error, children,
}: Props) {
  const insets = useSafeAreaInsets();
  const scrollRef = useRef<ScrollViewInstance>(null);
  const details = STEP_LABELS[step - 1];
  const handleBack = () => { Keyboard.dismiss(); onBack(); };
  const handlePrimaryAction = () => {
    Keyboard.dismiss();
    if (step === 4) onFinish();
    else onContinue();
  };

  useEffect(() => {
    scrollRef.current?.scrollTo({ y: 0, animated: false });
  }, [step]);

  return (
    <SafeAreaView style={styles.safeArea} edges={['top']}>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView
          ref={scrollRef}
          style={styles.scrollView}
          contentContainerStyle={styles.scrollContent}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="on-drag"
          showsVerticalScrollIndicator={false}>
          <PathoraDetailHeader
            topInset={0}
            eyebrow="COMPANY WORKSPACE"
            title={mode === 'create' ? 'Post an internship' : 'Edit internship'}
            onBack={handleBack}
          />
          <View style={styles.stepIntro}>
            <View style={styles.progressLabels}>
              <Text style={styles.stepCount}>STEP {String(step).padStart(2, '0')} / 04</Text>
              <Text style={styles.progressCopy}>{step} of 4</Text>
            </View>
            <View style={styles.progressBars} accessibilityLabel={`Step ${step} of 4`}>
              {([1, 2, 3, 4] as const).map((number) => (
                <View
                  key={number}
                  style={[styles.progressBar, number <= step && styles.progressBarActive]}
                />
              ))}
            </View>
            <Text style={styles.eyebrow}>{details.eyebrow}</Text>
            <Text style={styles.stepTitle}>{step === 4 && mode === 'edit' ? 'Review your changes' : details.title}</Text>
            <Text style={styles.stepDescription}>{step === 4 && mode === 'edit' ? 'Review the details before saving.' : details.description}</Text>
          </View>
          <View style={styles.form}>{children}</View>
        </ScrollView>

        {/* A dedicated footer replaces the floating company tab bar on create/edit routes. */}
        <View style={[styles.footer, { paddingBottom: Math.max(insets.bottom, 12) }]}>
          {error ? <View style={styles.notice}><PathoraInlineNotice message={error} /></View> : null}
          <View style={styles.footerActions}>
            {step > 1 ? (
              <View style={styles.backAction}>
                <PathoraButton label="Back" variant="secondary" onPress={handleBack} disabled={isSubmitting} />
              </View>
            ) : null}
            <View style={styles.mainAction}>
              <PathoraButton
                label={step === 4
                  ? (isSubmitting ? 'Saving…' : mode === 'create' ? 'Post internship' : 'Save changes')
                  : 'Continue'}
                onPress={handlePrimaryAction}
                disabled={isSubmitting}
              />
            </View>
          </View>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

/** Small, consistent review rows shared by create/edit final steps. */
export function InternshipReviewRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.reviewRow}>
      <Text style={styles.reviewLabel}>{label}</Text>
      <Text style={styles.reviewValue}>{value || '—'}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: C.teal700 },
  flex: { flex: 1, backgroundColor: C.paper },
  scrollView: { flex: 1, backgroundColor: C.paper },
  scrollContent: { flexGrow: 1, paddingHorizontal: 24, paddingBottom: 24 },
  stepIntro: { paddingTop: 22, paddingBottom: 20, gap: 8 },
  progressLabels: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  stepCount: { color: C.teal700, fontFamily: F.en.bold, letterSpacing: 1.3, fontSize: 11 },
  progressCopy: { color: C.textSecondary, fontFamily: F.en.medium, fontSize: 12 },
  progressBars: { flexDirection: 'row', gap: 8, marginTop: 5, marginBottom: 13 },
  progressBar: { flex: 1, height: 5, borderRadius: 99, backgroundColor: C.border },
  progressBarActive: { backgroundColor: C.teal600 },
  eyebrow: { color: C.teal600, fontFamily: F.en.bold, fontSize: 10, letterSpacing: 1.1 },
  stepTitle: { color: C.ink900, fontFamily: F.en.extraBold, fontSize: 23, lineHeight: 31 },
  stepDescription: { color: C.textSecondary, fontFamily: F.en.medium, fontSize: 13, lineHeight: 21 },
  form: { gap: 18 },
  notice: { marginBottom: 10 },
  footer: {
    backgroundColor: C.paper,
    paddingTop: 12, paddingHorizontal: 24,
    borderTopWidth: 1, borderTopColor: C.border,
  },
  footerActions: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  backAction: { flex: 0.42 },
  mainAction: { flex: 1 },
  reviewRow: { gap: 5, paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: C.border },
  reviewLabel: { color: C.textSecondary, fontFamily: F.en.medium, fontSize: 12 },
  reviewValue: { color: C.ink900, fontFamily: F.en.semiBold, fontSize: 14, lineHeight: 20 },
});
