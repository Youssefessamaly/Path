import { SymbolView } from 'expo-symbols';
import { Pressable, StyleSheet, Text, type StyleProp, type ViewStyle } from 'react-native';

import { PathoraColors, PathoraFonts } from '@/constants/pathora-theme';
import { pathoraSearchSurface } from '@/components/pathora-search-styles';

export type PathoraSearchBarProps = {
  placeholder: string;
  onPress: () => void;
  style?: StyleProp<ViewStyle>;
};

/**
 * The white "Search internships" surface from the dashboard design. Not a real text
 * field — tapping it navigates to the Internships screen, which has the actual typable search
 * input (see PathoraSearchInput) and real backend-filtered results. Never produces fake
 * results itself.
 */
export function PathoraSearchBar({ placeholder, onPress, style }: PathoraSearchBarProps) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={placeholder}
      style={({ pressed }) => [styles.bar, pressed && styles.barPressed, style]}>
      <SymbolView
        name={{ ios: 'magnifyingglass', android: 'search', web: 'search' }}
        size={18}
        tintColor={PathoraColors.textTertiary}
      />
      <Text style={styles.placeholder}>{placeholder}</Text>
      <SymbolView name={{ ios: 'arrow.up.right', android: 'arrow_outward', web: 'arrow_outward' }} size={15} tintColor={PathoraColors.teal600} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  bar: { ...pathoraSearchSurface },
  barPressed: {
    opacity: 0.7,
  },
  placeholder: {
    fontFamily: PathoraFonts.en.regular,
    fontSize: 14,
    color: PathoraColors.textTertiary,
    flex: 1,
  },
});
