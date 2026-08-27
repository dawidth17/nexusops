import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
    plugins: [
        react()
    ],

    server: {
        host: "127.0.0.1",
        port: 3001,
        strictPort: true
    },

    preview: {
        host: "127.0.0.1",
        port: 3001,
        strictPort: true
    },

    test: {
        environment: "jsdom",

        setupFiles: [
            "./vitest.setup.ts"
        ],

        include: [
            "src/**/*.test.ts",
            "src/**/*.test.tsx"
        ],

        css: true
    }
});