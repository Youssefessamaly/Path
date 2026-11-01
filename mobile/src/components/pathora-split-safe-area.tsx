import type { ReactNode } from 'react';
import { StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { PathoraColors } from '@/constants/pathora-theme';

type Props = { children: ReactNode };

/**
 * Keeps the iPhone's top inset teal under Pathora headers while the bottom inset
 * matches the warm-paper page background. The content still fills the viewport.
 */
export function PathoraSplitSafeArea({ children }: Props) {
  return (
    <SafeAreaView style={styles.bottom} edges={['bottom']}>
      <SafeAreaView style={styles.top} edges={['top']}>
        {children}
      </SafeAreaView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  bottom: { flex: 1, backgroundColor: PathoraColors.paper },
  top: { flex: 1, backgroundColor: PathoraColors.teal700 },
});
