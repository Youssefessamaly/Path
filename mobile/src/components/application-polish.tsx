import type { ReactNode } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { PathoraColors, PathoraFonts } from '@/constants/pathora-theme';
import { PathoraMetricCard } from '@/components/pathora-metric-card';
import { PathoraFilterChip } from '@/components/pathora-filter-chip';
import { pathoraCardSurface } from '@/components/pathora-card-styles';

/** Lightweight, shared visual pieces for the two application workspaces. */
export function ApplicationMetric({ value, label, tint = 'teal' }: {
  value: number;
  label: string;
  tint?: 'teal' | 'amber' | 'muted';
}) {
  return <PathoraMetricCard value={value} label={label} tone={tint} />;
}

export function ApplicationFilter({
  title, count, selected, onPress,
}: {
  title: string;
  count: number;
  selected: boolean;
  onPress: () => void;
}) {
  return <PathoraFilterChip label={title} count={count} selected={selected} onPress={onPress} />;
}

export function ApplicationDetailCard({
  label, children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <View style={styles.detailCard}>
      <View style={styles.detailAccent} />
      <Text style={styles.detailHeading}>{label}</Text>
      {children}
    </View>
  );
}

export function ApplicationDetailText({ children, muted = false }: { children: ReactNode; muted?: boolean }) {
  return <Text style={[styles.detailText, muted && styles.detailMuted]}>{children}</Text>;
}

const styles = StyleSheet.create({
  detailCard: { ...pathoraCardSurface, padding: 18, gap: 12 },
  detailAccent: { width: 24, height: 4, borderRadius: 99, backgroundColor: PathoraColors.teal500 },
  detailHeading: {
    fontFamily: PathoraFonts.en.bold, fontSize: 11, letterSpacing: 1.2,
    color: PathoraColors.teal700, textTransform: 'uppercase', marginBottom: 1,
  },
  detailText: { fontFamily: PathoraFonts.en.semiBold, fontSize: 14, lineHeight: 22, color: PathoraColors.ink900 },
  detailMuted: { color: PathoraColors.textSecondary, fontFamily: PathoraFonts.en.regular },
});
