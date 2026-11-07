import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import dns from 'node:dns';

// Java resolves localhost to IPv4; keep the local asset listener consistent.
dns.setDefaultResultOrder('ipv4first');

export default defineConfig(({ command, isPreview }) => ({
  plugins: [react()],
  base: command === 'serve' && !isPreview ? '/__vite/' : '/',
  server: { port: 5173, strictPort: true, hmr: { host: 'localhost', port: 5173 } },
  test: { environment: 'jsdom', setupFiles: ['./src/test-setup.ts'], include: ['src/**/*.test.{ts,tsx}'] },
}));
