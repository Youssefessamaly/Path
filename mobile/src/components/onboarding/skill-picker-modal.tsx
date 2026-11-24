import { SymbolView } from 'expo-symbols';
import { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Keyboard,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { PathoraButton } from '@/components/pathora-button';
import { PathoraCheckbox } from '@/components/pathora-checkbox';
import { PathoraColors, PathoraFonts, PathoraRadius, PathoraSpacing } from '@/constants/pathora-theme';
import type { InternProfileSkill } from '@/lib/api';

const MIN_CUSTOM_SKILL_LENGTH = 2;
const MAX_CUSTOM_SKILL_LENGTH = 100;

export type SkillPickerModalProps = {
  visible: boolean;
  skills: InternProfileSkill[];
  selectedSkillIds: string[];
  onToggleSkill: (skillId: string) => void;
  /**
   * Resolves `trimmedName` against the server's catalog (including aliases) and adds the
   * result to both the caller's own catalog state and its selection — see
   * `createCustomSkill` in lib/api.ts. Never creates a true duplicate: a name that matches an
   * existing skill or a known alias comes back as that existing skill, not a new one. This
   * modal only drives the request/loading/error UI around that one call; it never decides on
   * its own whether something is "new".
   */
  onAddCustomSkill: (trimmedName: string) => Promise<void>;
  onClose: () => void;
};

function normalize(value: string): string {
  return value.trim().toLowerCase();
}

export function SkillPickerModal({
  visible,
  skills,
  selectedSkillIds,
  onToggleSkill,
  onAddCustomSkill,
  onClose,
}: SkillPickerModalProps) {
  const [query, setQuery] = useState('');
  const [isAdding, setIsAdding] = useState(false);
  const [addError, setAddError] = useState<string | null>(null);

  const trimmedQuery = query.trim();
  const normalizedQuery = normalize(query);

  const filteredSkills = useMemo(() => {
    if (!normalizedQuery) {
      return skills;
    }
    return skills.filter((skill) => normalize(skill.name).includes(normalizedQuery));
  }, [skills, normalizedQuery]);

  // Only a same-catalog name match is checked here — known aliases aren't sent to the client,
  // so an alias-only duplicate (e.g. typing "JS" when only "JavaScript" is in the catalog)
  // can't be ruled out until the add request round-trips through the server, which resolves
  // it to the existing skill rather than creating a new one. This client-side check only
  // avoids offering "Add" for something plainly already present by name.
  const hasExactCatalogMatch = useMemo(
    () => skills.some((skill) => normalize(skill.name) === normalizedQuery),
    [skills, normalizedQuery],
  );

  const canOfferAdd =
    trimmedQuery.length >= MIN_CUSTOM_SKILL_LENGTH &&
    trimmedQuery.length <= MAX_CUSTOM_SKILL_LENGTH &&
    !hasExactCatalogMatch;

  const resetAddState = () => {
    setIsAdding(false);
    setAddError(null);
  };

  const handleQueryChange = (value: string) => {
    setQuery(value);
    if (addError) {
      setAddError(null);
    }
  };

  const handleToggle = (skillId: string) => {
    Keyboard.dismiss();
    onToggleSkill(skillId);
  };

  const handleAddCustomSkill = async () => {
    if (!canOfferAdd || isAdding) {
      return;
    }
    setIsAdding(true);
    setAddError(null);
    try {
      await onAddCustomSkill(trimmedQuery);
      Keyboard.dismiss();
      setQuery('');
    } catch {
      setAddError('Could not add that skill. Check your connection and try again.');
    } finally {
      setIsAdding(false);
    }
  };

  const handleClose = () => {
    Keyboard.dismiss();
    setQuery('');
    resetAddState();
    onClose();
  };

  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={handleClose}>
      <View style={styles.backdrop}>
        <KeyboardAvoidingView
          style={styles.keyboardAvoiding}
          behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
          <SafeAreaView style={styles.sheet} edges={['bottom']}>
            <View style={styles.handle} />
            <Text style={styles.title}>Choose your skills</Text>

            <TextInput
              value={query}
              onChangeText={handleQueryChange}
              placeholder="Search or add a skill"
              placeholderTextColor={PathoraColors.textTertiary}
              style={styles.searchInput}
              autoCapitalize="none"
              autoCorrect={false}
              maxLength={MAX_CUSTOM_SKILL_LENGTH}
              accessibilityLabel="Search skills"
              accessibilityHint="Type to filter PATH's skill list, or enter a skill that isn't listed"
              returnKeyType="done"
            />

            <ScrollView
              style={styles.list}
              contentContainerStyle={styles.listContent}
              keyboardShouldPersistTaps="handled">
              {filteredSkills.length === 0 ? (
                <Text style={styles.emptyText}>
                  {trimmedQuery ? 'No matching skills in PATH’s catalog.' : 'No skills available right now.'}
                </Text>
              ) : (
                filteredSkills.map((skill) => {
                  const checked = selectedSkillIds.includes(skill.id);
                  return (
                    <Pressable
                      key={skill.id}
                      onPress={() => handleToggle(skill.id)}
                      style={styles.row}
                      accessibilityRole="checkbox"
                      accessibilityState={{ checked }}>
                      <Text style={styles.rowLabel}>{skill.name}</Text>
                      <PathoraCheckbox checked={checked} onToggle={() => handleToggle(skill.id)} />
                    </Pressable>
                  );
                })
              )}

              {canOfferAdd ? (
                <Pressable
                  onPress={handleAddCustomSkill}
                  disabled={isAdding}
                  style={({ pressed }) => [styles.addRow, pressed && !isAdding && styles.addRowPressed]}
                  accessibilityRole="button"
                  accessibilityLabel={`Add "${trimmedQuery}" as a new skill`}
                  accessibilityState={{ disabled: isAdding, busy: isAdding }}>
                  {isAdding ? (
                    <ActivityIndicator size="small" color={PathoraColors.teal600} />
                  ) : (
                    <SymbolView
                      name={{ ios: 'plus.circle', android: 'add_circle', web: 'add_circle' }}
                      size={18}
                      tintColor={PathoraColors.teal600}
                    />
                  )}
                  <Text style={styles.addRowText} numberOfLines={1}>
                    {isAdding ? 'Adding…' : `Add "${trimmedQuery}"`}
                  </Text>
                </Pressable>
              ) : null}

              {addError ? <Text style={styles.errorText}>{addError}</Text> : null}
            </ScrollView>

            <PathoraButton label="Done" variant="primary" onPress={handleClose} style={styles.doneButton} />
          </SafeAreaView>
        </KeyboardAvoidingView>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(22, 33, 58, 0.4)',
    justifyContent: 'flex-end',
  },
  keyboardAvoiding: {
    width: '100%',
  },
  sheet: {
    backgroundColor: PathoraColors.surface,
    borderTopLeftRadius: PathoraRadius[20],
    borderTopRightRadius: PathoraRadius[20],
    paddingHorizontal: PathoraSpacing[24],
    paddingTop: 12,
    maxHeight: '80%',
  },
  handle: {
    alignSelf: 'center',
    width: 40,
    height: 4,
    borderRadius: PathoraRadius[999],
    backgroundColor: PathoraColors.borderStrong,
    marginBottom: PathoraSpacing[16],
  },
  title: {
    fontFamily: PathoraFonts.en.extraBold,
    fontSize: 18,
    color: PathoraColors.ink900,
    marginBottom: PathoraSpacing[12],
  },
  searchInput: {
    borderWidth: 1.5,
    borderColor: PathoraColors.border,
    borderRadius: PathoraRadius[14],
    paddingHorizontal: PathoraSpacing[16],
    paddingVertical: 11,
    fontFamily: PathoraFonts.en.regular,
    fontSize: 14.5,
    color: PathoraColors.ink900,
    marginBottom: PathoraSpacing[12],
  },
  list: {
    flexGrow: 0,
  },
  listContent: {
    paddingBottom: PathoraSpacing[12],
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: PathoraColors.border,
  },
  rowLabel: {
    fontFamily: PathoraFonts.en.regular,
    fontSize: 14.5,
    color: PathoraColors.ink900,
    flexShrink: 1,
    paddingRight: PathoraSpacing[12],
  },
  emptyText: {
    fontFamily: PathoraFonts.en.regular,
    fontSize: 13.5,
    color: PathoraColors.textTertiary,
    paddingVertical: PathoraSpacing[16],
  },
  addRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingVertical: 14,
    paddingHorizontal: 4,
    marginTop: 4,
  },
  addRowPressed: {
    opacity: 0.7,
  },
  addRowText: {
    fontFamily: PathoraFonts.en.semiBold,
    fontSize: 14.5,
    color: PathoraColors.teal700,
    flexShrink: 1,
  },
  errorText: {
    fontFamily: PathoraFonts.en.medium,
    fontSize: 12.5,
    color: PathoraColors.error,
    paddingBottom: PathoraSpacing[8],
  },
  doneButton: {
    marginTop: PathoraSpacing[12],
    marginBottom: PathoraSpacing[24],
  },
});
