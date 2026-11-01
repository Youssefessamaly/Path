import { Platform, StyleSheet, View } from 'react-native';

import { PathoraColors } from '@/constants/pathora-theme';

/**
 * Extends the teal header above the first scrollable row.
 *
 * iOS pulls the ScrollView's content down during rubber-band scrolling, revealing
 * the area above its first child. This decorative fill travels with that first
 * child so the newly revealed area stays teal while the rest of the page stays
 * warm paper. It has no layout height, touch handling, or accessibility role.
 */
export function PathoraTopOverscrollFill() {
  if (Platform.OS !== 'ios') return null;

  return <View pointerEvents="none" accessible={false} style={styles.fill} />;
}

const styles = StyleSheet.create({
  fill: {
    position: 'absolute',
    top: -1200,
    left: 0,
    right: 0,
    height: 1200,
    backgroundColor: PathoraColors.teal700,
  },
});
