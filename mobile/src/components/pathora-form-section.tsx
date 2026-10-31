import type { ReactNode } from 'react';
import { StyleSheet, Text, View, type StyleProp, type ViewStyle } from 'react-native';

import { PathoraColors, PathoraFonts, PathoraSpacing } from '@/constants/pathora-theme';

type Props = {
  title: string;
  description?: string;
  index?: number;
  children: ReactNode;
  style?: StyleProp<ViewStyle>;
};

/** Shared presentation only. Keeps field state and validation in the owning screen. */
export function PathoraFormSection({ title, description, index, children, style }: Props) {
  return (
    <View style={[styles.card, style]}>
      <View style={styles.headingRow}>
        {index !== undefined ? (
          <View style={styles.numberBadge}>
            <Text style={styles.numberText}>{String(index).padStart(2, '0')}</Text>
          </View>
        ) : (
          <View style={styles.accentLine} />
        )}
        <View style={styles.headingCopy}>
          <Text style={styles.title}>{title}</Text>
          {description ? <Text style={styles.description}>{description}</Text> : null}
        </View>
      </View>
      <View style={styles.content}>{children}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: PathoraColors.surface,
    borderColor: PathoraColors.border,
    borderWidth: 1,
    borderRadius: 18,
    padding: PathoraSpacing[20],
    gap: PathoraSpacing[20],
  },
  headingRow: { flexDirection: 'row', alignItems: 'flex-start', gap: PathoraSpacing[12] },
  headingCopy: { flex: 1, minWidth: 0, gap: PathoraSpacing[4] },
  numberBadge: {
    width: 34, height: 34, borderRadius: 11,
    backgroundColor: PathoraColors.teal100,
    alignItems: 'center', justifyContent: 'center',
  },
  numberText: { fontFamily: PathoraFonts.en.extraBold, fontSize: 12, color: PathoraColors.teal700 },
  accentLine: { width: 5, height: 30, borderRadius: 99, backgroundColor: PathoraColors.teal500 },
  title: { fontFamily: PathoraFonts.en.bold, fontSize: 16.5, lineHeight: 23, color: PathoraColors.ink900 },
  description: { fontFamily: PathoraFonts.en.regular, fontSize: 12.5, lineHeight: 19, color: PathoraColors.textSecondary },
  content: { gap: PathoraSpacing[20] },
});
