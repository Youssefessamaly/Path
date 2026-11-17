import { OnboardingGuard } from '@/components/onboarding/onboarding-guard';
import { OptionalPhotoStep } from '@/components/optional-photo-step';

export default function CompanyLogoStepRoute() {
  return <OnboardingGuard role="COMPANY"><OptionalPhotoStep role="COMPANY" /></OnboardingGuard>;
}
