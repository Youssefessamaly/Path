import {
  Cairo_400Regular,
  Cairo_500Medium,
  Cairo_600SemiBold,
  Cairo_700Bold,
  Cairo_800ExtraBold,
  useFonts as useCairoFonts,
} from '@expo-google-fonts/cairo';
import {
  PlusJakartaSans_400Regular,
  PlusJakartaSans_500Medium,
  PlusJakartaSans_600SemiBold,
  PlusJakartaSans_700Bold,
  PlusJakartaSans_800ExtraBold,
  useFonts as usePlusJakartaSansFonts,
} from '@expo-google-fonts/plus-jakarta-sans';
import { Stack, usePathname, useRouter } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import * as SplashScreen from 'expo-splash-screen';
import { useEffect } from 'react';

import { PathoraRenderDiagnostic } from '@/components/pathora-render-diagnostic';
import { PathoraColors } from '@/constants/pathora-theme';
import { AuthProvider, useAuth } from '@/context/auth-context';

SplashScreen.preventAutoHideAsync();

export default function RootLayout() {
  const [plusJakartaSansLoaded] = usePlusJakartaSansFonts({
    PlusJakartaSans_400Regular,
    PlusJakartaSans_500Medium,
    PlusJakartaSans_600SemiBold,
    PlusJakartaSans_700Bold,
    PlusJakartaSans_800ExtraBold,
  });
  const [cairoLoaded] = useCairoFonts({
    Cairo_400Regular,
    Cairo_500Medium,
    Cairo_600SemiBold,
    Cairo_700Bold,
    Cairo_800ExtraBold,
  });

  const fontsReady = plusJakartaSansLoaded && cairoLoaded;

  return (
    <AuthProvider>
      <RootNavigator fontsReady={fontsReady} />
    </AuthProvider>
  );
}

/**
 * Declares which routes belong to the signed-out vs signed-in halves of the app so
 * `Stack.Protected` can enforce the split at the navigator level — not just on each
 * screen's first render. A per-screen guard (see e.g. intern/_layout.tsx) only stops the
 * CURRENTLY FOCUSED screen from rendering the wrong thing; it does nothing about screens
 * already sitting lower in the native stack's history. Without this, "Welcome -> Login"
 * (push) followed by a successful sign-in only replaces the top of the stack — Welcome
 * (and, after registration, Login itself) stays in history, reachable by pressing Back
 * even though the user is now authenticated. `Stack.Protected` fixes this by physically
 * dropping its guarded screens from the stack the moment its `guard` goes false, in both
 * directions: signing in strips the signed-out screens, signing out strips the signed-in
 * ones. The two lists below must stay exhaustive for every route that actually requires
 * (or actually forbids) an authenticated session — `index` (this file's own splash/launch
 * router) and the legacy template `explore` screen are intentionally left unguarded, since
 * `index` is itself what decides where a user of either auth state should land.
 */
function RootNavigator({ fontsReady }: { fontsReady: boolean }) {
  const { isInitializing, isAuthenticated, sessionExpired, clearSessionExpired } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const hasTealHeader =
    pathname.startsWith('/intern') ||
    pathname.startsWith('/company') ||
    pathname.startsWith('/onboarding') ||
    pathname.startsWith('/account') ||
    pathname.startsWith('/register') ||
    ['/account-type', '/login', '/forgot-password', '/verify-otp', '/reset-password'].includes(pathname);
  const isReady = fontsReady && !isInitializing;

  useEffect(() => {
    if (isReady) {
      SplashScreen.hideAsync();
    }
  }, [isReady]);

  useEffect(() => {
    // The '/' (splash) route handles its own sessionExpired-aware destination on cold start —
    // see index.tsx's useSplashDestination, which also consumes the flag. This effect only
    // needs to react to a session going bad WHILE the user is already past the splash, on some
    // already-mounted authenticated screen, where nothing else is watching for it.
    if (!isReady || pathname === '/' || !sessionExpired) {
      return;
    }
    clearSessionExpired();
    router.replace({ pathname: '/login', params: { sessionExpired: '1' } });
  }, [isReady, pathname, sessionExpired, clearSessionExpired, router]);

  if (!isReady) {
    return null;
  }

  const stackScreenOptions = {
    headerShown: false,
    contentStyle: { backgroundColor: PathoraColors.paper },
  };

  const stack = (
    <Stack screenOptions={stackScreenOptions}>
      <Stack.Screen name="index" />
      <Stack.Screen name="explore" />

      <Stack.Protected guard={!isAuthenticated}>
        <Stack.Screen name="welcome" />
        <Stack.Screen name="login" />
        <Stack.Screen name="account-type" />
        <Stack.Screen name="register/student" />
        <Stack.Screen name="register/company" />
        <Stack.Screen name="forgot-password" />
        <Stack.Screen name="verify-otp" />
        <Stack.Screen name="reset-password" />
      </Stack.Protected>

      <Stack.Protected guard={isAuthenticated}>
        <Stack.Screen name="intern" />
        <Stack.Screen name="company/(tabs)" />
        <Stack.Screen name="company/approval-pending" />
        <Stack.Screen name="company/approval-rejected" />
        <Stack.Screen name="company/onboarding/company" />
        <Stack.Screen name="company/onboarding/contact" />
        <Stack.Screen name="company/onboarding/details" />
        <Stack.Screen name="company/onboarding/logo" />
        <Stack.Screen name="onboarding/personal" />
        <Stack.Screen name="onboarding/education" />
        <Stack.Screen name="onboarding/skills" />
        <Stack.Screen name="onboarding/photo" />
        <Stack.Screen name="account/delete" />
        <Stack.Screen name="cv-preview" />
      </Stack.Protected>
    </Stack>
  );

  return (
    <>
      <StatusBar style={hasTealHeader ? 'light' : 'dark'} />
      {__DEV__ ? <PathoraRenderDiagnostic route={pathname}>{stack}</PathoraRenderDiagnostic> : stack}
    </>
  );
}
