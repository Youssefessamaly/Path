import type { InternProfile } from '@/lib/api';

export type ProfileStrength = {
  percentage: number;
  /** The backend's own onboarding-completion flag — always the source of truth for whether
   *  the intern still needs to finish onboarding, independent of the optional-extras score. */
  isOnboardingComplete: boolean;
};

/**
 * A small, fully deterministic 0-100 profile-completeness score derived only from fields
 * InternProfile already exposes. UI-only — never read by, or derived from, the backend's
 * recommendation/relevance scoring.
 *
 * Each applicable check below counts equally. academicYear is only an applicable check for
 * UNIVERSITY-level interns, matching the same conditional the onboarding flow itself uses.
 * The CV is required to finish onboarding for new interns, but existing completed profiles
 * may predate that requirement. LinkedIn stays optional. The score remains independent
 * from the backend's stored onboardingCompleted flag.
 */
export function calculateProfileStrength(profile: InternProfile): ProfileStrength {
  const checks: boolean[] = [
    // Personal details
    Boolean(profile.firstName?.trim()),
    Boolean(profile.lastName?.trim()),
    Boolean(profile.dateOfBirth),
    Boolean(profile.phoneNumber?.trim()),
    // Education
    Boolean(profile.institutionName?.trim()),
    Boolean(profile.academicLevel),
    Boolean(profile.major?.trim()),
    Boolean(profile.expectedGraduationYear),
    // Career
    profile.skills.length > 0,
    Boolean(profile.careerInterests?.trim()),
    // Optional strength
    Boolean(profile.cvFileName),
    Boolean(profile.linkedinUrl?.trim()),
  ];

  if (profile.academicLevel === 'UNIVERSITY') {
    checks.push(profile.academicYear !== null);
  }

  const passed = checks.filter(Boolean).length;
  const percentage = Math.round((passed / checks.length) * 100);

  return {
    percentage,
    isOnboardingComplete: profile.onboardingCompleted,
  };
}
