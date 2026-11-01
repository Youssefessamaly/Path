import { Pressable, StyleSheet, Text, View } from 'react-native';

import { PathoraColors, PathoraFonts, PathoraRadius, PathoraSpacing } from '@/constants/pathora-theme';

export type DashboardWelcomeHeaderProps = {
  workspaceLabel: string;
  name: string;
  // Kept optional for compatibility with dashboard call sites. Not displayed.
  subtitle?: string;
  avatarInitials: string;
  onAvatarPress: () => void;
  avatarAccessibilityLabel: string;
};

function greetingForHour(hour: number): string {
  if (hour < 12) return 'Good morning';
  if (hour < 18) return 'Good afternoon';
  return 'Good evening';
}

/**
 * Shared student/company dashboard header.
 * Show the personalized greeting in the teal heading above the workspace label.
 * No secondary tagline is rendered beneath the teal header.
 */
export function DashboardWelcomeHeader({
  workspaceLabel,
  name,
  avatarInitials,
  onAvatarPress,
  avatarAccessibilityLabel,
}: DashboardWelcomeHeaderProps) {
  const greeting = greetingForHour(new Date().getHours());

  return (
    <View style={styles.container}>
      <View style={styles.brandPanel}>
        <View style={styles.topRow}>
          <View style={styles.brandBlock}>
            <Text style={styles.greeting} numberOfLines={2} accessibilityLabel={`${greeting}, ${name}`}>
              {greeting}, {name}
            </Text>
            <Text style={styles.workspaceLabel}>{workspaceLabel}</Text>
          </View>
          <Pressable
            onPress={onAvatarPress}
            accessibilityRole="button"
            accessibilityLabel={avatarAccessibilityLabel}
            hitSlop={8}
            style={({ pressed }) => [styles.avatar, pressed && styles.avatarPressed]}>
            <Text style={styles.avatarText}>{avatarInitials}</Text>
          </Pressable>
        </View>
      </View>

    </View>
  );
}

const styles = StyleSheet.create({
  container: {},
  brandPanel: {
    backgroundColor: PathoraColors.teal700,
    marginHorizontal: -PathoraSpacing[24],
    marginTop: -PathoraSpacing[8],
    paddingHorizontal: PathoraSpacing[24],
    paddingTop: PathoraSpacing[20],
    paddingBottom: PathoraSpacing[24],
    borderBottomLeftRadius: 24,
    borderBottomRightRadius: 24,
  },
  topRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: PathoraSpacing[12],
  },
  brandBlock: { flex: 1, minWidth: 0, gap: PathoraSpacing[4] },
  greeting: {
    fontFamily: PathoraFonts.en.extraBold,
    fontSize: 25,
    lineHeight: 32,
    letterSpacing: -0.5,
    color: PathoraColors.surface,
  },
  workspaceLabel: {
    marginLeft: 2,
    fontFamily: PathoraFonts.en.bold,
    fontSize: 11.5,
    letterSpacing: 1.1,
    color: 'rgba(255,255,255,0.8)',
  },
  avatar: {
    width: 46,
    height: 46,
    borderRadius: PathoraRadius[999],
    backgroundColor: 'rgba(255,255,255,0.15)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.38)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarPressed: { opacity: 0.75 },
  avatarText: {
    fontFamily: PathoraFonts.en.extraBold,
    fontSize: 16,
    color: PathoraColors.surface,
  },
});
