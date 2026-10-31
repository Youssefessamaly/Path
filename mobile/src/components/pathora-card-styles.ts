import type { ViewStyle } from 'react-native';

import { PathoraColors } from '@/constants/pathora-theme';

/** Reusable surface for feed/list cards. Keep spacing specific to the card's contents. */
export const pathoraCardSurface: ViewStyle = {
  backgroundColor: PathoraColors.surface,
  borderRadius: 18,
  borderWidth: 1,
  borderColor: PathoraColors.border,
  shadowColor: PathoraColors.ink900,
  shadowOffset: { width: 0, height: 4 },
  shadowOpacity: 0.04,
  shadowRadius: 12,
  elevation: 2,
};

export const pathoraCardPressed: ViewStyle = { opacity: 0.83 };
