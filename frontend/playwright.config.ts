import { defineConfig, devices } from '@playwright/test';
export default defineConfig({
  testDir: './tests', fullyParallel: false,
  use: {baseURL:'http://localhost:4173', trace:'retain-on-failure'},
  projects: [
    {name:'desktop',use:{...devices['Desktop Chrome']}},
    {name:'mobile',use:{...devices['Pixel 7']}},
  ],
  webServer: {command:'pnpm exec vite preview --host localhost --port 4173 --strictPort',url:'http://localhost:4173',reuseExistingServer:false},
});
