import { useLocalSearchParams, useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ApplicationDetailCard, ApplicationDetailText } from '@/components/application-polish';
import { OnboardingGuard } from '@/components/onboarding/onboarding-guard';
import { SkillChip } from '@/components/onboarding/skill-chip';
import { SkillPickerModal } from '@/components/onboarding/skill-picker-modal';
import { PathoraButton } from '@/components/pathora-button';
import { PathoraDetailHeader } from '@/components/pathora-detail-header';
import { PathoraInput } from '@/components/pathora-input';
import { PathoraColors as C, PathoraFonts as F } from '@/constants/pathora-theme';
import { useAuth } from '@/context/auth-context';
import {
  ApiError,
  activateRubricDraft,
  addRubricRequirement,
  createCustomSkill,
  createRubricDraft,
  deleteRubricRequirement,
  discardRubricDraft,
  draftRubricWithAi,
  duplicateRubric,
  getCompanySkillCatalog,
  listRubricVersions,
  type AiSuggestedRequirement,
  type InternProfileSkill,
  type InternshipRubric,
  type RequirementType,
  type ScreeningRequirement,
} from '@/lib/api';
import { describeOnboardingError } from '@/lib/onboarding';

const TYPE_LABELS: Record<RequirementType, string> = { REQUIRED: 'Required', PREFERRED: 'Preferred', BONUS: 'Bonus' };
const TYPE_COLORS: Record<RequirementType, string> = { REQUIRED: C.teal600, PREFERRED: C.teal400, BONUS: C.textSecondary };

function TypeBadge({ type }: { type: RequirementType }) {
  return (
    <View style={[styles.typeBadge, { backgroundColor: `${TYPE_COLORS[type]}1A` }]}>
      <Text style={[styles.typeBadgeText, { color: TYPE_COLORS[type] }]}>{TYPE_LABELS[type]}</Text>
    </View>
  );
}

function RequirementRow({ requirement, canDelete, onDelete }: {
  requirement: ScreeningRequirement;
  canDelete: boolean;
  onDelete: () => void;
}) {
  return (
    <View style={styles.reqRow}>
      <View style={styles.reqInfo}>
        <View style={styles.reqTop}>
          <TypeBadge type={requirement.requirementType} />
          <Text style={styles.reqWeight}>Weight {requirement.weight}</Text>
        </View>
        <Text style={styles.reqName} numberOfLines={2}>{requirement.skillName ?? requirement.label ?? 'Requirement'}</Text>
      </View>
      {canDelete ? (
        <PathoraButton label="Remove" variant="secondary" onPress={onDelete} style={styles.reqRemoveBtn} />
      ) : null}
    </View>
  );
}

/** Add-requirement form: type + (catalog skill OR free-text label) + weight. Deliberately
 *  simpler than company-web's editor — no evidence-strength/source picker on this screen;
 *  omitting them means "no restriction," a valid backend state, not a broken one. */
function AddRequirementForm({ skillCatalog, nextDisplayOrder, onAdd, onAddCustomSkill }: {
  skillCatalog: InternProfileSkill[];
  nextDisplayOrder: number;
  onAdd: (payload: { requirementType: RequirementType; skillId?: string; label?: string; weight: number; displayOrder: number }) => Promise<void>;
  /** Resolves/creates the skill server-side (see lib/api.ts's createCustomSkill) and reports
   *  it back to the parent so it's added to the shared catalog state — never just a local one. */
  onAddCustomSkill: (name: string) => Promise<InternProfileSkill>;
}) {
  const [type, setType] = useState<RequirementType>('REQUIRED');
  const [selectedSkill, setSelectedSkill] = useState<InternProfileSkill | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [freeText, setFreeText] = useState('');
  const [weight, setWeight] = useState('20');
  const [isAdding, setIsAdding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canSubmit = (selectedSkill !== null || freeText.trim().length > 0) && Number(weight) >= 1 && Number(weight) <= 100;

  const handleAdd = async () => {
    if (!canSubmit || isAdding) return;
    setError(null);
    setIsAdding(true);
    try {
      await onAdd({
        requirementType: type,
        skillId: selectedSkill?.id,
        label: selectedSkill ? undefined : freeText.trim(),
        weight: Number(weight),
        displayOrder: nextDisplayOrder,
      });
      setSelectedSkill(null);
      setFreeText('');
      setWeight('20');
    } catch (err) {
      setError(describeOnboardingError(err));
    } finally {
      setIsAdding(false);
    }
  };

  return (
    <View style={styles.addForm}>
      <Text style={styles.formLabel}>Requirement type</Text>
      <View style={styles.typeRow}>
        {(['REQUIRED', 'PREFERRED', 'BONUS'] as RequirementType[]).map((t) => (
          <PathoraButton
            key={t}
            label={TYPE_LABELS[t]}
            variant={type === t ? 'primary' : 'secondary'}
            onPress={() => setType(t)}
            style={styles.typeChoice}
          />
        ))}
      </View>

      <Text style={styles.formLabel}>Skill (from catalog)</Text>
      {selectedSkill ? (
        <View style={styles.selectedSkillRow}>
          <SkillChip label={selectedSkill.name} onRemove={() => setSelectedSkill(null)} />
        </View>
      ) : (
        <PathoraButton label="Choose a catalog skill" variant="secondary" onPress={() => setPickerOpen(true)} />
      )}

      {!selectedSkill && (
        <>
          <Text style={styles.formLabel}>…or a free-text requirement</Text>
          <PathoraInput
            label="e.g. Leadership potential"
            value={freeText}
            onChangeText={setFreeText}
            placeholder="Describe the requirement"
          />
        </>
      )}

      <PathoraInput label="Weight (1–100)" value={weight} onChangeText={setWeight} keyboardType="number-pad" />

      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      <PathoraButton
        label={isAdding ? 'Adding…' : 'Add requirement'}
        onPress={() => void handleAdd()}
        disabled={!canSubmit || isAdding}
      />

      <SkillPickerModal
        visible={pickerOpen}
        skills={skillCatalog}
        selectedSkillIds={selectedSkill ? [selectedSkill.id] : []}
        onToggleSkill={(skillId) => {
          const skill = skillCatalog.find((s) => s.id === skillId) ?? null;
          setSelectedSkill(skill);
          setPickerOpen(false);
        }}
        onAddCustomSkill={async (name) => {
          const skill = await onAddCustomSkill(name);
          setSelectedSkill(skill);
          setPickerOpen(false);
        }}
        onClose={() => setPickerOpen(false)}
      />
    </View>
  );
}

/** "Generate with AI" panel — Stage 5B Phase C2/C3. Calls the existing Phase C1
 *  POST .../rubric/ai-draft endpoint. Every suggestion stays a suggestion until the recruiter
 *  explicitly taps "Add", which goes through the SAME addRubricRequirement call the manual
 *  form above uses — this component never creates or activates anything on its own. */
function AiDraftPanel({ internshipId, nextDisplayOrder, onAddRequirement }: {
  internshipId: string;
  nextDisplayOrder: number;
  onAddRequirement: (payload: { requirementType: RequirementType; skillId?: string; label?: string; weight: number; displayOrder: number }) => Promise<void>;
}) {
  const { token } = useAuth();
  const [instructions, setInstructions] = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [unavailableReason, setUnavailableReason] = useState<string | null>(null);
  const [suggestions, setSuggestions] = useState<AiSuggestedRequirement[] | null>(null);
  const [addedLabels, setAddedLabels] = useState<Set<string>>(new Set());

  const canGenerate = !!token && instructions.trim().length >= 10 && instructions.trim().length <= 2000 && !isGenerating;

  const handleGenerate = async () => {
    if (!canGenerate || !token) return;
    setError(null);
    setUnavailableReason(null);
    setIsGenerating(true);
    try {
      const result = await draftRubricWithAi(token, internshipId, instructions.trim());
      if (result.completed) {
        setSuggestions(result.suggestedRequirements);
      } else {
        setSuggestions(null);
        setUnavailableReason(result.unavailableReason ?? 'AI rubric drafting is not available right now.');
      }
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) setError('Please describe your requirements in 10–2000 characters.');
      else setError(describeOnboardingError(err));
    } finally {
      setIsGenerating(false);
    }
  };

  const handleAddSuggestion = async (suggestion: AiSuggestedRequirement, index: number) => {
    await onAddRequirement({
      requirementType: suggestion.requirementType,
      skillId: suggestion.skillResolved && suggestion.resolvedSkillId ? suggestion.resolvedSkillId : undefined,
      label: suggestion.skillResolved ? undefined : suggestion.label,
      weight: suggestion.suggestedWeight ?? 10,
      displayOrder: nextDisplayOrder + index,
    });
    setAddedLabels((prev) => new Set(prev).add(`${suggestion.label}-${index}`));
  };

  return (
    <ApplicationDetailCard label="Generate with AI (optional)">
      <ApplicationDetailText muted>
        Describe who you&rsquo;re looking for in plain English or Arabic. Nothing is added to this draft until you
        explicitly tap &ldquo;Add&rdquo; on a suggestion.
      </ApplicationDetailText>
      <PathoraInput
        label="e.g. We need a backend intern with Java, SQL, and Git. Docker is preferred."
        value={instructions}
        onChangeText={setInstructions}
        multiline
        numberOfLines={3}
      />
      <PathoraButton
        label={isGenerating ? 'Generating…' : 'Generate suggestions'}
        variant="secondary"
        onPress={() => void handleGenerate()}
        disabled={!canGenerate}
      />
      {error ? <Text style={styles.errorText}>{error}</Text> : null}
      {unavailableReason ? (
        <Text style={styles.mutedNotice}>
          AI assistance isn&rsquo;t available right now — a deliberate safety setting, not an error: {unavailableReason}
        </Text>
      ) : null}
      {suggestions && suggestions.length === 0 ? (
        <Text style={styles.mutedNotice}>No suggestions were generated — try adding more detail.</Text>
      ) : null}
      {suggestions?.map((suggestion, index) => {
        const key = `${suggestion.label}-${index}`;
        const isAdded = addedLabels.has(key);
        return (
          <View key={key} style={styles.suggestionRow}>
            <View style={styles.reqInfo}>
              <View style={styles.reqTop}>
                <TypeBadge type={suggestion.requirementType} />
                {suggestion.suggestedWeight !== null ? <Text style={styles.reqWeight}>Weight {suggestion.suggestedWeight}</Text> : null}
              </View>
              <Text style={styles.reqName} numberOfLines={2}>{suggestion.resolvedSkillName ?? suggestion.label}</Text>
              {!suggestion.skillResolved ? <Text style={styles.freeTextNotice}>Free-text — not a catalog skill</Text> : null}
            </View>
            <PathoraButton
              label={isAdded ? 'Added' : 'Add'}
              variant="secondary"
              onPress={() => void handleAddSuggestion(suggestion, index)}
              disabled={isAdded}
              style={styles.reqRemoveBtn}
            />
          </View>
        );
      })}
    </ApplicationDetailCard>
  );
}

function RubricScreen() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const { token } = useAuth();

  const [versions, setVersions] = useState<InternshipRubric[] | null>(null);
  const [skillCatalog, setSkillCatalog] = useState<InternProfileSkill[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isBusy, setIsBusy] = useState(false);

  const load = useCallback(async () => {
    if (!token || !id) return;
    try {
      const [loadedVersions, catalog] = await Promise.all([
        listRubricVersions(token, id),
        getCompanySkillCatalog(token).catch(() => [] as InternProfileSkill[]),
      ]);
      setVersions(loadedVersions);
      setSkillCatalog(catalog);
      setError(null);
    } catch (err) {
      setError(describeOnboardingError(err));
    }
  }, [token, id]);

  useFocusEffect(useCallback(() => { void load(); }, [load]));

  const handleAddCustomSkill = async (name: string): Promise<InternProfileSkill> => {
    if (!token) {
      throw new Error('Not authenticated');
    }
    const skill = await createCustomSkill(token, name);
    setSkillCatalog((current) => (current.some((existing) => existing.id === skill.id) ? current : [...current, skill]));
    return skill;
  };

  if (!id) return null;

  const draft = versions?.find((v) => v.status === 'DRAFT') ?? null;
  const active = versions?.find((v) => v.status === 'ACTIVE') ?? null;
  const current = draft ?? active ?? null;

  const handleCreateDraft = async () => {
    if (!token || isBusy) return;
    setIsBusy(true);
    try {
      await createRubricDraft(token, id);
      await load();
    } catch (err) {
      Alert.alert('Something went wrong', describeOnboardingError(err));
    } finally {
      setIsBusy(false);
    }
  };

  const handleActivate = async () => {
    if (!token || isBusy) return;
    setIsBusy(true);
    try {
      await activateRubricDraft(token, id);
      await load();
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) Alert.alert('Add at least one requirement first.', '');
      else Alert.alert('Something went wrong', describeOnboardingError(err));
    } finally {
      setIsBusy(false);
    }
  };

  const handleDiscard = () => {
    Alert.alert('Discard this draft?', 'This cannot be undone.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Discard', style: 'destructive',
        onPress: async () => {
          if (!token) return;
          setIsBusy(true);
          try { await discardRubricDraft(token, id); await load(); }
          catch (err) { Alert.alert('Something went wrong', describeOnboardingError(err)); }
          finally { setIsBusy(false); }
        },
      },
    ]);
  };

  const handleDuplicate = async () => {
    if (!token || isBusy) return;
    setIsBusy(true);
    try {
      await duplicateRubric(token, id);
      await load();
    } catch (err) {
      Alert.alert('Something went wrong', describeOnboardingError(err));
    } finally {
      setIsBusy(false);
    }
  };

  const handleDeleteRequirement = async (requirementId: string) => {
    if (!token) return;
    try { await deleteRubricRequirement(token, id, requirementId); await load(); }
    catch (err) { Alert.alert('Something went wrong', describeOnboardingError(err)); }
  };

  return (
    <View style={styles.screen}>
      <SafeAreaView style={styles.topSafeArea} edges={['top']}>
        <ScrollView style={styles.scrollView} contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
          <PathoraDetailHeader topInset={0} onBack={() => router.back()} eyebrow="SMART ATS" title="Screening rubric" />

          {error ? (
            <View style={styles.feedbackCard}>
              <Text style={styles.feedbackBody}>{error}</Text>
              <PathoraButton label="Try again" variant="secondary" onPress={() => void load()} />
            </View>
          ) : versions === null ? (
            <View style={styles.loadingCard}><ActivityIndicator color={C.teal600} size="large" /></View>
          ) : !current ? (
            <ApplicationDetailCard label="No rubric yet">
              <ApplicationDetailText muted>
                Configure requirements to screen applicants against. You can add them manually or generate
                suggestions with AI once a draft exists.
              </ApplicationDetailText>
              <PathoraButton label={isBusy ? 'Creating…' : 'Create draft rubric'} onPress={() => void handleCreateDraft()} disabled={isBusy} />
            </ApplicationDetailCard>
          ) : (
            <>
              <ApplicationDetailCard label={current.status === 'DRAFT' ? 'Draft rubric (editable)' : 'Active rubric'}>
                <ApplicationDetailText muted>
                  {current.status === 'DRAFT'
                    ? 'Activating will archive the current active version, if any. Past screening reports always keep referencing the exact version they were produced against.'
                    : 'This is the live, frozen rubric used for screening. To change it, start a new draft below.'}
                </ApplicationDetailText>
                {current.requirements.length === 0 ? (
                  <ApplicationDetailText muted>No requirements yet.</ApplicationDetailText>
                ) : (
                  current.requirements.map((req) => (
                    <RequirementRow
                      key={req.id}
                      requirement={req}
                      canDelete={current.status === 'DRAFT'}
                      onDelete={() => void handleDeleteRequirement(req.id)}
                    />
                  ))
                )}
              </ApplicationDetailCard>

              {current.status === 'DRAFT' && (
                <>
                  <ApplicationDetailCard label="Add a requirement">
                    <AddRequirementForm
                      skillCatalog={skillCatalog}
                      nextDisplayOrder={current.requirements.length}
                      onAdd={async (payload) => { await addRubricRequirement(token!, id, payload); await load(); }}
                      onAddCustomSkill={handleAddCustomSkill}
                    />
                  </ApplicationDetailCard>

                  <AiDraftPanel
                    internshipId={id}
                    nextDisplayOrder={current.requirements.length}
                    onAddRequirement={async (payload) => { await addRubricRequirement(token!, id, payload); await load(); }}
                  />

                  <View style={styles.actionRow}>
                    <PathoraButton
                      label={isBusy ? 'Activating…' : 'Activate rubric'}
                      onPress={() => void handleActivate()}
                      disabled={isBusy || current.requirements.length === 0}
                    />
                    <PathoraButton label="Discard draft" variant="destructive" onPress={handleDiscard} disabled={isBusy} />
                  </View>
                </>
              )}

              {current.status === 'ACTIVE' && (
                <PathoraButton label={isBusy ? 'Starting…' : 'Start a new draft'} variant="secondary" onPress={() => void handleDuplicate()} disabled={isBusy} />
              )}
            </>
          )}
        </ScrollView>
      </SafeAreaView>
      <SafeAreaView edges={['bottom']} style={styles.bottomSafeArea} />
    </View>
  );
}

export default function RubricRoute() {
  return (
    <OnboardingGuard role="COMPANY">
      <RubricScreen />
    </OnboardingGuard>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.paper },
  topSafeArea: { flex: 1, backgroundColor: C.teal700 },
  bottomSafeArea: { backgroundColor: C.paper },
  scrollView: { flex: 1, backgroundColor: C.paper },
  scrollContent: { flexGrow: 1, paddingHorizontal: 24, paddingBottom: 175, gap: 16 },
  feedbackCard: { backgroundColor: C.surface, borderWidth: 1, borderColor: C.border, borderRadius: 20, padding: 22, gap: 14, alignItems: 'center' },
  feedbackBody: { fontFamily: F.en.medium, color: C.textSecondary, fontSize: 13, textAlign: 'center', lineHeight: 20 },
  loadingCard: { minHeight: 240, alignItems: 'center', justifyContent: 'center' },
  typeBadge: { borderRadius: 999, paddingHorizontal: 9, paddingVertical: 4, alignSelf: 'flex-start' },
  typeBadgeText: { fontFamily: F.en.bold, fontSize: 10, letterSpacing: 0.4 },
  reqRow: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 10, borderTopWidth: 1, borderTopColor: C.border },
  reqInfo: { flex: 1, minWidth: 0, gap: 5 },
  reqTop: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  reqWeight: { fontFamily: F.en.medium, fontSize: 11, color: C.textTertiary },
  reqName: { fontFamily: F.en.bold, fontSize: 13.5, color: C.ink900 },
  reqRemoveBtn: { minWidth: 88 },
  addForm: { gap: 10 },
  formLabel: { fontFamily: F.en.bold, fontSize: 12, color: C.ink900, marginTop: 4 },
  typeRow: { flexDirection: 'row', gap: 8 },
  typeChoice: { flex: 1 },
  selectedSkillRow: { flexDirection: 'row' },
  errorText: { fontFamily: F.en.medium, fontSize: 12, color: C.error },
  mutedNotice: { fontFamily: F.en.medium, fontSize: 12, color: C.textSecondary, lineHeight: 18 },
  suggestionRow: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 10, borderTopWidth: 1, borderTopColor: C.border },
  freeTextNotice: { fontFamily: F.en.medium, fontSize: 10.5, color: C.textTertiary },
  actionRow: { flexDirection: 'row', gap: 10 },
});
