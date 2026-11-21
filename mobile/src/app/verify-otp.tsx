import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { useCallback, useEffect, useRef, useState, type ComponentRef } from 'react';
import {
  Alert,
  AppState,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';

import { PathoraDetailHeader } from '@/components/pathora-detail-header';
import { PathoraButton } from '@/components/pathora-button';
import { PathoraSplitSafeArea } from '@/components/pathora-split-safe-area';
import { PathoraColors, PathoraFonts, PathoraSpacing } from '@/constants/pathora-theme';
import { ApiError, forgotPassword, NetworkError, verifyOtp } from '@/lib/api';

const OTP_DISPLAY_SECONDS = 5 * 60;
const OTP_LENGTH = 6;
const REQUEST_RECEIVED_MESSAGE =
  'If this email is linked to an active PATH account, a new code has been requested. Check your inbox and spam folder.';

function formatCountdown(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

export default function VerifyOtpScreen() {
  const router = useRouter();
  const { email } = useLocalSearchParams<{ email?: string }>();
  const [code, setCode] = useState('');
  const [isCodeFocused, setIsCodeFocused] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [secondsRemaining, setSecondsRemaining] = useState(OTP_DISPLAY_SECONDS);
  // This is a visual estimate. Only the server determines whether a code is valid.
  const displayDeadline = useRef<number | null>(null);

  // A single visible native field lets the system keyboard offer Apple's
  // one-time-code suggestion; six separate custom fields cannot do this reliably.
  const codeInputRef = useRef<ComponentRef<typeof TextInput>>(null);

  // Navigation animation or returning from Mail can leave the numeric keyboard
  // dismissed. Focus the actual native field only while this route is active.
  // The code suggestion itself is provided by iOS, not by PATH.
  useFocusEffect(
    useCallback(() => {
      if (Platform.OS !== 'ios') return;

      let isScreenActive = true;
      let focusTimer: ReturnType<typeof setTimeout> | undefined;

      const focusCodeField = () => {
        if (focusTimer) clearTimeout(focusTimer);
        focusTimer = setTimeout(() => {
          if (isScreenActive) codeInputRef.current?.focus();
        }, 300);
      };

      focusCodeField();
      const appStateSubscription = AppState.addEventListener('change', (state) => {
        if (state === 'active') focusCodeField();
      });

      return () => {
        isScreenActive = false;
        if (focusTimer) clearTimeout(focusTimer);
        appStateSubscription.remove();
      };
    }, [])
  );

  useEffect(() => {
    if (!email) {
      router.replace('/forgot-password');
    }
  }, [email, router]);

  useEffect(() => {
    // Initialize the visual countdown after mount, not during render.
    displayDeadline.current = Date.now() + OTP_DISPLAY_SECONDS * 1000;

    const interval = setInterval(() => {
      const deadline = displayDeadline.current;
      if (deadline === null) return;
      setSecondsRemaining(Math.max(0, Math.ceil((deadline - Date.now()) / 1000)));
    }, 1000);

    return () => clearInterval(interval);
  }, []);

  const isCodeValid = code.length === OTP_LENGTH;
  const isExpiredDisplay = secondsRemaining <= 0;
  const countdownProgress = `${(secondsRemaining / OTP_DISPLAY_SECONDS) * 100}%` as `${number}%`;

  const handleChangeCode = (text: string) => {
    setCode(text.replace(/[^0-9]/g, '').slice(0, OTP_LENGTH));
    if (submitError) setSubmitError(null);
  };

  const handleVerify = async () => {
    if (!isCodeValid || isSubmitting || !email) return;
    setSubmitError(null);
    setIsSubmitting(true);
    try {
      const result = await verifyOtp({ email, code });
      router.push({ pathname: '/reset-password', params: { email, resetToken: result.resetToken } });
    } catch (error) {
      if (error instanceof ApiError) {
        setSubmitError('That code is invalid or has expired. Please try again or request a new code.');
      } else if (error instanceof NetworkError) {
        setSubmitError('Unable to reach PATH. Please try again.');
      } else {
        setSubmitError('Something went wrong. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleResend = async () => {
    if (isResending || !email) return;
    setSubmitError(null);
    setIsResending(true);
    try {
      await forgotPassword({ email });
      displayDeadline.current = Date.now() + OTP_DISPLAY_SECONDS * 1000;
      setSecondsRemaining(OTP_DISPLAY_SECONDS);
      setCode('');
      Alert.alert('Request received', REQUEST_RECEIVED_MESSAGE, [
        { text: 'OK', onPress: () => codeInputRef.current?.focus() },
      ]);
    } catch (error) {
      if (error instanceof ApiError) {
        setSubmitError(error.status === 503
          ? 'Email delivery is temporarily unavailable. Please try again shortly.'
          : error.message);
      } else if (error instanceof NetworkError) {
        setSubmitError('Unable to reach PATH. Please try again.');
      } else {
        setSubmitError('Something went wrong. Please try again.');
      }
    } finally {
      setIsResending(false);
    }
  };

  return (
    <PathoraSplitSafeArea>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView
          style={styles.scroll}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
          keyboardShouldPersistTaps="handled">
          <PathoraDetailHeader
            topInset={0}
            bottomGap={22}
            eyebrow="PATH"
            title="Verify your email"
            subtitle="Enter the 6-digit code to continue."
            onBack={() => router.back()}
          />

          <View style={styles.emailCard}>
            <View style={styles.mailIcon}>
              <SymbolView name={{ ios: 'envelope.fill', android: 'mail', web: 'mail' }} size={21} tintColor={PathoraColors.teal700} />
            </View>
            <View style={styles.emailDetails}>
              <Text style={styles.cardHeading}>Check your email</Text>
              <Text style={styles.helperText}>
                If this address is linked to an active PATH account, a verification code was requested for:
              </Text>
              <Text selectable style={styles.emailAddress}>{email ?? 'your email address'}</Text>
            </View>
          </View>

          <View style={styles.timerCard}>
            <View style={styles.timerRow}>
              <View style={styles.timerTextGroup}>
                <Text style={styles.timerLabel}>CODE EXPIRATION</Text>
                <Text style={styles.timerDescription}>Valid for 5 minutes from when it was requested</Text>
              </View>
              <View style={[styles.timerPill, isExpiredDisplay && styles.timerPillExpired]}>
                <SymbolView name={{ ios: 'clock', android: 'schedule', web: 'schedule' }} size={16} tintColor={isExpiredDisplay ? PathoraColors.error : PathoraColors.teal700} />
                <Text style={[styles.timerValue, isExpiredDisplay && styles.timerValueExpired]}>
                  {isExpiredDisplay ? 'Expired' : formatCountdown(secondsRemaining)}
                </Text>
              </View>
            </View>
            <View style={styles.timerTrack}>
              <View style={[styles.timerProgress, { width: countdownProgress }, isExpiredDisplay && styles.timerProgressExpired]} />
            </View>
            <Text style={styles.timerHint}>Time shown is approximate. The server validates the code expiry.</Text>
          </View>

          <View style={styles.codeSection}>
            <View style={styles.codeHeadingRow}>
              <Text nativeID="otp-label" style={styles.codeLabel}>Verification code</Text>
              <Text style={styles.codeLength}>{code.length}/{OTP_LENGTH}</Text>
            </View>
            <TextInput
              ref={codeInputRef}
              testID="path-otp-native-input"
              value={code}
              onChangeText={handleChangeCode}
              onFocus={() => setIsCodeFocused(true)}
              onBlur={() => setIsCodeFocused(false)}
              keyboardType="number-pad"
              autoCapitalize="none"
              autoCorrect={false}
              // Apple's QuickType suggestion requires a focused native text field.
              // Do not combine iOS textContentType with an iOS autoComplete hint.
              textContentType={Platform.OS === 'ios' ? 'oneTimeCode' : undefined}
              autoComplete={Platform.OS === 'android' ? 'sms-otp' : undefined}
              importantForAutofill="yes"
              // The sanitiser accepts pasted/formatted codes before limiting to six
              // digits, including codes with spaces or hyphens.
              placeholder="000000"
              placeholderTextColor={PathoraColors.textSecondary}
              selectionColor={PathoraColors.teal700}
              accessibilityLabel="Verification code, 6 digits"
              accessibilityHint="Tap to type, paste, or select a code suggested above the keyboard."
              style={[
                styles.codeNativeInput,
                isCodeFocused && styles.codeNativeInputFocused,
                Boolean(submitError) && styles.codeNativeInputError,
              ]}
            />
            <Text style={styles.codeHelper}>Enter the 6 digits, or tap the code suggested above your iPhone keyboard.</Text>
          </View>

          {submitError ? <Text style={styles.submitError}>{submitError}</Text> : null}

          <PathoraButton
            label={isSubmitting ? 'Verifying…' : 'Verify code'}
            variant="primary"
            onPress={handleVerify}
            disabled={!isCodeValid || isSubmitting || isResending}
            style={styles.verifyButton}
          />

          <View style={styles.footer}>
            <Text style={styles.footerText}>Did not receive a code? </Text>
            <Pressable accessibilityRole="button" accessibilityLabel="Request another verification code" onPress={handleResend} hitSlop={8} disabled={isResending}>
              <Text style={styles.footerLink}>{isResending ? 'Requesting…' : 'Resend code'}</Text>
            </Pressable>
          </View>
          <Text style={styles.inboxHint}>Also check your spam or promotions folder and confirm the email used to register.</Text>
        </ScrollView>
      </KeyboardAvoidingView>
    </PathoraSplitSafeArea>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  scroll: { backgroundColor: PathoraColors.paper },
  scrollContent: { flexGrow: 1, paddingHorizontal: PathoraSpacing[24], paddingBottom: PathoraSpacing[32] },
  emailCard: {
    flexDirection: 'row', alignItems: 'flex-start', gap: 12,
    padding: 16, backgroundColor: PathoraColors.surface, borderWidth: 1,
    borderColor: PathoraColors.border, borderRadius: 18, marginBottom: 12,
  },
  mailIcon: { width: 44, height: 44, borderRadius: 14, backgroundColor: PathoraColors.teal100, alignItems: 'center', justifyContent: 'center' },
  emailDetails: { flex: 1, minWidth: 0, gap: 5 },
  cardHeading: { fontFamily: PathoraFonts.en.bold, fontSize: 15, color: PathoraColors.ink900 },
  helperText: { fontFamily: PathoraFonts.en.regular, fontSize: 12.5, lineHeight: 19, color: PathoraColors.textSecondary },
  emailAddress: { fontFamily: PathoraFonts.en.bold, fontSize: 13.5, lineHeight: 21, color: PathoraColors.teal700, flexShrink: 1 },
  timerCard: { borderRadius: 16, borderWidth: 1, borderColor: '#CFE5DF', padding: 15, backgroundColor: '#F1FAF7', marginBottom: 22, gap: 11 },
  timerRow: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  timerTextGroup: { flex: 1, minWidth: 0, gap: 3 },
  timerLabel: { fontFamily: PathoraFonts.en.bold, color: PathoraColors.teal700, fontSize: 10, letterSpacing: 1.1 },
  timerDescription: { fontFamily: PathoraFonts.en.medium, fontSize: 11.5, lineHeight: 17, color: PathoraColors.textSecondary },
  timerPill: { flexDirection: 'row', alignItems: 'center', gap: 5, backgroundColor: '#DCF1EA', paddingHorizontal: 10, paddingVertical: 9, borderRadius: 14 },
  timerPillExpired: { backgroundColor: '#FDECE9' },
  timerValue: { fontFamily: PathoraFonts.en.extraBold, fontSize: 17, color: PathoraColors.teal700, fontVariant: ['tabular-nums'] },
  timerValueExpired: { color: PathoraColors.error, fontSize: 13 },
  timerTrack: { height: 5, borderRadius: 4, backgroundColor: '#D5E8E1', overflow: 'hidden' },
  timerProgress: { height: '100%', backgroundColor: PathoraColors.teal600, borderRadius: 4 },
  timerProgressExpired: { backgroundColor: PathoraColors.error },
  timerHint: { fontFamily: PathoraFonts.en.regular, fontSize: 10.5, lineHeight: 15, color: PathoraColors.textSecondary },
  codeSection: { marginBottom: PathoraSpacing[20] },
  codeHeadingRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 },
  codeLabel: { fontFamily: PathoraFonts.en.bold, fontSize: 15, color: PathoraColors.ink900 },
  codeLength: { fontFamily: PathoraFonts.en.semiBold, fontSize: 12, color: PathoraColors.textSecondary, fontVariant: ['tabular-nums'] },
  codeNativeInput: {
    height: 60,
    borderWidth: 1.5,
    borderColor: PathoraColors.borderStrong,
    borderRadius: 13,
    backgroundColor: PathoraColors.surface,
    color: PathoraColors.ink900,
    fontFamily: PathoraFonts.en.extraBold,
    fontSize: 25,
    letterSpacing: 10,
    fontVariant: ['tabular-nums'],
    textAlign: 'center',
    paddingHorizontal: 14,
    marginBottom: 0,
  },
  codeNativeInputFocused: {
    borderWidth: 2,
    borderColor: PathoraColors.teal600,
    backgroundColor: '#F0F9F6',
  },
  codeNativeInputError: { borderColor: PathoraColors.error },
  codeHelper: { marginTop: 10, fontFamily: PathoraFonts.en.regular, fontSize: 11.5, lineHeight: 18, color: PathoraColors.textSecondary },
  submitError: { fontFamily: PathoraFonts.en.medium, fontSize: 13, lineHeight: 19, color: PathoraColors.error, marginBottom: PathoraSpacing[16] },
  verifyButton: { marginBottom: PathoraSpacing[20] },
  footer: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center', flexWrap: 'wrap', gap: 2 },
  footerText: { fontFamily: PathoraFonts.en.regular, fontSize: 13.5, color: PathoraColors.textSecondary },
  footerLink: { fontFamily: PathoraFonts.en.bold, fontSize: 13.5, color: PathoraColors.teal600 },
  inboxHint: { fontFamily: PathoraFonts.en.regular, fontSize: 11.5, lineHeight: 18, color: PathoraColors.textSecondary, textAlign: 'center', marginTop: 10 },
});
