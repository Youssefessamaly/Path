import { StyleSheet, Text, View } from 'react-native';

import { PathoraColors, PathoraFonts, PathoraSpacing } from '@/constants/pathora-theme';

type Props = { message: string; tone?: 'error' | 'info' | 'success' };

/** Always wraps notice text in Text, including empty and optional message states. */
export function PathoraInlineNotice({ message, tone = 'error' }: Props) {
  if (!message) return null;
  return (
    <View accessibilityRole="alert" style={[styles.notice, tone === 'error' ? styles.error : tone === 'success' ? styles.success : styles.info]}>
      <View style={[styles.accent, tone === 'error' ? styles.errorAccent : tone === 'success' ? styles.successAccent : styles.infoAccent]} />
      <Text style={[styles.message, tone === 'error' ? styles.errorText : tone === 'success' ? styles.successText : styles.infoText]}>{message}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  notice: {
    borderWidth: 1, borderRadius: 12, padding: PathoraSpacing[12],
    flexDirection: 'row', alignItems: 'flex-start', gap: PathoraSpacing[12],
  },
  error: { backgroundColor: '#FCF0EE', borderColor: '#EFCFC9' },
  info: { backgroundColor: PathoraColors.teal100, borderColor: '#BEDFD7' },
  success: { backgroundColor: '#ECF7F1', borderColor: '#CAE7D7' },
  accent: { width: 3, borderRadius: 99, alignSelf: 'stretch', minHeight: 15 },
  errorAccent: { backgroundColor: PathoraColors.error },
  infoAccent: { backgroundColor: PathoraColors.teal500 },
  successAccent: { backgroundColor: PathoraColors.success },
  message: { flex: 1, fontFamily: PathoraFonts.en.medium, fontSize: 12.5, lineHeight: 19 },
  errorText: { color: '#8E3029' },
  infoText: { color: PathoraColors.teal700 },
  successText: { color: '#216746' },
});
