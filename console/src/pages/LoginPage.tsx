import {
    Navigate
} from "react-router-dom";

import { useAuth } from "../auth";

export function LoginPage() {
    const {
        status,
        login,
        error
    } = useAuth();

    if (
        status === "authenticated"
    ) {
        return (
            <Navigate
                to="/"
                replace
            />
        );
    }

    return (
        <main
            className="login-page"
        >
            <section
                className="login-card"
            >
                <div
                    className="login-card__brand"
                >
                    N
                </div>

                <p
                    className="eyebrow"
                >
                    NexusOps
                </p>

                <h1>
                    Sign in to NexusOps
                </h1>

                <p>
                    Authenticate with Keycloak
                    to access the shared
                    operator Console.
                </p>

                {
                    error !== null
                        ? (
                            <div
                                className="inline-error"
                                role="alert"
                            >
                                {error}
                            </div>
                        )
                        : null
                }

                <button
                    type="button"
                    className="button button--primary button--full"
                    onClick={
                        () => {
                            void login();
                        }
                    }
                >
                    Sign in
                </button>
            </section>
        </main>
    );
}