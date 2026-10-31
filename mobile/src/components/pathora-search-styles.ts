import type { ViewStyle } from 'react-native';
import { PathoraColors, PathoraRadius } from '@/constants/pathora-theme';

/** Both a navigational search prompt and a live search field use this same surface. */
export const pathoraSearchSurface: ViewStyle = {
  flexDirection: 'row', alignItems: 'center', gap: 10,
  minHeight: 50,
  borderRadius: PathoraRadius[14],
  backgroundColor: PathoraColors.surface,
  borderColor: PathoraColors.border, borderWidth: 1,
  paddingHorizontal: 16, paddingVertical: 12,
};
