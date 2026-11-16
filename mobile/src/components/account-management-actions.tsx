import { SymbolView } from 'expo-symbols';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { PathoraColors as C, PathoraFonts as F } from '@/constants/pathora-theme';

type Props = {
  accountType: 'student' | 'company';
  onLogOut: () => void;
  onDelete: () => void;
};

/** Shared, intentionally low-prominence account actions for both profile pages. */
export function AccountManagementActions({ accountType, onLogOut, onDelete }: Props) {
  return (
    <View>
      <Pressable
        onPress={onLogOut}
        accessibilityRole="button"
        accessibilityLabel="Log out"
        accessibilityHint="Signs you out without deleting your account"
        style={({ pressed }) => [styles.row, pressed && styles.pressed]}>
        <View style={styles.iconTile}>
          <SymbolView
            name={{ ios: 'rectangle.portrait.and.arrow.right', android: 'logout', web: 'logout' }}
            size={18}
            tintColor={C.teal700}
          />
        </View>
        <View style={styles.copy}>
          <Text style={styles.title}>Log out</Text>
          <Text style={styles.subtitle}>Sign out from this device</Text>
        </View>
        <SymbolView
          name={{ ios: 'chevron.right', android: 'chevron_right', web: 'chevron_right' }}
          size={16}
          tintColor={C.textSecondary}
        />
      </Pressable>

      <View style={styles.divider} />

      <Pressable
        onPress={onDelete}
        accessibilityRole="button"
        accessibilityLabel={`Delete ${accountType} account`}
        accessibilityHint="Opens the permanent account deletion confirmation screen"
        style={({ pressed }) => [styles.row, pressed && styles.pressed]}>
        <View style={[styles.iconTile, styles.deleteIconTile]}>
          <SymbolView
            name={{ ios: 'trash', android: 'delete_outline', web: 'delete' }}
            size={18}
            tintColor={C.error}
          />
        </View>
        <View style={styles.copy}>
          <Text style={styles.deleteTitle}>Delete account</Text>
          <Text style={styles.subtitle}>
            {accountType === 'company'
              ? 'Permanently remove your company account and related records'
              : 'Permanently remove your profile and related data'}
          </Text>
        </View>
        <SymbolView
          name={{ ios: 'chevron.right', android: 'chevron_right', web: 'chevron_right' }}
          size={16}
          tintColor={C.textSecondary}
        />
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 9, minHeight: 58 },
  pressed: { opacity: 0.72 },
  iconTile: { width: 40, height: 40, borderRadius: 12, backgroundColor: C.teal100, alignItems: 'center', justifyContent: 'center' },
  deleteIconTile: { backgroundColor: '#FBEDEB' },
  copy: { flex: 1, minWidth: 0, gap: 4 },
  title: { fontFamily: F.en.bold, color: C.ink900, fontSize: 14 },
  deleteTitle: { fontFamily: F.en.bold, color: C.error, fontSize: 14 },
  subtitle: { fontFamily: F.en.medium, color: C.textSecondary, fontSize: 12, lineHeight: 18 },
  divider: { height: 1, backgroundColor: C.border, marginVertical: 3, marginLeft: 52 },
});
