import { Pressable, StyleSheet, Text, View } from 'react-native';

import { PathoraColors, PathoraFonts, PathoraSpacing } from '@/constants/pathora-theme';

type Props = {
  eyebrow?: string;
  title: string;
  actionLabel?: string;
  onAction?: () => void;
};

/** Shared section hierarchy and optional real action across both workspaces. */
export function PathoraSectionHeading({ eyebrow, title, actionLabel, onAction }: Props) {
  return (
    <View style={styles.container}>
      <View style={styles.copy}>
        {eyebrow ? <Text style={styles.eyebrow}>{eyebrow}</Text> : null}
        <Text style={styles.title}>{title}</Text>
      </View>
      {actionLabel && onAction ? (
        <Pressable
          onPress={onAction}
          accessibilityRole="button"
          accessibilityLabel={actionLabel}
          hitSlop={8}
          style={({ pressed }) => [styles.actionTouch, pressed && styles.pressed]}>
          <Text style={styles.actionText} numberOfLines={2}>{actionLabel} →</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', gap: PathoraSpacing[12] },
  copy: { flex: 1, minWidth: 0, gap: PathoraSpacing[4] },
  eyebrow: { fontFamily: PathoraFonts.en.bold, fontSize: 12, letterSpacing: 1.2, color: PathoraColors.teal600 },
  title: { fontFamily: PathoraFonts.en.extraBold, fontSize: 20, lineHeight: 28, letterSpacing: -0.4, color: PathoraColors.ink900 },
  actionTouch: { minHeight: 44, paddingVertical: 5, justifyContent: 'center', flexShrink: 1, maxWidth: '35%' },
  actionText: { fontFamily: PathoraFonts.en.bold, fontSize: 13, lineHeight: 18, textAlign: 'right', color: PathoraColors.teal700 },
  pressed: { opacity: 0.72 },
});
