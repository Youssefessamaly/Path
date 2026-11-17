/**
 * Select a PDF from Files and upload it using PATH's existing authenticated CV API.
 * Shared by the student onboarding and profile screens to keep validation consistent.
 */
import * as DocumentPicker from 'expo-document-picker';

import { uploadInternCv, type CvUploadResponse } from '@/lib/api';

const MAX_CV_SIZE_BYTES = 5 * 1024 * 1024;

/** Expected, actionable local file-selection failure (not a backend error). */
export class CvSelectionError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'CvSelectionError';
  }
}

/** Returns null if the user cancels without choosing a file. */
export async function pickAndUploadInternCv(token: string): Promise<CvUploadResponse | null> {
  let result: DocumentPicker.DocumentPickerResult;
  try {
    result = await DocumentPicker.getDocumentAsync({
      type: 'application/pdf',
      multiple: false,
      // A local cache copy makes document-provider URIs readable by our upload client.
      copyToCacheDirectory: true,
    });
  } catch {
    throw new CvSelectionError('Could not open Files. Please try again.');
  }

  if (result.canceled || !result.assets?.length) {
    return null;
  }

  const asset = result.assets[0];
  // Some cloud file providers label PDFs as generic binary files. Trust either
  // PDF extension or MIME metadata here; the server validates actual PDF bytes.
  const isPdf = asset.name.toLowerCase().endsWith('.pdf') ||
    asset.mimeType?.toLowerCase() === 'application/pdf';

  if (!isPdf) {
    throw new CvSelectionError('Please select a PDF document.');
  }
  if (typeof asset.size === 'number' && asset.size > MAX_CV_SIZE_BYTES) {
    throw new CvSelectionError('Your CV must be 5 MB or smaller.');
  }

  // The existing backend validates the actual PDF signature/content and enforces size.
  return uploadInternCv(token, {
    uri: asset.uri,
    name: asset.name,
    mimeType: 'application/pdf',
  });
}
