import { useRouter } from 'expo-router';
import { ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AccountTypeCard } from '@/components/account-type-card';
import { PathoraDetailHeader } from '@/components/pathora-detail-header';
import { PathoraColors, PathoraSpacing } from '@/constants/pathora-theme';

export default function AccountTypeScreen() {
  const router = useRouter();

  const handleStudent = () => {
    router.push('/register/student');
  };

  const handleCompany = () => {
    router.push('/register/company');
  };

  return (
    <SafeAreaView style={styles.bottomSafeArea} edges={['bottom']}>
      <SafeAreaView style={styles.safeArea} edges={['top']}>
        <ScrollView
          style={{ backgroundColor: PathoraColors.paper }}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}>
          <PathoraDetailHeader topInset={0} bottomGap={24} eyebrow="PATHORA" title="How will you use Pathora?" subtitle="Choose the account that fits what you'll do on Pathora." onBack={() => router.replace('/welcome')} />

          <AccountTypeCard
            title="I'm a student"
            description="Find and apply to internships that match your profile."
            iconName={{ ios: 'person.fill', android: 'person', web: 'person' }}
            onPress={handleStudent}
          />

          <AccountTypeCard
            title="I'm a company"
            description="Post internships and review applicants."
            iconName={{ ios: 'building.2.fill', android: 'apartment', web: 'apartment' }}
            onPress={handleCompany}
          />
        </ScrollView>
      </SafeAreaView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  // The bottom safe-area matches the page, while the top remains teal.
  bottomSafeArea: {
    flex: 1,
    backgroundColor: PathoraColors.paper,
  },
  safeArea: {
    flex: 1,
    backgroundColor: PathoraColors.teal700,
  },
  scrollContent: {
    flexGrow: 1,
    paddingHorizontal: PathoraSpacing[24],
  },
});
