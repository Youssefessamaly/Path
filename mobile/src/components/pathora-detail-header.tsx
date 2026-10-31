import type { ReactNode } from 'react';
import { I18nManager, Pressable, StyleSheet, Text, View } from 'react-native';
import { SymbolView } from 'expo-symbols';

import { PathoraColors, PathoraFonts, PathoraSpacing } from '@/constants/pathora-theme';

type Props = {
  title: string;
  onBack?: () => void;
  eyebrow?: string;
  subtitle?: string;
  action?: ReactNode;
  /** Set true when the header is rendered outside a padded ScrollView (e.g. CV preview). */
  standalone?: boolean;
  /** Existing detail screens may use a 20px rather than 24px page gutter. */
  horizontalInset?: number;
  /** Pass 0 for forms whose scroll content has no top padding. */
  topInset?: number;
  /** Space after the panel when the next block does not use a ScrollView gap. */
  bottomGap?: number;
};

/** Consistent, accessible teal navigation header for detail/edit/form screens. */
export function PathoraDetailHeader({ title, onBack, eyebrow, subtitle, action, standalone = false, horizontalInset = 24, topInset = 12, bottomGap = 0 }: Props) {
  return (
    <View style={[styles.panel, !standalone && { marginHorizontal: -horizontalInset, marginTop: -topInset }, standalone && styles.standalone, bottomGap > 0 && { marginBottom: bottomGap }]}>
      <View style={styles.row}>
        {onBack ? (
          <Pressable
            accessibilityRole="button" accessibilityLabel="Go back"
            hitSlop={8} onPress={onBack}
            style={({ pressed }) => [styles.back, pressed && styles.pressed]}>
            <SymbolView
              name={{ ios: I18nManager.isRTL ? 'chevron.right' : 'chevron.left', android: I18nManager.isRTL ? 'chevron_right' : 'chevron_left', web: I18nManager.isRTL ? 'chevron_right' : 'chevron_left' }}
              tintColor={PathoraColors.surface} size={21}
            />
          </Pressable>
        ) : null}
        <View style={styles.textGroup}>
          {eyebrow ? <Text style={styles.eyebrow}>{eyebrow}</Text> : null}
          <Text style={styles.title} numberOfLines={2}>{title}</Text>
          {subtitle ? <Text style={styles.subtitle} numberOfLines={2}>{subtitle}</Text> : null}
        </View>
        {action ? <View style={styles.action}>{action}</View> : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    paddingHorizontal: PathoraSpacing[20], paddingTop: PathoraSpacing[20], paddingBottom: PathoraSpacing[24],
    backgroundColor: PathoraColors.teal700,
    borderBottomLeftRadius: 24, borderBottomRightRadius: 24,
  },
  standalone: { marginHorizontal: 0, marginTop: 0 },
  row: { flexDirection: 'row', alignItems: 'center', gap: PathoraSpacing[12] },
  back: {
    width: 40, height: 40, borderRadius: 20,
    backgroundColor: 'rgba(255,255,255,0.12)', borderColor: 'rgba(255,255,255,0.25)', borderWidth: 1,
    alignItems: 'center', justifyContent: 'center',
  },
  pressed: { opacity: 0.7 },
  textGroup: { flex: 1, minWidth: 0, gap: 3 },
  eyebrow: { color: 'rgba(255,255,255,0.75)', fontFamily: PathoraFonts.en.bold, fontSize: 10, letterSpacing: 1.1 },
  title: { color: PathoraColors.surface, fontFamily: PathoraFonts.en.extraBold, fontSize: 21, lineHeight: 29, letterSpacing: -0.4 },
  subtitle: { color: 'rgba(255,255,255,0.8)', fontFamily: PathoraFonts.en.medium, fontSize: 12.5, lineHeight: 19 },
  action: { flexShrink: 0 },
});
