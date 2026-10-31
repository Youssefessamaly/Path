import { StyleSheet, Text, View } from 'react-native';

import { PathoraColors, PathoraFonts, PathoraRadius, PathoraSpacing } from '@/constants/pathora-theme';

export type OnboardingStepHeaderProps = {
  step: 1 | 2 | 3;
  heading: string;
};

const TOTAL_STEPS = 3;

/** The same progress treatment is used when setting up or editing either account type. */
export function OnboardingStepHeader({ step, heading }: OnboardingStepHeaderProps) {
  return (
    <View style={styles.wrapper}>
      <View style={styles.metaRow}>
        <Text style={styles.stepLabel}>{`STEP ${String(step).padStart(2, '0')} / 03`}</Text>
        <Text style={styles.stepHelper}>{step === 3 ? 'Final step' : 'Your progress'}</Text>
      </View>
      <View style={styles.progressRow} accessibilityLabel={`Step ${step} of ${TOTAL_STEPS}`}>
        {Array.from({ length: TOTAL_STEPS }, (_, index) => {
          const number = index + 1;
          return (
            <View
              key={number}
              style={[styles.bar, number < step && styles.barCompleted, number === step && styles.barCurrent]}
            />
          );
        })}
      </View>
      <Text style={styles.heading}>{heading}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: { marginBottom: PathoraSpacing[16], gap: PathoraSpacing[12] },
  metaRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: PathoraSpacing[12] },
  stepLabel: { fontFamily: PathoraFonts.en.bold, fontSize: 11, color: PathoraColors.teal700, letterSpacing: 1.1 },
  stepHelper: { fontFamily: PathoraFonts.en.medium, fontSize: 12, color: PathoraColors.textSecondary },
  progressRow: { flexDirection: 'row', gap: 8 },
  bar: { flex: 1, height: 5, borderRadius: PathoraRadius[999], backgroundColor: PathoraColors.border },
  barCompleted: { backgroundColor: PathoraColors.teal400 },
  barCurrent: { backgroundColor: PathoraColors.teal700 },
  heading: { fontFamily: PathoraFonts.en.extraBold, fontSize: 22, lineHeight: 30, color: PathoraColors.ink900 },
});
