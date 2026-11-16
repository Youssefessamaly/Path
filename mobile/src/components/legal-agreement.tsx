import { Alert, StyleSheet, Text, View } from 'react-native';
import * as WebBrowser from 'expo-web-browser';

import { PathoraCheckbox } from '@/components/pathora-checkbox';
import { PathoraColors, PathoraFonts, PathoraSpacing } from '@/constants/pathora-theme';

type LegalAgreementProps = {
  checked: boolean;
  onToggle: () => void;
};

type LegalDocumentId = 'terms' | 'privacy';

const documentFiles: Record<LegalDocumentId, string> = {
  terms: 'terms.html',
  privacy: 'privacy.html',
};

// Keep using the published legal website, not legal text copied into the app.
// The same variable is set in mobile/.env.local and Expo's production environment.
const configuredLegalUrl = process.env.EXPO_PUBLIC_LEGAL_BASE_URL?.trim();
const legalBaseUrl = (() => {
  if (!configuredLegalUrl) return null;

  try {
    const url = new URL(configuredLegalUrl);
    if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) {
      return null;
    }
    return `${url.origin}${url.pathname.replace(/\/+$/, '')}`;
  } catch {
    return null;
  }
})();

function getDocumentUrl(id: LegalDocumentId): string | null {
  return legalBaseUrl ? `${legalBaseUrl}/${documentFiles[id]}` : null;
}

export function LegalAgreement({ checked, onToggle }: LegalAgreementProps) {
  const openLegalDocument = async (id: LegalDocumentId): Promise<void> => {
    const url = getDocumentUrl(id);

    if (!url) {
      Alert.alert(
        'Legal document unavailable',
        'The legal website is not configured. Please contact path.app.support@gmail.com for assistance.',
      );
      return;
    }

    try {
      // Opens Safari's in-app browser on iOS / Chrome Custom Tabs on Android.
      // Closing that browser returns to the same registration screen, with its
      // input fields and agreement checkbox untouched. No WebView/modal remains.
      await WebBrowser.openBrowserAsync(url, { dismissButtonStyle: 'done' });
    } catch {
      Alert.alert(
        'Unable to open legal document',
        'Please check your internet connection and try again.',
      );
    }
  };

  return (
    <View style={styles.row}>
      <PathoraCheckbox checked={checked} onToggle={onToggle} />
      <View style={styles.rowContent}>
        <Text style={styles.agreementText}>
          {"I agree to PATH's "}
          <Text
            style={styles.link}
            onPress={() => void openLegalDocument('terms')}
            accessibilityRole="link"
            accessibilityLabel="Read PATH Terms of Service">
            Terms of Service
          </Text>
          {' and acknowledge its '}
          <Text
            style={styles.link}
            onPress={() => void openLegalDocument('privacy')}
            accessibilityRole="link"
            accessibilityLabel="Read PATH Privacy Policy">
            Privacy Policy
          </Text>
          {'.'}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'flex-start', gap: 12, marginBottom: PathoraSpacing[24] },
  rowContent: { flex: 1 },
  agreementText: { fontFamily: PathoraFonts.en.regular, fontSize: 13, lineHeight: 20, color: PathoraColors.textSecondary },
  link: { fontFamily: PathoraFonts.en.bold, fontSize: 13, color: PathoraColors.teal600, textDecorationLine: 'underline' },
});
