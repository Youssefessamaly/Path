import * as DocumentPicker from 'expo-document-picker';
import * as ImagePicker from 'expo-image-picker';
import { SymbolView } from 'expo-symbols';
import { useRef, useState } from 'react';
import { ActivityIndicator, Alert, Image, Modal, Platform, Pressable, StyleSheet, Text, View } from 'react-native';

import { ProfileAvatar } from '@/components/profile-avatar';
import { PathoraColors as C, PathoraFonts as F } from '@/constants/pathora-theme';
import { deleteProfilePhoto, photoImageSource, uploadProfilePhoto, type ProfilePhotoFile } from '@/lib/api';

type Props = {
  role: 'INTERN' | 'COMPANY';
  initials: string;
  photoUrl?: string | null;
  token: string;
  onSaved: () => void | Promise<void>;
  size?: number;
  showAction?: boolean;
};

/** Both onboarding flows and profile pages use the same accessible, optional photo UI. */
export function ProfilePhotoEditor({ role, initials, photoUrl, token, onSaved, size = 106, showAction = true }: Props) {
  const company = role === 'COMPANY';
  const [sheet, setSheet] = useState(false);
  const [preview, setPreview] = useState(false);
  const [candidate, setCandidate] = useState<ProfilePhotoFile | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Native image and document pickers must not be presented while our bottom sheet is dismissing.
  // iOS reports the completion through Modal.onDismiss; Android needs the slide animation
  // to finish. On web the picker must be opened directly in the button's event handler.
  const pendingIosPicker = useRef<'library' | 'camera' | 'files' | null>(null);

  const openPicker = async (source: 'library' | 'camera' | 'files') => {
    setError(null);
    try {
      if (source === 'files') {
        // Keep the selected document in Expo's cache so the existing upload helper can
        // copy it into its own multipart staging directory on iOS and Android.
        const result = await DocumentPicker.getDocumentAsync({
          type: ['image/jpeg', 'image/png'],
          copyToCacheDirectory: true,
          multiple: false,
        });
        if (!result || result.canceled || !result.assets?.length) return;
        const asset = result.assets[0];
        if (!asset.uri) {
          setError('The selected file could not be opened. Please choose another image.');
          return;
        }
        if (asset.size != null && asset.size > 5 * 1024 * 1024) {
          setError('The selected image is larger than 5 MB. Choose a smaller file.');
          return;
        }

        // Files providers are inconsistent about MIME metadata. Only accept JPEG/PNG,
        // and never label an HEIC/WebP/PDF as JPEG just because it is an image.
        const name = (asset.name ?? '').toLowerCase();
        const reportedMime = (asset.mimeType ?? '').toLowerCase();
        const fromName = /\.png$/.test(name) ? 'image/png'
          : /\.jpe?g$/.test(name) ? 'image/jpeg' : null;
        const fromMime = reportedMime === 'image/png' ? 'image/png'
          : (reportedMime === 'image/jpeg' || reportedMime === 'image/jpg') ? 'image/jpeg' : null;
        const genericMime = !reportedMime || reportedMime === 'application/octet-stream';
        const mimeType = fromMime && (!fromName || fromMime === fromName) ? fromMime
          : genericMime ? fromName : null;
        if (!mimeType) {
          setError('Please select a JPEG or PNG image from Files.');
          return;
        }
        setCandidate({ uri: asset.uri, name: mimeType === 'image/png' ? 'path-logo.png' : 'path-profile.jpg', mimeType });
        return;
      }
      if (source === 'camera') {
        const permission = await ImagePicker.requestCameraPermissionsAsync();
        if (!permission.granted) {
          setError('Camera access is required to take a photo. Enable it in your device settings.');
          return;
        }
      }

      // Image-only system picker (not the Files/document browser). No broad photo-library
      // permission request is necessary for choosing one photo with the system picker.
      const options: ImagePicker.ImagePickerOptions = {
        mediaTypes: ['images'],
        allowsEditing: true,
        aspect: [1, 1],
        quality: 0.75,
        exif: false,
        base64: false,
      };
      const result = source === 'camera'
        ? await ImagePicker.launchCameraAsync(options)
        : await ImagePicker.launchImageLibraryAsync(options);
      if (!result || result.canceled || !result.assets?.length) return;
      const asset = result.assets[0];
      if (!asset.uri) {
        setError('No photo was returned. Please choose another image.');
        return;
      }
      if (asset.fileSize != null && asset.fileSize > 5 * 1024 * 1024) {
        setError('The selected photo is larger than 5 MB. Choose a smaller image.');
        return;
      }

      // Prefer the actual output URI; the original filename may still be .HEIC on iOS
      // even though the system editor exported a new JPEG file for the selected photo.
      const uriPath = asset.uri.split('?')[0].toLowerCase();
      const fileName = (asset.fileName ?? '').toLowerCase();
      const isPng = /\.png$/.test(uriPath) || (!/\.jpe?g$/.test(uriPath)
        && (asset.mimeType === 'image/png' || /\.png$/.test(fileName)));
      const isJpeg = /\.jpe?g$/.test(uriPath) || asset.mimeType === 'image/jpeg'
        || /\.jpe?g$/.test(fileName);
      if (!isPng && !isJpeg) {
        setError('This photo could not be converted to JPEG or PNG. Please choose another photo.');
        return;
      }
      setCandidate({ uri: asset.uri, name: isPng ? 'path-logo.png' : 'path-profile.jpg',
        mimeType: isPng ? 'image/png' : 'image/jpeg' });
    } catch (cause) {
      // Preserve the underlying message during local development without logging image data.
      if (__DEV__) console.warn('[PATH photo picker]', cause);
      setError(source === 'camera'
        ? 'Could not open the camera. Please try again.'
        : source === 'files' ? 'Could not open Files. Please try again.'
        : 'Could not open your photo library. Please try again.');
    }
  };

  const chooseSource = (source: 'library' | 'camera' | 'files') => {
    setSheet(false);
    if (Platform.OS === 'web') {
      void openPicker(source);
    } else if (Platform.OS === 'ios') {
      pendingIosPicker.current = source;
    } else {
      // Wait until the React Native bottom-sheet dismissal animation has finished.
      setTimeout(() => { void openPicker(source); }, 450);
    }
  };

  const onSheetDismissed = () => {
    const source = pendingIosPicker.current;
    pendingIosPicker.current = null;
    if (source) void openPicker(source);
  };

  const save = async () => {
    if (!candidate || saving) return;
    setSaving(true); setError(null);
    try {
      await uploadProfilePhoto(token, role, candidate);
      setCandidate(null);
      await onSaved();
    } catch {
      setError('We could not upload this image. Your previous photo has been kept.');
    } finally { setSaving(false); }
  };

  const remove = () => {
    setSheet(false);
    Alert.alert(company ? 'Remove company logo?' : 'Remove profile photo?',
      'Your initials will appear until you add a new image.', [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Remove', style: 'destructive', onPress: async () => {
          setSaving(true); setError(null);
          try { await deleteProfilePhoto(token, role); await onSaved(); }
          catch { setError('Could not remove the image. Please try again.'); }
          finally { setSaving(false); }
        } },
      ]);
  };

  return (
    <View style={styles.container}>
      <View style={styles.avatarWrap}>
        <ProfileAvatar initials={initials} photoUrl={photoUrl} token={company ? undefined : token}
          size={size} company={company} onPress={() => photoUrl ? setPreview(true) : setSheet(true)}
          accessibilityLabel={photoUrl ? (company ? 'View company logo' : 'View profile photo') : (company ? 'Upload company logo' : 'Add profile photo')} />
        <Pressable onPress={() => setSheet(true)} disabled={saving} accessibilityRole="button"
          accessibilityLabel={company ? (photoUrl ? 'Change company logo' : 'Upload company logo') : (photoUrl ? 'Change profile photo' : 'Add profile photo')} style={styles.cameraBadge}>
          <SymbolView name={{ ios: 'camera.fill', android: 'photo_camera', web: 'photo_camera' }}
            tintColor="#FFFFFF" size={17} />
        </Pressable>
      </View>
      {showAction ? (
        <Pressable accessibilityRole="button" onPress={() => setSheet(true)} disabled={saving}
          style={styles.action}>
          <Text style={styles.actionText}>{photoUrl
            ? company ? 'Change logo' : 'Change photo'
            : company ? 'Upload logo' : 'Add photo'}</Text>
        </Pressable>
      ) : null}
      {saving ? <ActivityIndicator size="small" color={C.teal700} /> : null}
      {error ? <Text style={styles.error} accessibilityRole="alert">{error}</Text> : null}

      <Modal transparent visible={sheet} animationType="slide" onRequestClose={() => setSheet(false)}
        onDismiss={onSheetDismissed}>
        <View style={styles.scrim}>
          <Pressable style={StyleSheet.absoluteFill} onPress={() => setSheet(false)} />
          <View style={styles.sheet}>
            <View style={styles.handle} />
            <Text style={styles.sheetTitle}>{company ? 'Company logo' : 'Profile photo'}</Text>
            <Text style={styles.sheetHint}>Choose a JPEG or PNG image, up to 5 MB.</Text>
            <Pressable style={styles.sheetAction} onPress={() => chooseSource('library')}>
              <SymbolView name={{ ios: 'photo.on.rectangle', android: 'image', web: 'image' }} tintColor={C.teal700} size={21} />
              <Text style={styles.sheetActionText}>Choose from photo library</Text>
            </Pressable>
            <Pressable style={styles.sheetAction} onPress={() => chooseSource('files')}>
              <SymbolView name={{ ios: 'folder', android: 'folder', web: 'folder' }} tintColor={C.teal700} size={21} />
              <Text style={styles.sheetActionText}>Choose from Files</Text>
            </Pressable>
            {!company && Platform.OS !== 'web' ? (
              <Pressable style={styles.sheetAction} onPress={() => chooseSource('camera')}>
                <SymbolView name={{ ios: 'camera', android: 'photo_camera', web: 'photo_camera' }}
                  tintColor={C.teal700} size={21} />
                <Text style={styles.sheetActionText}>Take a photo</Text>
              </Pressable>
            ) : null}
            {photoUrl ? (
              <Pressable style={styles.sheetAction} onPress={remove}>
                <SymbolView name={{ ios: 'trash', android: 'delete', web: 'delete' }} tintColor="#A33B37" size={21} />
                <Text style={[styles.sheetActionText, { color: '#A33B37' }]}>Remove image</Text>
              </Pressable>
            ) : null}
            <Pressable style={styles.cancel} onPress={() => setSheet(false)}>
              <Text style={styles.cancelText}>Cancel</Text>
            </Pressable>
          </View>
        </View>
      </Modal>

      {/* Only mount the review modal when a file has actually been selected.
          Keeping an invisible modal mounted can cause inconsistent native modal
          transitions when the picker closes and the selected image is displayed. */}
      {candidate !== null ? (
        <Modal
          transparent
          visible
          animationType="fade"
          onRequestClose={() => {
            if (!saving) {
              setCandidate(null);
            }
          }}>
          <View style={styles.previewRoot}>
            <Text style={styles.previewTitle}>Review your image</Text>
            <Image
              source={{ uri: candidate.uri }}
              style={styles.previewImage}
              resizeMode={company ? 'contain' : 'cover'}
            />
            <Text style={styles.previewHint}>
              {company ? 'Your full logo will be visible.' : 'Your picture will be cropped to a circle.'}
            </Text>
            <Pressable style={styles.saveButton} disabled={saving} onPress={() => void save()}>
              <Text style={styles.saveText}>{saving ? 'Saving…' : 'Use this image'}</Text>
            </Pressable>
            <Pressable onPress={() => setCandidate(null)} disabled={saving} style={styles.previewCancel}>
              <Text style={styles.cancelText}>Choose another time</Text>
            </Pressable>
          </View>
        </Modal>
      ) : null}

      <Modal transparent visible={preview} animationType="fade" onRequestClose={() => setPreview(false)}>
        <View style={styles.previewRoot}>
          <Pressable onPress={() => setPreview(false)} accessibilityRole="button" accessibilityLabel="Close photo preview"
            style={styles.previewClose}><Text style={styles.closeText}>✕</Text></Pressable>
          {photoUrl ? <Image source={photoImageSource(photoUrl, company ? undefined : token)}
            style={styles.fullPhoto} resizeMode="contain" /> :
            <ProfileAvatar initials={initials} size={Math.min(290, size * 2.8)} company={company} />}
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { alignItems: 'center', gap: 7 },
  avatarWrap: { position: 'relative', alignItems: 'center' },
  cameraBadge: { position: 'absolute', bottom: 0, right: 0, width: 34, height: 34, borderRadius: 17,
    backgroundColor: C.teal700, borderColor: '#FFFFFF', borderWidth: 3, alignItems: 'center', justifyContent: 'center' },
  action: { paddingVertical: 6, paddingHorizontal: 8 },
  actionText: { fontFamily: F.en.bold, fontSize: 12, color: C.teal700, textAlign: 'center' },
  error: { fontFamily: F.en.medium, color: '#A33B37', fontSize: 12, textAlign: 'center', maxWidth: 210 },
  scrim: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(7,25,24,0.45)' },
  sheet: { backgroundColor: C.surface, borderTopLeftRadius: 26, borderTopRightRadius: 26,
    paddingHorizontal: 24, paddingTop: 14, paddingBottom: 38, gap: 13 },
  handle: { width: 38, height: 5, borderRadius: 6, backgroundColor: C.border, alignSelf: 'center', marginBottom: 6 },
  sheetTitle: { fontFamily: F.en.extraBold, fontSize: 20, color: C.ink900 },
  sheetHint: { fontFamily: F.en.medium, color: C.textSecondary, fontSize: 13, marginBottom: 6 },
  sheetAction: { flexDirection: 'row', alignItems: 'center', gap: 13, backgroundColor: C.paper, padding: 15, borderRadius: 14 },
  sheetActionText: { fontFamily: F.en.bold, color: C.ink900, fontSize: 14 },
  cancel: { padding: 13, alignItems: 'center' },
  cancelText: { fontFamily: F.en.bold, color: C.textSecondary, fontSize: 13 },
  previewRoot: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#0A2422', padding: 30, gap: 20 },
  previewTitle: { color: '#FFFFFF', fontFamily: F.en.bold, fontSize: 22 },
  fullPhoto: { width: '100%', maxWidth: 420, height: '65%' },
  previewImage: { width: 250, height: 250, borderRadius: 20, backgroundColor: '#FFFFFF' },
  previewHint: { color: '#DCE9E6', fontFamily: F.en.medium, fontSize: 13, textAlign: 'center' },
  saveButton: { backgroundColor: '#FFFFFF', borderRadius: 13, width: '100%', padding: 15, alignItems: 'center' },
  saveText: { color: C.teal700, fontFamily: F.en.bold, fontSize: 14 },
  previewCancel: { padding: 10 },
  previewClose: { position: 'absolute', right: 24, top: 58, padding: 12 },
  closeText: { color: '#FFFFFF', fontSize: 27 },
});
