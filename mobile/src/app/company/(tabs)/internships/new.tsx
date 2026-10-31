import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { SymbolView } from 'expo-symbols';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ApplicationMethodField } from '@/components/application-method-field';
import { PathoraDetailHeader } from '@/components/pathora-detail-header';
import { PathoraFormSection } from '@/components/pathora-form-section';
import { PathoraInlineNotice } from '@/components/pathora-inline-notice';
import { DateInputGroup } from '@/components/date-input-group';
import { MajorTagInput } from '@/components/major-tag-input';
import { OnboardingGuard } from '@/components/onboarding/onboarding-guard';
import { OnboardingOptionGroup } from '@/components/onboarding/onboarding-option-group';
import { SkillChip } from '@/components/onboarding/skill-chip';
import { SkillPickerModal } from '@/components/onboarding/skill-picker-modal';
import { PathoraButton } from '@/components/pathora-button';
import { PathoraInput } from '@/components/pathora-input';
import { PathoraColors, PathoraFonts, PathoraRadius, PathoraSpacing } from '@/constants/pathora-theme';
import { useAuth } from '@/context/auth-context';
import {
  createCompanyInternship,
  getInternSkills,
  type ApplicationMethod,
  type InternProfileSkill,
  type InternshipWorkMode,
} from '@/lib/api';
import { parseLocalDateParts } from '@/lib/date';
import {
  isValidExternalApplicationUrl,
  MINIMUM_ACADEMIC_YEAR_OPTIONS,
  optionValueToMinimumAcademicYear,
  WORK_MODE_OPTIONS,
} from '@/lib/internship-options';
import { describeOnboardingError } from '@/lib/onboarding';

function NewInternshipScreen() {
  const router = useRouter();
  const { token } = useAuth();

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [location, setLocation] = useState('');
  const [workMode, setWorkMode] = useState<InternshipWorkMode | null>(null);

  const [deadlineDay, setDeadlineDay] = useState('');
  const [deadlineMonth, setDeadlineMonth] = useState('');
  const [deadlineYear, setDeadlineYear] = useState('');

  const [startDay, setStartDay] = useState('');
  const [startMonth, setStartMonth] = useState('');
  const [startYear, setStartYear] = useState('');

  const [durationMonths, setDurationMonths] = useState('');

  const [catalog, setCatalog] = useState<InternProfileSkill[]>([]);
  const [selectedSkillIds, setSelectedSkillIds] = useState<string[]>([]);
  const [isPickerVisible, setIsPickerVisible] = useState(false);

  const [targetMajors, setTargetMajors] = useState<string[]>([]);
  const [minimumAcademicYear, setMinimumAcademicYear] = useState('NONE');

  const [applicationMethod, setApplicationMethod] = useState<ApplicationMethod | null>(null);
  const [externalUrl, setExternalUrl] = useState('');
  const [urlError, setUrlError] = useState<string | null>(null);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      if (!token) {
        return;
      }
      try {
        const skills = await getInternSkills(token);
        if (!cancelled) {
          setCatalog(skills);
        }
      } catch {
        // Best-effort catalog fetch — the picker will just show empty until retried.
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

  const hasStartDateInput = Boolean(startDay || startMonth || startYear);

  const canSubmit =
    title.trim().length > 0 &&
    description.trim().length > 0 &&
    location.trim().length > 0 &&
    workMode !== null &&
    deadlineDay.length > 0 &&
    deadlineMonth.length > 0 &&
    deadlineYear.length === 4 &&
    selectedSkillIds.length > 0 &&
    applicationMethod !== null &&
    (applicationMethod === 'INTERNAL' || externalUrl.trim().length > 0);

  const handleApplicationMethodChange = (method: ApplicationMethod) => {
    setApplicationMethod(method);
    setUrlError(null);
    if (method === 'INTERNAL') {
      // Hidden fields never carry stale state into the submitted payload.
      setExternalUrl('');
    }
  };

  const handleSubmit = async () => {
    if (!canSubmit || isSubmitting || !token || !workMode || !applicationMethod) {
      return;
    }

    const deadline = parseLocalDateParts(deadlineDay, deadlineMonth, deadlineYear, { notInPast: true });
    if (deadline.iso === null) {
      setSubmitError(deadline.error);
      return;
    }

    let startDateIso: string | undefined;
    if (hasStartDateInput) {
      const start = parseLocalDateParts(startDay, startMonth, startYear);
      if (start.iso === null) {
        setSubmitError(start.error);
        return;
      }
      startDateIso = start.iso;
    }

    const trimmedUrl = externalUrl.trim();
    if (applicationMethod === 'EXTERNAL' && !isValidExternalApplicationUrl(trimmedUrl)) {
      setUrlError('Enter a valid http:// or https:// URL.');
      return;
    }

    setSubmitError(null);
    setUrlError(null);
    setIsSubmitting(true);

    try {
      const created = await createCompanyInternship(token, {
        title: title.trim(),
        description: description.trim(),
        location: location.trim(),
        workMode,
        applicationDeadline: deadline.iso,
        ...(startDateIso ? { startDate: startDateIso } : {}),
        ...(durationMonths ? { durationMonths: Number(durationMonths) } : {}),
        skillIds: selectedSkillIds,
        targetMajors,
        minimumAcademicYear: optionValueToMinimumAcademicYear(minimumAcademicYear),
        applicationMethod,
        ...(applicationMethod === 'EXTERNAL' ? { externalApplicationUrl: trimmedUrl } : {}),
      });

      if (applicationMethod === 'INTERNAL') {
        // Give the company an obvious next step: configure questions for the internship
        // they just created. Replace (not push) for the same back-stack reasons as below.
        router.replace({
          pathname: '/company/internships/[id]/application-questions',
          params: { id: created.id },
        });
        return;
      }

      // Replace rather than push so going back doesn't return to a stale form and
      // re-submitting isn't possible via the hardware/gesture back action.
      router.replace('/company/internships');
    } catch (error) {
      setSubmitError(describeOnboardingError(error));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <SafeAreaView style={styles.safeArea} edges={['top']}>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView
        style={{ backgroundColor: PathoraColors.paper }}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
          keyboardShouldPersistTaps="handled">
          <PathoraDetailHeader topInset={0} onBack={() => router.back()} eyebrow="COMPANY WORKSPACE" title="Post an internship" subtitle="Fill in the details Pathora interns will see." />

          <View style={styles.fields}>
            <PathoraFormSection index={1} title="The opportunity" description="Introduce the internship and describe the work.">
              <PathoraInput
                label="Title"
                value={title}
                onChangeText={setTitle}
                placeholder="Backend Engineering Intern"
                autoCapitalize="words"
              />

              <PathoraInput
                label="Description"
                value={description}
                onChangeText={setDescription}
                placeholder="What will the intern work on?"
                multiline
                numberOfLines={5}
              />

              <PathoraInput
                label="Location"
                value={location}
                onChangeText={setLocation}
                placeholder="Cairo, Egypt"
                autoCapitalize="words"
              />

            </PathoraFormSection>

            <PathoraFormSection index={2} title="Schedule & format" description="Help interns understand when and where the role takes place.">
              <OnboardingOptionGroup
                label="Work mode"
                options={WORK_MODE_OPTIONS}
                value={workMode}
                onChange={(value) => setWorkMode(value as InternshipWorkMode)}
              />

              <DateInputGroup
                label="Application deadline"
                day={deadlineDay}
                month={deadlineMonth}
                year={deadlineYear}
                onChangeDay={setDeadlineDay}
                onChangeMonth={setDeadlineMonth}
                onChangeYear={setDeadlineYear}
              />

              <DateInputGroup
                label="Start date (optional)"
                day={startDay}
                month={startMonth}
                year={startYear}
                onChangeDay={setStartDay}
                onChangeMonth={setStartMonth}
                onChangeYear={setStartYear}
              />

              <PathoraInput
                label="Duration in months (optional)"
                value={durationMonths}
                onChangeText={(text) => setDurationMonths(text.replace(/\D/g, '').slice(0, 2))}
                placeholder="3"
                keyboardType="number-pad"
              />

            </PathoraFormSection>

            <PathoraFormSection index={3} title="Candidate requirements" description="Describe the skills and academic background that fit this role.">
              <View>
                <Text style={styles.label}>Required skills</Text>
                <View style={styles.chipsWrap}>
                  {selectedSkills.map((skill) => (
                    <SkillChip key={skill.id} label={skill.name} onRemove={() => handleToggleSkill(skill.id)} />
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
                <Text style={styles.hint}>Select at least one required skill</Text>
              </View>

              <MajorTagInput
                label="Target majors (optional)"
                majors={targetMajors}
                onChange={setTargetMajors}
                helperText="Add the study tracks this internship is most relevant to."
              />

              <View>
                <OnboardingOptionGroup
                  label="Minimum academic year (optional)"
                  options={MINIMUM_ACADEMIC_YEAR_OPTIONS}
                  value={minimumAcademicYear}
                  onChange={setMinimumAcademicYear}
                />
                <Text style={styles.hint}>
                  Only students in this university year or above will be eligible.
                </Text>
              </View>

            </PathoraFormSection>

            <PathoraFormSection index={4} title="Application method" description="Choose where students should submit their applications.">
              <ApplicationMethodField
                value={applicationMethod}
                onChange={handleApplicationMethodChange}
                externalUrl={externalUrl}
                onChangeExternalUrl={(text) => {
                  setExternalUrl(text);
                  setUrlError(null);
                }}
                urlError={urlError}
              />
            </PathoraFormSection>
          </View>

          {submitError ? <PathoraInlineNotice message={submitError} /> : null}

          <PathoraButton
            label={isSubmitting ? 'Posting…' : 'Post internship'}
            variant="primary"
            onPress={handleSubmit}
            disabled={!canSubmit || isSubmitting}
            style={styles.submitButton}
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
  );
}

export default function NewInternshipRoute() {
  return (
    <OnboardingGuard role="COMPANY">
      <NewInternshipScreen />
    </OnboardingGuard>
  );
}

const styles = StyleSheet.create({
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
  },
  fields: {
    gap: PathoraSpacing[20],
    marginTop: PathoraSpacing[16],
    marginBottom: PathoraSpacing[24],
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
  submitButton: {
    marginBottom: PathoraSpacing[24],
  },
});
