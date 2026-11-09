import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests", workers: 1, timeout: 30000,
  use: { baseURL: "http://127.0.0.1:3100", viewport: { width: 375, height: 812 }, trace: "off" },
  webServer: [
    { command: "node tests/contract-server.mjs", url: "http://127.0.0.1:8189/healthz", reuseExistingServer: false },
    { command: "npm run dev -- --port 3100", url: "http://127.0.0.1:3100", env: { BACKEND_URL: "http://127.0.0.1:8189" }, reuseExistingServer: false },
  ],
});
