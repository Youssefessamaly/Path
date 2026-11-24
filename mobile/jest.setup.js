// EXPO_PUBLIC_* vars normally come from .env.local via Metro; Jest doesn't load that, so tests
// need a stand-in base URL for any test that touches src/lib/api.ts (it reads this at module
// load time). Never a real backend — nothing in the test suite is meant to reach the network.
process.env.EXPO_PUBLIC_API_BASE_URL = 'https://test.invalid/api/v1';
