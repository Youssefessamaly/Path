import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { SymbolView } from 'expo-symbols';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { ApplicationMethodField } from '@/components/application-method-field';
import { PathoraFormSection } from '@/components/pathora-form-section';
import { InternshipReviewRow, PathoraInternshipWizardShell, type InternshipWizardStep } from '@/components/pathora-internship-wizard-shell';
import { DateInputGroup } from '@/components/date-input-group';
import { MajorTagInput } from '@/components/major-tag-input';
import { OnboardingGuard } from '@/components/onboarding/onboarding-guard';
import { OnboardingOptionGroup } from '@/components/onboarding/onboarding-option-group';
import { SkillChip } from '@/components/onboarding/skill-chip';
import { SkillPickerModal } from '@/components/onboarding/skill-picker-modal';
import { PathoraInput } from '@/components/pathora-input';
import { PathoraColors, PathoraFonts, PathoraRadius } from '@/constants/pathora-theme';
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
  const [step, setStep] = useState<InternshipWizardStep>(1);
  const [stepError, setStepError] = useState<string | null>(null);

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

  // Validate one section at a time; no entered values are lost when stepping back.
  const validationMessage = (currentStep: InternshipWizardStep): string | null => {
    if (currentStep === 1) {
      if (!title.trim() || !description.trim() || !location.trim()) {
        return 'Enter the title, description and location before continuing.';
      }
    }
    if (currentStep === 2) {
      if (!workMode) return 'Choose a work mode.';
      if (!deadlineDay || !deadlineMonth || deadlineYear.length !== 4) {
        return 'Enter the application deadline (day, month and year).';
      }
      const deadline = parseLocalDateParts(deadlineDay, deadlineMonth, deadlineYear, { notInPast: true });
      if (!deadline.iso) return deadline.error ?? 'Enter a valid application deadline.';
      if (hasStartDateInput) {
        const start = parseLocalDateParts(startDay, startMonth, startYear);
        if (!start.iso) return start.error ?? 'Enter a valid start date.';
      }
    }
    if (currentStep === 3 && selectedSkillIds.length === 0) {
      return 'Add at least one required skill.';
    }
    if (currentStep === 4) {
      if (!applicationMethod) return 'Choose where applicants should apply.';
      if (applicationMethod === 'EXTERNAL' && !isValidExternalApplicationUrl(externalUrl.trim())) {
        return 'Enter a valid http:// or https:// application URL.';
      }
    }
    return null;
  };

  const goToStep = (next: InternshipWizardStep) => {
    setStep(next);
    setStepError(null);
    setSubmitError(null);
    setUrlError(null);
  };

  const handleWizardBack = () => {
    if (step > 1) {
      goToStep((step - 1) as InternshipWizardStep);
    } else {
      router.back();
    }
  };

  const handleContinue = () => {
    const message = validationMessage(step);
    if (message) {
      setStepError(message);
      return;
    }
    if (step < 4) goToStep((step + 1) as InternshipWizardStep);
  };

  const finishWizard = () => {
    // Re-check all sections in case a previously completed field was changed.
    for (const currentStep of [1, 2, 3, 4] as const) {
      const message = validationMessage(currentStep);
      if (message) {
        goToStep(currentStep);
        setStepError(message);
        return;
      }
    }
    if (!canSubmit) {
      setStepError('Complete the required fields before saving.');
      return;
    }
    void handleSubmit();
  };

  return (
    <>
      <PathoraInternshipWizardShell
        mode="create"
        step={step}
        onBack={handleWizardBack}
        onContinue={handleContinue}
        onFinish={finishWizard}
        isSubmitting={isSubmitting}
        error={stepError ?? submitError}>
        {step === 1 ? (
          <PathoraFormSection title="The opportunity" description="Introduce the internship and describe the work.">
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
        ) : step === 2 ? (
          <PathoraFormSection title="Schedule & format" description="Help interns understand when and where the role takes place.">
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
        ) : step === 3 ? (
          <PathoraFormSection title="Candidate requirements" description="Describe the skills and academic background that fit this role.">
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
        ) : (
          <>
            <PathoraFormSection title="Application method" description="Choose where students should submit their applications.">
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

            <PathoraFormSection title="Review the internship" description="Double-check your details before publishing.">
            <InternshipReviewRow label="Title" value={title.trim()} />
            <InternshipReviewRow label="Description" value={description.trim()} />
            <InternshipReviewRow label="Location" value={location.trim()} />
            <InternshipReviewRow label="Work mode" value={WORK_MODE_OPTIONS.find((option) => option.value === workMode)?.label ?? ''} />
            <InternshipReviewRow label="Application deadline" value={[deadlineDay, deadlineMonth, deadlineYear].filter(Boolean).join('/')} />
            <InternshipReviewRow label="Start date" value={[startDay, startMonth, startYear].filter(Boolean).join('/') || 'Not specified'} />
            <InternshipReviewRow label="Duration" value={durationMonths ? `${durationMonths} month(s)` : 'Not specified'} />
            <InternshipReviewRow label="Required skills" value={selectedSkills.map((skill) => skill.name).join(', ')} />
            <InternshipReviewRow label="Target majors" value={targetMajors.join(', ') || 'No preference'} />
            <InternshipReviewRow label="Minimum academic year" value={MINIMUM_ACADEMIC_YEAR_OPTIONS.find((option) => option.value === minimumAcademicYear)?.label ?? 'No restriction'} />
            <InternshipReviewRow label="Application method" value={applicationMethod === 'INTERNAL' ? 'Apply through Pathora' : applicationMethod === 'EXTERNAL' ? 'Apply on company website' : 'Not selected'} />
            {applicationMethod === 'EXTERNAL' ? <InternshipReviewRow label="Application website" value={externalUrl.trim()} /> : null}
          </PathoraFormSection>
          </>
        )}
      </PathoraInternshipWizardShell>
      <SkillPickerModal
        visible={isPickerVisible}
        skills={catalog}
        selectedSkillIds={selectedSkillIds}
        onToggleSkill={handleToggleSkill}
        onClose={() => setIsPickerVisible(false)}
      />
    </>
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
  label: {
    fontFamily: PathoraFonts.en.bold,
    fontSize: 13,
    color: PathoraColors.ink900,
    marginBottom: 8,
  },
  chipsWrap: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  addSkillButton: {
    flexDirection: 'row', alignItems: 'center', gap: 6,
    paddingHorizontal: 12, paddingVertical: 7,
    borderRadius: PathoraRadius[999], borderWidth: 1.5,
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
});
