import { useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { PathoraCheckbox } from '@/components/pathora-checkbox';
import { PathoraColors, PathoraFonts, PathoraSpacing } from '@/constants/pathora-theme';
import { legalDocuments, type LegalDocumentId } from '@/constants/legal-documents';

type LegalAgreementProps = {
  checked: boolean;
  onToggle: () => void;
};

export function LegalAgreement({ checked, onToggle }: LegalAgreementProps) {
  const [openDocument, setOpenDocument] = useState<LegalDocumentId | null>(null);
  const document = openDocument ? legalDocuments[openDocument] : null;

  return (
    <>
      <View style={styles.row}>
        <PathoraCheckbox checked={checked} onToggle={onToggle} />
        <View style={styles.rowContent}>
          <Text style={styles.agreementText}>
            {"I agree to PATH's "}
            <Text
              style={styles.link}
              onPress={() => setOpenDocument('terms')}
              accessibilityRole="link"
              accessibilityLabel="Read PATH Terms of Service">
              Terms of Service
            </Text>
            {' and acknowledge its '}
            <Text
              style={styles.link}
              onPress={() => setOpenDocument('privacy')}
              accessibilityRole="link"
              accessibilityLabel="Read PATH Privacy Policy">
              Privacy Policy
            </Text>
            {'.'}
          </Text>
        </View>
      </View>

      <Modal
        visible={document !== null}
        animationType="slide"
        presentationStyle="pageSheet"
        onRequestClose={() => setOpenDocument(null)}>
        <SafeAreaView style={styles.modal} edges={['top', 'bottom']}>
          <View style={styles.header}>
            <View style={styles.headerText}>
              <Text style={styles.eyebrow}>PATH · LEGAL</Text>
              <Text style={styles.title} accessibilityRole="header">{document?.title}</Text>
            </View>
            <Pressable onPress={() => setOpenDocument(null)} style={styles.close} accessibilityRole="button" accessibilityLabel="Close legal document">
              <Text style={styles.closeText}>Done</Text>
            </Pressable>
          </View>
          <ScrollView style={styles.content} contentContainerStyle={styles.contentInner} showsVerticalScrollIndicator>
            {document && (
              <>
                <Text style={styles.updated}>Last updated: {document.updated}</Text>
                {document.sections.map((section) => (
                  <View style={styles.section} key={section.title}>
                    <Text style={styles.sectionTitle} accessibilityRole="header">{section.title}</Text>
                    {section.paragraphs.map((paragraph, index) => (
                      <Text style={styles.paragraph} key={`${section.title}-${index}`}>{paragraph}</Text>
                    ))}
                  </View>
                ))}
              </>
            )}
          </ScrollView>
        </SafeAreaView>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'flex-start', gap: 12, marginBottom: PathoraSpacing[24] },
  rowContent: { flex: 1 },
  agreementText: { fontFamily: PathoraFonts.en.regular, fontSize: 13, lineHeight: 20, color: PathoraColors.textSecondary },
  link: { fontFamily: PathoraFonts.en.bold, fontSize: 13, color: PathoraColors.teal600, textDecorationLine: 'underline' },
  modal: { flex: 1, backgroundColor: PathoraColors.paper },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 22, paddingVertical: 19, backgroundColor: PathoraColors.teal700 },
  headerText: { flex: 1 },
  eyebrow: { color: '#D6EEE9', fontFamily: PathoraFonts.en.bold, letterSpacing: 1.8, fontSize: 10 },
  title: { color: '#FFFFFF', fontFamily: PathoraFonts.en.bold, fontSize: 20, marginTop: 3 },
  close: { paddingHorizontal: 13, paddingVertical: 9, marginLeft: 8, backgroundColor: 'rgba(255,255,255,.16)', borderRadius: 12 },
  closeText: { color: '#FFFFFF', fontFamily: PathoraFonts.en.bold, fontSize: 13 },
  content: { flex: 1 },
  contentInner: { paddingHorizontal: 24, paddingTop: 22, paddingBottom: 40 },
  updated: { color: PathoraColors.textTertiary, fontFamily: PathoraFonts.en.regular, fontSize: 12, marginBottom: 14 },
  section: { paddingTop: 17, paddingBottom: 5, borderTopWidth: 1, borderTopColor: PathoraColors.border },
  sectionTitle: { fontFamily: PathoraFonts.en.bold, fontSize: 16, lineHeight: 23, color: PathoraColors.teal700, marginBottom: 9 },
  paragraph: { fontFamily: PathoraFonts.en.regular, fontSize: 13, lineHeight: 22, color: PathoraColors.ink700, marginBottom: 12 },
});
