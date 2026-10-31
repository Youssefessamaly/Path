import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';

import { PathoraButton } from '@/components/pathora-button';
import { PathoraInlineNotice } from '@/components/pathora-inline-notice';
import { PathoraPageHeader } from '@/components/pathora-page-header';
import { PathoraColors, PathoraFonts, PathoraSpacing } from '@/constants/pathora-theme';

type Props = {
  eyebrow: string;
  title: string;
  subtitle: string;
  loading: boolean;
  loadingLabel: string;
  errorMessage?: string;
  onRetry?: () => void;
};

/** Unified, readable loading/error state for intern and company profile tabs. */
export function PathoraProfileState({ eyebrow, title, subtitle, loading, loadingLabel, errorMessage, onRetry }: Props) {
  return (
    <View style={styles.page}>
      <PathoraPageHeader eyebrow={eyebrow} title={title} subtitle={subtitle} />
      <View style={styles.card}>
        {loading ? (
          <>
            <ActivityIndicator size="large" color={PathoraColors.teal600} />
            <Text style={styles.message}>{loadingLabel}</Text>
          </>
        ) : (
          <>
            <PathoraInlineNotice message={errorMessage ?? 'Unable to load your profile.'} />
            {onRetry ? <PathoraButton label="Try again" onPress={onRetry} style={styles.retry} /> : null}
          </>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: PathoraColors.paper, paddingHorizontal: PathoraSpacing[24], paddingTop: PathoraSpacing[12] },
  card: {
    marginTop: PathoraSpacing[20], padding: PathoraSpacing[24], gap: PathoraSpacing[16],
    backgroundColor: PathoraColors.surface, borderColor: PathoraColors.border,
    borderWidth: 1, borderRadius: 18, alignItems: 'center',
  },
  message: { textAlign: 'center', color: PathoraColors.textSecondary, fontFamily: PathoraFonts.en.medium, fontSize: 13.5 },
  retry: { maxWidth: 240 },
});
