import { Tabs, useRouter } from 'expo-router';
import { useEffect, type ReactNode } from 'react';
import { View } from 'react-native';

import { InternLiquidTabBar } from '@/components/intern-liquid-tab-bar';
import { PathoraColors } from '@/constants/pathora-theme';
import { useAuth } from '@/context/auth-context';

/**
 * Client-side gate for the whole INTERN tab group: requires an authenticated INTERN session.
 * The backend independently enforces this on every request regardless of this guard.
 * Centralized here (once) rather than duplicated per tab, now that /intern is a real
 * multi-screen navigator rather than a single route.
 */
function InternTabsGuard({ children }: { children: ReactNode }) {
  const router = useRouter();
  const { isAuthenticated, isInitializing, user, signOut } = useAuth();
  const isIntern = user?.role === 'INTERN';

  useEffect(() => {
    if (isInitializing) {
      return;
    }
    if (!isAuthenticated) {
      // Replace the guarded route directly; popping a tab navigator can trigger
      // an unhandled POP_TO_TOP when no stack history exists.
      router.replace('/welcome');
      return;
    }
    if (!isIntern) {
      if (user?.role === 'COMPANY') {
        // Signed in, but as the wrong role — send them to their own home instead of
        // bouncing an already-authenticated user back to the login screen.
        router.replace('/company');
        return;
      }
      // No mobile experience exists for any other role (e.g. ADMIN). Never park an
      // authenticated session on Login — end it and let the normal signed-out routing
      // (Welcome) take over instead.
      void signOut();
    }
  }, [isInitializing, isAuthenticated, isIntern, user?.role, router, signOut]);

  if (isInitializing || !isAuthenticated || !isIntern) {
    return <View style={{ flex: 1, backgroundColor: PathoraColors.paper }} />;
  }

  return <>{children}</>;
}

export default function InternTabsLayout() {
  return (
    <InternTabsGuard>
      <Tabs
        tabBar={(props) => <InternLiquidTabBar {...props} />}
        screenOptions={{ headerShown: false }}>
        <Tabs.Screen name="index" options={{ title: 'Home' }} />
        <Tabs.Screen name="internships" options={{ title: 'Internships' }} />
        <Tabs.Screen name="favorites" options={{ title: 'Favorites' }} />
        <Tabs.Screen name="applications" options={{ title: 'Applications' }} />
        <Tabs.Screen name="profile" options={{ title: 'Profile' }} />
      </Tabs>
    </InternTabsGuard>
  );
}
