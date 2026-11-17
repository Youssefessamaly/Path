import { useEffect, useState } from 'react';
import { Image, Pressable, StyleSheet, Text, View } from 'react-native';
import type { ImageURISource, StyleProp, ViewStyle } from 'react-native';

import { PathoraColors, PathoraFonts } from '@/constants/pathora-theme';
import { photoImageSource } from '@/lib/api';

type Props = {
  initials: string;
  photoUrl?: string | null;
  token?: string | null;
  size?: number;
  company?: boolean;
  onPress?: () => void;
  accessibilityLabel?: string;
  style?: StyleProp<ViewStyle>;
};

/** Single initials fallback shared by all student and company photo placements. */
export function ProfileAvatar({ initials, photoUrl, token, size = 48, company = false,
  onPress, accessibilityLabel, style }: Props) {
  const [failed, setFailed] = useState(false);
  useEffect(() => { setFailed(false); }, [photoUrl]);
  let source: ImageURISource | null = null;
  if (photoUrl && !failed) {
    try { source = photoImageSource(photoUrl, token); } catch { source = null; }
  }
  const radius = company ? Math.round(size * 0.27) : Math.round(size / 2);
  const avatar = (
    <View style={[styles.frame, {
      width: size, height: size, borderRadius: radius,
      backgroundColor: company ? PathoraColors.teal100 : PathoraColors.teal700,
    }, style]}>
      {source ? (
        <Image source={source} style={[StyleSheet.absoluteFillObject, { width: size, height: size }]}
          resizeMode={company ? 'contain' : 'cover'} onError={() => setFailed(true)}
          accessibilityLabel={accessibilityLabel || (company ? 'Company logo' : 'Profile photo')} />
      ) : (
        <Text style={[styles.initials, { fontSize: size * 0.33, color: company ? PathoraColors.teal700 : '#FFFFFF' }]}>
          {initials || '?'}
        </Text>
      )}
    </View>
  );
  return onPress ? (
    <Pressable accessibilityRole="button" accessibilityLabel={accessibilityLabel || 'Preview profile photo'}
      onPress={onPress} hitSlop={6}>{avatar}</Pressable>
  ) : avatar;
}

const styles = StyleSheet.create({
  frame: { borderWidth: 1, borderColor: 'rgba(20, 90, 78, 0.12)', alignItems: 'center',
    justifyContent: 'center', overflow: 'hidden' },
  initials: { fontFamily: PathoraFonts.en.extraBold },
});
