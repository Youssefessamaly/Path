import { OnboardingGuard } from '@/components/onboarding/onboarding-guard';
import { OptionalPhotoStep } from '@/components/optional-photo-step';

export default function StudentPhotoStepRoute() {
  return <OnboardingGuard role="INTERN"><OptionalPhotoStep role="INTERN" /></OnboardingGuard>;
}
