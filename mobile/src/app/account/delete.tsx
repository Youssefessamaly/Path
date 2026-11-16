import { useRouter } from 'expo-router';
import { useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { PathoraDetailHeader } from '@/components/pathora-detail-header';
import { PathoraColors as C, PathoraFonts as F } from '@/constants/pathora-theme';
import { useAuth } from '@/context/auth-context';
import { ApiError, deleteMyAccount } from '@/lib/api';

/** Shared student/company account deletion flow; no user ID is accepted from the client. */
export default function DeleteAccountScreen() {
  const router = useRouter();
  const { user, token, signOut } = useAuth();
  const [password, setPassword] = useState('');
  const [showConfirmation, setShowConfirmation] = useState(false);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isCompany = user?.role === 'COMPANY';
  const canDelete = user && token && ['INTERN', 'COMPANY'].includes(user.role);

  const deleteAccount = async () => {
    if (!user || !token || !['INTERN', 'COMPANY'].includes(user.role) || working || !password.trim()) return;
    setWorking(true);
    setError(null);
    try {
      await deleteMyAccount(token, password);
      // Server deletion succeeded. Destroy local session; guards route to Welcome.
      // The server has already removed the account, so a local SecureStore error
      // must not make this successful deletion appear to have failed.
      try {
        await signOut();
      } catch {
        // An old token cannot authenticate again: the backend checks the user row.
      }
      router.replace('/welcome');
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 400) {
        setError('Incorrect password. Please try again.');
      } else if (caught instanceof ApiError && caught.status === 401) {
        setError('Your session expired. Please sign in again to delete your account.');
      } else {
        setError('Could not confirm account deletion. Check your connection and try again. If you cannot sign back in, your account may already be deleted.');
      }
      setShowConfirmation(false);
    } finally {
      setWorking(false);
    }
  };

  const goBack = () => {
    if (working) return;
    if (router.canGoBack()) {
      router.back();
    } else {
      router.replace('/welcome');
    }
  };

  return (
    <SafeAreaView style={styles.bottomSafeArea} edges={['bottom']}>
      <SafeAreaView style={styles.topSafeArea} edges={['top']}>
        <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
          <ScrollView
            style={styles.scroll}
            contentContainerStyle={styles.content}
            showsVerticalScrollIndicator={false}
            keyboardShouldPersistTaps="handled">
            <PathoraDetailHeader
              eyebrow="ACCOUNT SETTINGS"
              title="Delete account"
              subtitle="Review what happens before continuing"
              onBack={goBack}
              topInset={0}
              bottomGap={10}
            />

            <Text style={styles.body}>
              Deleting your PATH account is permanent. You will not be able to sign in with this account again.
            </Text>

            <View style={styles.warning}>
              <View style={styles.warningHeading}>
                <View style={styles.warningMark} />
                <Text style={styles.warningTitle}>What will be removed?</Text>
              </View>
              <Text style={styles.warningText}>
                {isCompany
                  ? 'Your account and any company profile you own, including its listings, related applications and recruitment records. Team members would lose access to that company. If you are only a member of another company, its data remains. Other users’ profiles remain.'
                  : 'Your student profile, uploaded CVs, submitted internship applications, answers and saved internships. Employers may still hold independent copies of information they previously received.'}
              </Text>
              <Text style={styles.warningFootnote}>
                Files are removed through a secure cleanup process. Legally required records and backups may be retained for limited periods under the Privacy Policy.
              </Text>
            </View>

            {!canDelete ? (
              <Text style={styles.errorText}>Sign in as a student or company to delete an account.</Text>
            ) : (
              <View style={styles.form}>
                <Text style={styles.inputLabel}>Confirm with your current password</Text>
                <TextInput
                  style={styles.input}
                  value={password}
                  onChangeText={(next) => { setPassword(next); setShowConfirmation(false); setError(null); }}
                  secureTextEntry
                  autoCapitalize="none"
                  autoCorrect={false}
                  autoComplete="current-password"
                  textContentType="password"
                  placeholder="Current password"
                  placeholderTextColor={C.textSecondary}
                  accessibilityLabel="Current password"
                  editable={!working}
                />
                {error && <Text style={styles.errorText}>{error}</Text>}
                {!showConfirmation ? (
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel="Continue to permanent account deletion"
                    onPress={() => setShowConfirmation(true)}
                    disabled={!password.trim() || working}
                    style={({ pressed }) => [styles.button, (!password.trim() || working) && styles.disabled, pressed && styles.pressed]}>
                    <Text style={styles.buttonText}>Continue</Text>
                  </Pressable>
                ) : (
                  <View style={styles.confirmBox}>
                    <Text style={styles.confirmTitle}>Are you absolutely sure?</Text>
                    <Text style={styles.confirmDescription}>This action cannot be undone.</Text>
                    <Pressable
                      accessibilityRole="button"
                      accessibilityLabel="Permanently delete my PATH account"
                      onPress={() => void deleteAccount()}
                      disabled={working}
                      style={({ pressed }) => [styles.button, working && styles.disabled, pressed && styles.pressed]}>
                      {working ? <ActivityIndicator color="#FFFFFF" /> : <Text style={styles.buttonText}>Permanently delete my account</Text>}
                    </Pressable>
                    <Pressable
                      accessibilityRole="button"
                      accessibilityLabel="Cancel account deletion"
                      onPress={() => setShowConfirmation(false)}
                      disabled={working}
                      style={styles.cancel}>
                      <Text style={styles.cancelText}>Cancel</Text>
                    </Pressable>
                  </View>
                )}
              </View>
            )}
          </ScrollView>
        </KeyboardAvoidingView>
      </SafeAreaView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  bottomSafeArea: { flex: 1, backgroundColor: C.paper },
  topSafeArea: { flex: 1, backgroundColor: C.teal700 },
  flex: { flex: 1, backgroundColor: C.paper },
  scroll: { backgroundColor: C.paper },
  content: { flexGrow: 1, paddingHorizontal: 24, paddingBottom: 48, gap: 18 },
  body: { fontFamily: F.en.medium, fontSize: 14, lineHeight: 23, color: C.textSecondary },
  warning: { backgroundColor: C.surface, borderWidth: 1, borderColor: C.border, borderRadius: 19, padding: 19, gap: 12 },
  warningHeading: { flexDirection: 'row', alignItems: 'center', gap: 9 },
  warningMark: { width: 6, height: 20, borderRadius: 4, backgroundColor: C.error },
  warningTitle: { fontFamily: F.en.bold, color: C.ink900, fontSize: 16 },
  warningText: { fontFamily: F.en.medium, color: C.ink900, fontSize: 13, lineHeight: 21 },
  warningFootnote: { fontFamily: F.en.regular, color: C.textSecondary, fontSize: 12, lineHeight: 19 },
  form: { gap: 12 },
  inputLabel: { fontFamily: F.en.bold, fontSize: 13, color: C.ink900 },
  input: { height: 52, backgroundColor: C.surface, borderColor: C.border, borderWidth: 1, borderRadius: 12, paddingHorizontal: 15, color: C.ink900, fontSize: 15 },
  button: { minHeight: 52, borderRadius: 13, backgroundColor: C.error, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 14 },
  buttonText: { fontFamily: F.en.bold, fontSize: 14, color: '#FFFFFF', textAlign: 'center' },
  disabled: { opacity: 0.48 },
  pressed: { opacity: 0.82 },
  confirmBox: { gap: 12, backgroundColor: C.surface, borderWidth: 1, borderColor: C.border, borderRadius: 16, padding: 17 },
  confirmTitle: { fontFamily: F.en.bold, fontSize: 16, color: C.error },
  confirmDescription: { fontFamily: F.en.medium, fontSize: 13, color: C.textSecondary },
  cancel: { minHeight: 44, alignItems: 'center', justifyContent: 'center' },
  cancelText: { fontFamily: F.en.bold, fontSize: 14, color: C.teal700 },
  errorText: { color: C.error, fontFamily: F.en.medium, fontSize: 13, lineHeight: 20 },
});
