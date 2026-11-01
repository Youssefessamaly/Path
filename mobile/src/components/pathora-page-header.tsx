import type { ReactNode } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { PathoraColors, PathoraFonts, PathoraSpacing } from '@/constants/pathora-theme';
import { PathoraTopOverscrollFill } from '@/components/pathora-top-overscroll-fill';

export type PathoraPageHeaderProps = {
  eyebrow: string;
  title: string;
  subtitle: string;
  action?: ReactNode;
};

/** Deep-teal header shared across student and company main tabs.
 * Parent content uses 24px horizontal and 12px top padding.
 */
export function PathoraPageHeader({ eyebrow, title, subtitle, action }: PathoraPageHeaderProps) {
  return (
    <View style={styles.panel}>
      <PathoraTopOverscrollFill />
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
  panel: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    gap: PathoraSpacing[12],
    marginHorizontal: -PathoraSpacing[24], marginTop: -PathoraSpacing[12],
    paddingHorizontal: PathoraSpacing[24], paddingTop: PathoraSpacing[24],
    paddingBottom: PathoraSpacing[32],
    backgroundColor: PathoraColors.teal700,
    borderBottomLeftRadius: 26, borderBottomRightRadius: 26,
  },
  copy: { flex: 1, minWidth: 0, gap: PathoraSpacing[8] },
  eyebrow: {
    fontFamily: PathoraFonts.en.bold, fontSize: 12, letterSpacing: 1.1,
    color: 'rgba(255,255,255,0.78)',
  },
  title: {
    fontFamily: PathoraFonts.en.extraBold, fontSize: 28, lineHeight: 37,
    letterSpacing: -0.65, color: PathoraColors.surface,
  },
  subtitle: {
    fontFamily: PathoraFonts.en.medium, fontSize: 14, lineHeight: 22,
    color: 'rgba(255,255,255,0.80)',
  },
  action: { flexShrink: 0, alignSelf: 'center' },
});
