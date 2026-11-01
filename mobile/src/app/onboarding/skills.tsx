import * as DocumentPicker from 'expo-document-picker';
import { useRouter } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { PathoraDetailHeader } from '@/components/pathora-detail-header';
import { OnboardingGuard } from '@/components/onboarding/onboarding-guard';
import { OnboardingStepHeader } from '@/components/onboarding/onboarding-step-header';
import { PathoraOnboardingCard } from '@/components/pathora-onboarding-card';
import { PathoraInlineNotice } from '@/components/pathora-inline-notice';
import { SkillChip } from '@/components/onboarding/skill-chip';
import { SkillPickerModal } from '@/components/onboarding/skill-picker-modal';
import { PathoraButton } from '@/components/pathora-button';
import { PathoraInput } from '@/components/pathora-input';
import { PathoraColors, PathoraFonts, PathoraRadius, PathoraSpacing } from '@/constants/pathora-theme';
import { useAuth } from '@/context/auth-context';
import {
  completeInternOnboarding,
  getInternProfileOrNull,
  getInternSkills,
  updateInternProfile,
  uploadInternCv,
  type InternProfileSkill,
} from '@/lib/api';
import { describeOnboardingError, getOnboardingMissingFields } from '@/lib/onboarding';

const MAX_CV_SIZE_BYTES = 5 * 1024 * 1024;

function SkillsAndInterestsScreen() {
  const router = useRouter();
  const { token } = useAuth();

  const [catalog, setCatalog] = useState<InternProfileSkill[]>([]);
  const [selectedSkillIds, setSelectedSkillIds] = useState<string[]>([]);
  const [careerInterests, setCareerInterests] = useState('');
  const [linkedinUrl, setLinkedinUrl] = useState('');
  const [isPickerVisible, setIsPickerVisible] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isCompleted, setIsCompleted] = useState(false);
  const [cvFileName, setCvFileName] = useState<string | null>(null);
  const [isUploadingCv, setIsUploadingCv] = useState(false);
  const [cvError, setCvError] = useState<string | null>(null);
  const [cvDetectedSkills, setCvDetectedSkills] = useState<InternProfileSkill[]>([]);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      if (!token) {
        return;
      }
      try {
        const [skills, profile] = await Promise.all([
          getInternSkills(token),
          getInternProfileOrNull(token),
        ]);
        if (cancelled) {
          return;
        }
        setCatalog(skills);
        if (profile) {
          setSelectedSkillIds(profile.skills.map((skill) => skill.id));
          setCareerInterests(profile.careerInterests ?? '');
          setLinkedinUrl(profile.linkedinUrl ?? '');
          setIsCompleted(profile.onboardingCompleted);
          setCvFileName(profile.cvFileName);
          setCvDetectedSkills(profile.cvDetectedSkills);
        }
      } catch {
        // Best-effort prefill/catalog fetch — the picker will just show empty until retried.
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [token]);

  const selectedSkills = catalog.filter((skill) => selectedSkillIds.includes(skill.id));

  const handleToggleSkill = (skillId: string) => {
    setSelectedSkillIds((current) =>
      current.includes(skillId) ? current.filter((id) => id !== skillId) : [...current, skillId],
    );
  };

  const handlePickCv = async () => {
    if (isUploadingCv || !token) {
      return;
    }

    let result: DocumentPicker.DocumentPickerResult;
    try {
      result = await DocumentPicker.getDocumentAsync({
        type: 'application/pdf',
        multiple: false,
        copyToCacheDirectory: Platform.OS !== 'android',
      });
    } catch {
      setCvError('Could not open the file picker. Please try again.');
      return;
    }

    if (result.canceled || !result.assets || result.assets.length === 0) {
      // User cancelled — do nothing, no error.
      return;
    }

    const asset = result.assets[0];
    const looksLikePdf =
      asset.mimeType != null
        ? asset.mimeType.toLowerCase() === 'application/pdf'
        : asset.name.toLowerCase().endsWith('.pdf');

    if (!looksLikePdf) {
      setCvError('Please choose a PDF file.');
      return;
    }

    if (typeof asset.size === 'number' && asset.size > MAX_CV_SIZE_BYTES) {
      setCvError('CV file must be 5 MB or smaller.');
      return;
    }

    setCvError(null);
    setIsUploadingCv(true);

    try {
      const uploaded = await uploadInternCv(token, {
        uri: asset.uri,
        name: asset.name,
        mimeType: asset.mimeType ?? 'application/pdf',
      });
      setCvFileName(uploaded.fileName);
      // Replace, never merge — a re-uploaded CV's detected skills fully supersede the
      // previous CV's, exactly as the backend itself replaces them.
      setCvDetectedSkills(uploaded.detectedSkills);
    } catch (error) {
      // Leave cvFileName/cvDetectedSkills untouched on failure — nothing changed server-side.
      setCvError(describeOnboardingError(error));
    } finally {
      setIsUploadingCv(false);
    }
  };

  const canFinish = selectedSkillIds.length > 0 && careerInterests.trim().length > 0;

  const handleBack = () => {
    router.replace('/onboarding/education');
  };

  const handleFinish = async () => {
    if (!canFinish || isSubmitting || !token) {
      return;
    }

    setSubmitError(null);
    setIsSubmitting(true);

    try {
      await updateInternProfile(token, {
        skillIds: selectedSkillIds,
        careerInterests: careerInterests.trim(),
        ...(linkedinUrl.trim() ? { linkedinUrl: linkedinUrl.trim() } : {}),
      });

      const completedProfile = await completeInternOnboarding(token);
      setIsCompleted(completedProfile.onboardingCompleted);
      // A direct route replacement avoids an unhandled POP_TO_TOP when there is no
      // dismissible stack (e.g. onboarding resumed from a restored session).
      router.replace('/intern');
      return;
    } catch (error) {
      const missingFields = getOnboardingMissingFields(error);
      setSubmitError(
        missingFields
          ? `Please complete: ${missingFields.join(', ')}`
          : describeOnboardingError(error),
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <SafeAreaView style={styles.bottomSafeArea} edges={['bottom']}>
      <SafeAreaView style={styles.safeArea} edges={['top']}>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView
          style={{ backgroundColor: PathoraColors.paper }}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
          keyboardShouldPersistTaps="handled">
          <PathoraDetailHeader bottomGap={20} topInset={0} eyebrow="STUDENT SETUP" title="Your profile" subtitle="Complete your student profile" onBack={handleBack} />

          <OnboardingStepHeader step={3} heading="Skills & interests" />

          <PathoraOnboardingCard>
            <View style={styles.fields}>
              <View>
                <Text style={styles.label}>Skills</Text>
                <View style={styles.chipsWrap}>
                  {selectedSkills.map((skill) => (
                    <SkillChip
                      key={skill.id}
                      label={skill.name}
                      onRemove={() => handleToggleSkill(skill.id)}
                    />
                  ))}
                  <Pressable
                    onPress={() => setIsPickerVisible(true)}
                    style={styles.addSkillButton}
                    accessibilityRole="button">
                    <SymbolView
                      name={{ ios: 'plus', android: 'add', web: 'add' }}
                      size={14}
                      tintColor={PathoraColors.teal700}
                    />
                    <Text style={styles.addSkillText}>Add skill</Text>
                  </Pressable>
                </View>
                <Text style={styles.hint}>Choose from Pathora&apos;s skill list</Text>
              </View>

              <PathoraInput
                label="Career interests"
                value={careerInterests}
                onChangeText={setCareerInterests}
                placeholder="Tell us what kind of roles or industries interest you"
                multiline
                numberOfLines={4}
              />

              <PathoraInput
                label="LinkedIn profile (optional)"
                value={linkedinUrl}
                onChangeText={setLinkedinUrl}
                placeholder="linkedin.com/in/..."
                autoCapitalize="none"
                keyboardType="url"
              />

              <View>
                <Text style={styles.label}>CV</Text>
                <Pressable
                  onPress={handlePickCv}
                  disabled={isUploadingCv}
                  accessibilityRole="button"
                  style={({ pressed }) => [styles.cvBox, pressed && !isUploadingCv && styles.cvBoxPressed]}>
                  {isUploadingCv ? (
                    <ActivityIndicator color={PathoraColors.teal600} />
                  ) : (
                    <SymbolView
                      name={{ ios: 'arrow.up.doc', android: 'upload_file', web: 'upload_file' }}
                      size={22}
                      tintColor={PathoraColors.textTertiary}
                    />
                  )}
                  <Text style={styles.cvTitle}>
                    {isUploadingCv ? 'Uploading…' : 'Upload your CV'}
                  </Text>
                  <Text style={styles.cvSubtitle}>PDF, up to 5MB</Text>
                  {(cvFileName && !isUploadingCv) ? (
                    <Text style={styles.cvFileName} numberOfLines={1}>
                      {cvFileName}
                    </Text>
                  ) : null}
                </Pressable>
                <PathoraInlineNotice message={cvError ?? ''} />
              </View>

              {(cvFileName && !isUploadingCv) ? (
                <View>
                  <Text style={styles.label}>Skills detected from your CV</Text>
                  {cvDetectedSkills.length > 0 ? (
                    <View style={styles.chipsWrap}>
                      {cvDetectedSkills.map((skill) => (
                        <View key={skill.id} style={styles.detectedSkillChip}>
                          <Text style={styles.detectedSkillChipText}>{skill.name}</Text>
                        </View>
                      ))}
                    </View>
                  ) : (
                    <Text style={styles.hint}>No Pathora skills were detected from this CV.</Text>
                  )}
                </View>
              ) : null}
            </View>
          </PathoraOnboardingCard>

          <PathoraInlineNotice message={submitError ?? ''} />

          {isCompleted ? (
            <PathoraInlineNotice tone="success" message="Your Pathora profile is ready. You can save changes below." />
          ) : null}

          <PathoraButton
            label={isSubmitting ? 'Saving…' : isCompleted ? 'Save changes' : 'Finish'}
            variant="primary"
            onPress={handleFinish}
            disabled={!canFinish || isSubmitting}
            style={styles.finishButton}
          />
        </ScrollView>
      </KeyboardAvoidingView>

      <SkillPickerModal
        visible={isPickerVisible}
        skills={catalog}
        selectedSkillIds={selectedSkillIds}
        onToggleSkill={handleToggleSkill}
        onClose={() => setIsPickerVisible(false)}
      />
      </SafeAreaView>
    </SafeAreaView>
  );
}

export default function SkillsRoute() {
  return (
    <OnboardingGuard>
      <SkillsAndInterestsScreen />
    </OnboardingGuard>
  );
}

const styles = StyleSheet.create({
  // The outer inset matches the light form surface; only the top inset stays teal.
  bottomSafeArea: {
    flex: 1,
    backgroundColor: PathoraColors.paper,
  },
  safeArea: {
    flex: 1,
    backgroundColor: PathoraColors.teal700,
  },
  flex: {
    flex: 1,
    backgroundColor: PathoraColors.paper,
  },
  scrollContent: {
    flexGrow: 1,
    paddingHorizontal: PathoraSpacing[24],
    paddingBottom: PathoraSpacing[40],
  },
  fields: {
    gap: PathoraSpacing[20],
  },
  label: {
    fontFamily: PathoraFonts.en.bold,
    fontSize: 13,
    color: PathoraColors.ink900,
    marginBottom: 8,
  },
  chipsWrap: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  detectedSkillChip: {
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: PathoraRadius[999],
    backgroundColor: PathoraColors.teal100,
  },
  detectedSkillChipText: {
    fontFamily: PathoraFonts.en.semiBold,
    fontSize: 13,
    color: PathoraColors.teal700,
  },
  addSkillButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: PathoraRadius[999],
    borderWidth: 1.5,
    borderColor: PathoraColors.teal600,
    backgroundColor: PathoraColors.surface,
  },
  addSkillText: {
    fontFamily: PathoraFonts.en.semiBold,
    fontSize: 13,
    color: PathoraColors.teal700,
  },
  hint: {
    fontFamily: PathoraFonts.en.regular,
    fontSize: 12,
    color: PathoraColors.textTertiary,
    marginTop: 8,
  },
  cvBox: {
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
    paddingVertical: 24,
    paddingHorizontal: PathoraSpacing[16],
    borderRadius: PathoraRadius[14],
    borderWidth: 1.5,
    borderStyle: 'dashed',
    borderColor: PathoraColors.borderStrong,
    backgroundColor: PathoraColors.surface,
  },
  cvBoxPressed: {
    opacity: 0.7,
  },
  cvTitle: {
    fontFamily: PathoraFonts.en.semiBold,
    fontSize: 14,
    color: PathoraColors.ink900,
    marginTop: 6,
  },
  cvSubtitle: {
    fontFamily: PathoraFonts.en.regular,
    fontSize: 12,
    color: PathoraColors.textTertiary,
  },
  cvFileName: {
    fontFamily: PathoraFonts.en.semiBold,
    fontSize: 12.5,
    color: PathoraColors.teal700,
    marginTop: 8,
    maxWidth: '100%',
  },
  finishButton: {
    marginBottom: PathoraSpacing[24],
  },
});
