import { defineConfig, devices } from '@playwright/test';

const isCI = !!process.env.CI;

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: isCI,
  retries: isCI ? 3 : 1,
  workers: isCI ? 1 : undefined,
  timeout: isCI ? 60000 : 30000,
  reporter: [
    ['html'],
    ['list']
  ],
  // Use platform-agnostic snapshot names (include browser but not OS)
  snapshotPathTemplate: '{testDir}/{testFileDir}/{testFileName}-snapshots/{arg}-{projectName}{ext}',
  use: {
    baseURL: 'http://localhost:8080',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    viewport: { width: 1280, height: 720 },
    // Longer timeouts for CI
    actionTimeout: isCI ? 15000 : 10000,
    navigationTimeout: isCI ? 30000 : 15000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
    {
      name: 'mobile-chrome',
      use: { ...devices['Pixel 5'] },
    },
    {
      name: 'mobile-safari',
      use: { ...devices['iPhone 12'] },
    },
  ],
  webServer: {
    command: 'cd .. && python3 -m http.server 8080 --directory build/dist/js/productionExecutable',
    url: 'http://localhost:8080',
    reuseExistingServer: !isCI,
    timeout: 120000,
  },
  expect: {
    timeout: isCI ? 10000 : 5000,
    toHaveScreenshot: {
      // Lower tolerances now that we use system fonts and disable anti-aliasing
      // Small buffer for scrollbar/cursor rendering differences
      maxDiffPixels: 500,
      maxDiffPixelRatio: 0.01,
      threshold: 0.2,
      animations: 'disabled',
    },
  },
});
