import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

import { PathoraColors, PathoraSpacing } from '@/constants/pathora-theme';

type Props = { children: ReactNode };

/** Shared white form surface for both three-step onboarding / profile-edit flows. */
export function PathoraOnboardingCard({ children }: Props) {
  return <View style={styles.card}>{children}</View>;
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: PathoraColors.surface,
    borderColor: PathoraColors.border,
    borderWidth: 1,
    borderRadius: 18,
    padding: PathoraSpacing[20],
    marginBottom: PathoraSpacing[20],
  },
});
