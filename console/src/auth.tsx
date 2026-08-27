import {
    createContext,
    type ReactNode,
    useCallback,
    useContext,
    useEffect,
    useMemo,
    useState
} from "react";

import { config } from "./config";

type AuthenticationStatus =
    | "loading"
    | "authenticated"
    | "unauthenticated";

interface OidcDiscoveryDocument {
    issuer: string;
    authorization_endpoint: string;
    token_endpoint: string;
    end_session_endpoint?: string;
}

interface TokenClaims {
    sub?: unknown;
    preferred_username?: unknown;
    exp?: unknown;
    realm_access?: unknown;
    resource_access?: unknown;
}

interface AuthUser {
    subject: string;
    username: string;
    roles: string[];
}

interface AuthContextValue {
    status: AuthenticationStatus;
    accessToken: string | null;
    user: AuthUser | null;
    error: string | null;
    login: () => Promise<void>;
    logout: () => Promise<void>;
}

const storageKeys = Object.freeze({
    accessToken:
        "nexusops.access_token",

    idToken:
        "nexusops.id_token",

    state:
        "nexusops.oidc_state",

    verifier:
        "nexusops.pkce_verifier"
});

const AuthContext =
    createContext<AuthContextValue | undefined>(
        undefined
    );

let discoveryPromise:
    Promise<OidcDiscoveryDocument> | null =
    null;

function redirectUri(): string {
    return `${window.location.origin}/`;
}

function encodeBase64Url(
    bytes: Uint8Array
): string {
    let binary = "";

    for (const byte of bytes) {
        binary +=
            String.fromCharCode(byte);
    }

    return btoa(binary)
        .replace(/\+/g, "-")
        .replace(/\//g, "_")
        .replace(/=+$/g, "");
}

function randomValue(
    byteLength: number
): string {
    const bytes =
        new Uint8Array(byteLength);

    crypto.getRandomValues(bytes);

    return encodeBase64Url(bytes);
}

async function createCodeChallenge(
    verifier: string
): Promise<string> {
    const bytes =
        new TextEncoder().encode(
            verifier
        );

    const digest =
        await crypto.subtle.digest(
            "SHA-256",
            bytes
        );

    return encodeBase64Url(
        new Uint8Array(digest)
    );
}

function decodeJwtPayload(
    token: string
): TokenClaims {
    const parts =
        token.split(".");

    if (parts.length !== 3) {
        throw new Error(
            "token is not a JWT"
        );
    }

    const encodedPayload =
        parts[1]
            .replace(/-/g, "+")
            .replace(/_/g, "/");

    const padding =
        "=".repeat(
            (
                4
                - encodedPayload.length % 4
            ) % 4
        );

    const binary =
        atob(
            encodedPayload + padding
        );

    const bytes =
        Uint8Array.from(
            binary,
            character =>
                character.charCodeAt(0)
        );

    const json =
        new TextDecoder().decode(
            bytes
        );

    return JSON.parse(
        json
    ) as TokenClaims;
}

function collectRoleValues(
    rawRoles: unknown,
    roles: Set<string>
): void {
    if (!Array.isArray(rawRoles)) {
        return;
    }

    for (const rawRole of rawRoles) {
        if (
            typeof rawRole
            !== "string"
        ) {
            continue;
        }

        const role =
            rawRole
                .trim()
                .toLowerCase();

        if (role) {
            roles.add(role);
        }
    }
}

function extractRoles(
    claims: TokenClaims
): string[] {
    const roles =
        new Set<string>();

    if (
        typeof claims.realm_access
        === "object"
        && claims.realm_access
        !== null
    ) {
        const realmAccess =
            claims.realm_access as {
                roles?: unknown;
            };

        collectRoleValues(
            realmAccess.roles,
            roles
        );
    }

    if (
        typeof claims.resource_access
        === "object"
        && claims.resource_access
        !== null
    ) {
        const resources =
            claims.resource_access as Record<
                string,
                unknown
            >;

        for (
            const resource
            of Object.values(resources)
        ) {
            if (
                typeof resource
                !== "object"
                || resource === null
            ) {
                continue;
            }

            const clientAccess =
                resource as {
                    roles?: unknown;
                };

            collectRoleValues(
                clientAccess.roles,
                roles
            );
        }
    }

    return Array.from(roles).sort();
}

function userFromToken(
    token: string
): AuthUser {
    const claims =
        decodeJwtPayload(token);

    const now =
        Math.floor(
            Date.now() / 1000
        );

    if (
        typeof claims.exp
        !== "number"
        || claims.exp <= now
    ) {
        throw new Error(
            "access token is expired"
        );
    }

    if (
        typeof claims.sub
        !== "string"
        || !claims.sub.trim()
    ) {
        throw new Error(
            "access token subject is invalid"
        );
    }

    const username =
        typeof claims.preferred_username
            === "string"
            && claims.preferred_username.trim()
            ? claims.preferred_username.trim()
            : claims.sub.trim();

    return {
        subject:
            claims.sub.trim(),

        username,

        roles:
            extractRoles(claims)
    };
}

function clearSession(): void {
    sessionStorage.removeItem(
        storageKeys.accessToken
    );

    sessionStorage.removeItem(
        storageKeys.idToken
    );

    sessionStorage.removeItem(
        storageKeys.state
    );

    sessionStorage.removeItem(
        storageKeys.verifier
    );
}

async function loadDiscovery():
    Promise<OidcDiscoveryDocument> {
    if (discoveryPromise !== null) {
        return discoveryPromise;
    }

    discoveryPromise =
        (async () => {
            const response =
                await fetch(
                    `${config.issuer}/.well-known/openid-configuration`
                );

            if (!response.ok) {
                throw new Error(
                    "unable to load identity provider configuration"
                );
            }

            const discovery:
                OidcDiscoveryDocument =
                await response.json();

            if (
                discovery.issuer
                !== config.issuer
            ) {
                throw new Error(
                    "identity provider issuer does not match Console configuration"
                );
            }

            return discovery;
        })();

    return discoveryPromise;
}

async function exchangeAuthorizationCode(
    code: string,
    returnedState: string
): Promise<string> {
    const expectedState =
        sessionStorage.getItem(
            storageKeys.state
        );

    const verifier =
        sessionStorage.getItem(
            storageKeys.verifier
        );

    if (
        expectedState === null
        || expectedState
        !== returnedState
    ) {
        throw new Error(
            "OIDC state validation failed"
        );
    }

    if (verifier === null) {
        throw new Error(
            "PKCE verifier is missing"
        );
    }

    const discovery =
        await loadDiscovery();

    const response =
        await fetch(
            discovery.token_endpoint,
            {
                method: "POST",

                headers: {
                    "Content-Type":
                        "application/x-www-form-urlencoded"
                },

                body:
                    new URLSearchParams({
                        grant_type:
                            "authorization_code",

                        client_id:
                            config.clientId,

                        code,

                        redirect_uri:
                            redirectUri(),

                        code_verifier:
                            verifier
                    })
            }
        );

    if (!response.ok) {
        throw new Error(
            "authorization code exchange failed"
        );
    }

    const tokens =
        await response.json() as {
            access_token?: unknown;
            id_token?: unknown;
        };

    if (
        typeof tokens.access_token
        !== "string"
    ) {
        throw new Error(
            "identity provider response does not contain an access token"
        );
    }

    sessionStorage.setItem(
        storageKeys.accessToken,
        tokens.access_token
    );

    if (
        typeof tokens.id_token
        === "string"
    ) {
        sessionStorage.setItem(
            storageKeys.idToken,
            tokens.id_token
        );
    }

    sessionStorage.removeItem(
        storageKeys.state
    );

    sessionStorage.removeItem(
        storageKeys.verifier
    );

    window.history.replaceState(
        {},
        document.title,
        redirectUri()
    );

    return tokens.access_token;
}

export function AuthProvider(
    {
        children
    }: {
        children: ReactNode;
    }
) {
    const [
        status,
        setStatus
    ] = useState<AuthenticationStatus>(
        "loading"
    );

    const [
        accessToken,
        setAccessToken
    ] = useState<string | null>(
        null
    );

    const [
        user,
        setUser
    ] = useState<AuthUser | null>(
        null
    );

    const [
        error,
        setError
    ] = useState<string | null>(
        null
    );

    const setAuthenticated =
        useCallback(
            (
                token: string
            ) => {
                const authenticatedUser =
                    userFromToken(token);

                setAccessToken(token);
                setUser(authenticatedUser);
                setStatus(
                    "authenticated"
                );
            },
            []
        );

    const setSignedOut =
        useCallback(
            () => {
                setAccessToken(null);
                setUser(null);
                setStatus(
                    "unauthenticated"
                );
            },
            []
        );

    useEffect(
        () => {
            let cancelled = false;

            async function initialize():
                Promise<void> {
                const query =
                    new URLSearchParams(
                        window.location.search
                    );

                const oidcError =
                    query.get("error");

                if (oidcError !== null) {
                    clearSession();

                    if (!cancelled) {
                        setError(
                            "Authentication was not completed."
                        );

                        setSignedOut();
                    }

                    return;
                }

                const code =
                    query.get("code");

                const state =
                    query.get("state");

                if (
                    code !== null
                    && state !== null
                ) {
                    try {
                        const token =
                            await exchangeAuthorizationCode(
                                code,
                                state
                            );

                        if (!cancelled) {
                            setAuthenticated(
                                token
                            );
                        }
                    } catch {
                        clearSession();

                        if (!cancelled) {
                            setError(
                                "Authentication failed."
                            );

                            setSignedOut();
                        }
                    }

                    return;
                }

                const storedToken =
                    sessionStorage.getItem(
                        storageKeys.accessToken
                    );

                if (
                    storedToken
                    === null
                ) {
                    if (!cancelled) {
                        setSignedOut();
                    }

                    return;
                }

                try {
                    if (!cancelled) {
                        setAuthenticated(
                            storedToken
                        );
                    }
                } catch {
                    clearSession();

                    if (!cancelled) {
                        setSignedOut();
                    }
                }
            }

            void initialize();

            return () => {
                cancelled = true;
            };
        },
        [
            setAuthenticated,
            setSignedOut
        ]
    );

    const login =
        useCallback(
            async (): Promise<void> => {
                setError(null);

                const discovery =
                    await loadDiscovery();

                const verifier =
                    randomValue(64);

                const challenge =
                    await createCodeChallenge(
                        verifier
                    );

                const state =
                    randomValue(32);

                sessionStorage.setItem(
                    storageKeys.verifier,
                    verifier
                );

                sessionStorage.setItem(
                    storageKeys.state,
                    state
                );

                const authorizationUrl =
                    new URL(
                        discovery
                            .authorization_endpoint
                    );

                authorizationUrl
                    .searchParams
                    .set(
                        "client_id",
                        config.clientId
                    );

                authorizationUrl
                    .searchParams
                    .set(
                        "response_type",
                        "code"
                    );

                authorizationUrl
                    .searchParams
                    .set(
                        "scope",
                        config.scope
                    );

                authorizationUrl
                    .searchParams
                    .set(
                        "redirect_uri",
                        redirectUri()
                    );

                authorizationUrl
                    .searchParams
                    .set(
                        "state",
                        state
                    );

                authorizationUrl
                    .searchParams
                    .set(
                        "code_challenge",
                        challenge
                    );

                authorizationUrl
                    .searchParams
                    .set(
                        "code_challenge_method",
                        "S256"
                    );

                window.location.assign(
                    authorizationUrl.toString()
                );
            },
            []
        );

    const logout =
        useCallback(
            async (): Promise<void> => {
                setError(null);

                const idToken =
                    sessionStorage.getItem(
                        storageKeys.idToken
                    );

                clearSession();
                setSignedOut();

                try {
                    const discovery =
                        await loadDiscovery();

                    if (
                        typeof discovery
                            .end_session_endpoint
                        !== "string"
                    ) {
                        return;
                    }

                    const logoutUrl =
                        new URL(
                            discovery
                                .end_session_endpoint
                        );

                    logoutUrl.searchParams.set(
                        "client_id",
                        config.clientId
                    );

                    logoutUrl.searchParams.set(
                        "post_logout_redirect_uri",
                        redirectUri()
                    );

                    if (idToken !== null) {
                        logoutUrl
                            .searchParams
                            .set(
                                "id_token_hint",
                                idToken
                            );
                    }

                    window.location.assign(
                        logoutUrl.toString()
                    );
                } catch {
                    setError(
                        "Local session ended, but identity provider logout could not be completed."
                    );
                }
            },
            [
                setSignedOut
            ]
        );

    const value =
        useMemo<AuthContextValue>(
            () => ({
                status,
                accessToken,
                user,
                error,
                login,
                logout
            }),
            [
                status,
                accessToken,
                user,
                error,
                login,
                logout
            ]
        );

    return (
        <AuthContext.Provider
            value={value}
        >
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth():
    AuthContextValue {
    const context =
        useContext(AuthContext);

    if (context === undefined) {
        throw new Error(
            "useAuth must be used inside AuthProvider"
        );
    }

    return context;
}