import { Pressable, StyleSheet, Text, View } from 'react-native';

import { PathoraColors, PathoraFonts, PathoraRadius } from '@/constants/pathora-theme';

type Props = {
  label: string;
  count?: number | null;
  selected: boolean;
  onPress: () => void;
  accessibilityLabel?: string;
};

/** Identical filter shape, selected state and count across intern/company workspaces. */
export function PathoraFilterChip({ label, count, selected, onPress, accessibilityLabel }: Props) {
  const hasCount = count !== null && count !== undefined;
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      accessibilityLabel={accessibilityLabel ?? `${label}${hasCount ? `, ${count}` : ''}`}
      style={({ pressed }) => [styles.chip, selected && styles.selected, pressed && styles.pressed]}>
      <Text style={[styles.label, selected && styles.labelSelected]}>{label}</Text>
      {hasCount && (
        <View style={[styles.countWrap, selected && styles.countWrapSelected]}>
          <Text style={[styles.count, selected && styles.countSelected]}>{count}</Text>
        </View>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  chip: {
    flexDirection: 'row', alignItems: 'center', gap: 7,
    borderRadius: PathoraRadius[999],
    backgroundColor: PathoraColors.surface,
    borderColor: PathoraColors.border, borderWidth: 1,
    minHeight: 44, paddingVertical: 9, paddingHorizontal: 13,
  },
  selected: { backgroundColor: PathoraColors.teal700, borderColor: PathoraColors.teal700 },
  label: { fontFamily: PathoraFonts.en.bold, fontSize: 13, color: PathoraColors.textSecondary },
  labelSelected: { color: PathoraColors.surface },
  countWrap: { borderRadius: PathoraRadius[999], backgroundColor: PathoraColors.paper,
    paddingHorizontal: 7, paddingVertical: 2 },
  countWrapSelected: { backgroundColor: 'rgba(255,255,255,0.2)' },
  count: { fontFamily: PathoraFonts.en.bold, fontSize: 11.5, color: PathoraColors.textSecondary },
  countSelected: { color: PathoraColors.surface },
  pressed: { opacity: 0.74 },
});
