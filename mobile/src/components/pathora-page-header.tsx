import type { ReactNode } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { PathoraColors, PathoraFonts, PathoraSpacing } from '@/constants/pathora-theme';

export type PathoraPageHeaderProps = {
  eyebrow: string;
  title: string;
  subtitle: string;
  /** Optional genuine action, e.g. Post internship. Do not use for decorative art. */
  action?: ReactNode;
};

/** Consistent header for top-level student/company tabs (dashboard uses WelcomeHeader). */
export function PathoraPageHeader({ eyebrow, title, subtitle, action }: PathoraPageHeaderProps) {
  return (
    <View style={styles.container}>
      <View style={styles.copy}>
        <Text style={styles.eyebrow}>{eyebrow}</Text>
        <Text style={styles.title}>{title}</Text>
        <Text style={styles.subtitle}>{subtitle}</Text>
      </View>
      {action ? <View style={styles.action}>{action}</View> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    gap: PathoraSpacing[12],
  },
  copy: { flex: 1, minWidth: 0, gap: PathoraSpacing[4] },
  eyebrow: {
    fontFamily: PathoraFonts.en.bold, fontSize: 11, letterSpacing: 1.35,
    color: PathoraColors.teal600,
  },
  title: {
    fontFamily: PathoraFonts.en.extraBold, fontSize: 28, lineHeight: 37,
    letterSpacing: -0.65, color: PathoraColors.ink900,
  },
  subtitle: {
    fontFamily: PathoraFonts.en.medium, fontSize: 13.5, lineHeight: 20,
    color: PathoraColors.textSecondary,
  },
  action: { flexShrink: 0, alignSelf: 'center' },
});
