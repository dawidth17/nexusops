import {
    defineConfig,
    devices
} from "@playwright/test";

const baseURL =
    process.env.NEXUSOPS_E2E_CONSOLE_URL
    ?? "http://127.0.0.1:3001";

export default defineConfig({
    testDir: "./e2e",

    testMatch: "**/live-recovery.live.ts",

    fullyParallel: false,

    forbidOnly: Boolean(
        process.env.CI
    ),

    retries: process.env.CI
        ? 1
        : 0,

    workers: 1,

    reporter: "list",

    use: {
        baseURL,
        trace: "on-first-retry"
    },

    projects: [
        {
            name: "chromium",
            use: {
                ...devices["Desktop Chrome"]
            }
        }
    ]
});