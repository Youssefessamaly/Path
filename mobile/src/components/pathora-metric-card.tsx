import { Pressable, StyleSheet, Text, View } from 'react-native';

import { PathoraColors, PathoraFonts, PathoraSpacing } from '@/constants/pathora-theme';

export type PathoraMetricTone = 'teal' | 'amber' | 'muted';
type Props = {
  label: string;
  value: number | null;
  tone?: PathoraMetricTone;
  unavailable?: boolean;
  onPress?: () => void;
};

/** The same stat-card treatment for dashboards, applications, applicants and listings. */
export function PathoraMetricCard({ label, value, tone = 'teal', unavailable = false, onPress }: Props) {
  const inner = (
    <>
      <View style={[styles.indicator, tone === 'amber' && styles.amber, tone === 'muted' && styles.muted]} />
      <Text style={styles.value} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.8}>
        {value === null ? '—' : value}
      </Text>
      <Text style={styles.label} numberOfLines={2}>{label}</Text>
    </>
  );

  if (!onPress) {
    return <View style={styles.card} accessibilityLabel={`${label}: ${value === null ? 'loading' : value}`}>{inner}</View>;
  }
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${label}: ${value === null ? (unavailable ? 'unavailable' : 'loading') : value}`}
      style={({ pressed }) => [styles.card, pressed && styles.pressed]}>
      {inner}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    flex: 1, minWidth: 0, minHeight: 108,
    justifyContent: 'center',
    backgroundColor: PathoraColors.surface,
    borderWidth: 1, borderColor: PathoraColors.border, borderRadius: 16,
    paddingVertical: PathoraSpacing[16], paddingHorizontal: PathoraSpacing[12],
    gap: PathoraSpacing[8],
  },
  indicator: { width: 24, height: 4, borderRadius: 999, backgroundColor: PathoraColors.teal500 },
  amber: { backgroundColor: PathoraColors.amber500 },
  muted: { backgroundColor: PathoraColors.textTertiary },
  value: { fontFamily: PathoraFonts.en.extraBold, fontSize: 27, lineHeight: 32, color: PathoraColors.ink900 },
  label: { fontFamily: PathoraFonts.en.semiBold, fontSize: 12, lineHeight: 17, color: PathoraColors.textSecondary },
  pressed: { opacity: 0.7 },
});
