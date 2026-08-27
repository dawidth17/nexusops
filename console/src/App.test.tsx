import {
    render,
    screen
} from "@testing-library/react";

import {
    MemoryRouter
} from "react-router-dom";

import {
    beforeEach,
    describe,
    expect,
    it
} from "vitest";

import { App } from "./App";
import { AuthProvider } from "./auth";

describe(
    "authenticated routing",
    () => {
        beforeEach(
            () => {
                sessionStorage.clear();
            }
        );

        it(
            "redirects an unauthenticated protected route to login",
            async () => {
                render(
                    <MemoryRouter
                        initialEntries={[
                            "/alerts"
                        ]}
                    >
                        <AuthProvider>
                            <App />
                        </AuthProvider>
                    </MemoryRouter>
                );

                expect(
                    await screen.findByRole(
                        "heading",
                        {
                            name:
                                "Sign in to NexusOps"
                        }
                    )
                ).toBeInTheDocument();
            }
        );
    }
);