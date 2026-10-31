import { Pressable, StyleSheet, Text, View } from 'react-native';

import { PathoraLogo } from '@/components/pathora-logo';
import { PathoraColors, PathoraFonts, PathoraRadius, PathoraSpacing } from '@/constants/pathora-theme';

export type DashboardWelcomeHeaderProps = {
  workspaceLabel: string;
  name: string;
  subtitle: string;
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
 * Only the brand/workspace/avatar row uses the teal background; the greeting
 * remains on the paper surface with dark text, matching the rest of the page.
 */
export function DashboardWelcomeHeader({
  workspaceLabel,
  name,
  subtitle,
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
            <PathoraLogo size={26} wordmarkSize={18} onDark />
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

      <View style={styles.welcomeBlock}>
        <Text style={styles.title}>{greeting}, {name}</Text>
        <Text style={styles.subtitle}>{subtitle}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: PathoraSpacing[24],
  },
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
  brandBlock: { flex: 1, gap: 2 },
  workspaceLabel: {
    marginLeft: 2,
    fontFamily: PathoraFonts.en.bold,
    fontSize: 10,
    letterSpacing: 1.3,
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
  welcomeBlock: { gap: PathoraSpacing[8] },
  title: {
    fontFamily: PathoraFonts.en.extraBold,
    fontSize: 26,
    lineHeight: 34,
    letterSpacing: -0.6,
    color: PathoraColors.ink900,
  },
  subtitle: {
    fontFamily: PathoraFonts.en.regular,
    fontSize: 14,
    lineHeight: 21,
    color: PathoraColors.textSecondary,
  },
});
