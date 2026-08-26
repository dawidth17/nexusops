"use strict";

const config = window.NEXUSOPS_CONFIG;

const loginButton =
    document.getElementById("login");

const logoutButton =
    document.getElementById("logout");

const statusElement =
    document.getElementById("status");

const identityElement =
    document.getElementById("identity");

const usernameElement =
    document.getElementById("username");

const subjectElement =
    document.getElementById("subject");

const rolesElement =
    document.getElementById("roles");

const storageKeys = Object.freeze({
    accessToken: "nexusops.access_token",
    idToken: "nexusops.id_token",
    state: "nexusops.oidc_state",
    verifier: "nexusops.pkce_verifier"
});

let discoveryDocument = null;

function redirectUri() {
    return `${window.location.origin}/`;
}

function encodeBase64Url(bytes) {
    let binary = "";

    for (const byte of bytes) {
        binary += String.fromCharCode(byte);
    }

    return btoa(binary)
        .replace(/\+/g, "-")
        .replace(/\//g, "_")
        .replace(/=+$/g, "");
}

function randomValue(byteLength) {
    const bytes =
        new Uint8Array(byteLength);

    crypto.getRandomValues(bytes);

    return encodeBase64Url(bytes);
}

async function createCodeChallenge(
    verifier
) {
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

function decodeJwtPayload(token) {
    const parts = token.split(".");

    if (parts.length !== 3) {
        throw new Error(
            "token is not a JWT"
        );
    }

    const normalized =
        parts[1]
            .replace(/-/g, "+")
            .replace(/_/g, "/");

    const padding =
        "=".repeat(
            (4 - normalized.length % 4) % 4
        );

    return JSON.parse(
        atob(
            normalized + padding
        )
    );
}

async function loadDiscovery() {
    if (discoveryDocument !== null) {
        return discoveryDocument;
    }

    const response = await fetch(
        `${config.issuer}/.well-known/openid-configuration`
    );

    if (!response.ok) {
        throw new Error(
            "unable to load OIDC discovery document"
        );
    }

    discoveryDocument =
        await response.json();

    if (
        discoveryDocument.issuer
        !== config.issuer
    ) {
        throw new Error(
            "OIDC issuer does not match console configuration"
        );
    }

    return discoveryDocument;
}

function setSignedOut() {
    statusElement.textContent =
        "Not signed in.";

    identityElement.hidden = true;

    loginButton.hidden = false;
    logoutButton.hidden = true;
}

function setSignedIn(token) {
    const claims =
        decodeJwtPayload(token);

    const realmAccess =
        claims.realm_access ?? {};

    const roles =
        Array.isArray(
            realmAccess.roles
        )
            ? realmAccess.roles
            : [];

    usernameElement.textContent =
        claims.preferred_username
        ?? claims.sub
        ?? "-";

    subjectElement.textContent =
        claims.sub ?? "-";

    rolesElement.textContent =
        roles.length > 0
            ? roles.join(", ")
            : "no NexusOps roles";

    statusElement.textContent =
        "Authenticated with Keycloak.";

    identityElement.hidden = false;

    loginButton.hidden = true;
    logoutButton.hidden = false;
}

function clearSession() {
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

async function login() {
    loginButton.disabled = true;

    try {
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
                discovery.authorization_endpoint
            );

        authorizationUrl.searchParams.set(
            "client_id",
            config.clientId
        );

        authorizationUrl.searchParams.set(
            "response_type",
            "code"
        );

        authorizationUrl.searchParams.set(
            "scope",
            config.scope
        );

        authorizationUrl.searchParams.set(
            "redirect_uri",
            redirectUri()
        );

        authorizationUrl.searchParams.set(
            "state",
            state
        );

        authorizationUrl.searchParams.set(
            "code_challenge",
            challenge
        );

        authorizationUrl.searchParams.set(
            "code_challenge_method",
            "S256"
        );

        window.location.assign(
            authorizationUrl.toString()
        );

    } catch (error) {
        statusElement.textContent =
            `Login failed: ${error.message}`;

        loginButton.disabled = false;
    }
}

async function exchangeAuthorizationCode(
    code,
    returnedState
) {
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
        || returnedState !== expectedState
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

    const response = await fetch(
        discovery.token_endpoint,
        {
            method: "POST",
            headers: {
                "Content-Type":
                    "application/x-www-form-urlencoded"
            },
            body: new URLSearchParams({
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
        await response.json();

    if (
        typeof tokens.access_token
        !== "string"
    ) {
        throw new Error(
            "access token is missing"
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

    setSignedIn(
        tokens.access_token
    );
}

async function logout() {
    logoutButton.disabled = true;

    try {
        const discovery =
            await loadDiscovery();

        const idToken =
            sessionStorage.getItem(
                storageKeys.idToken
            );

        clearSession();

        if (
            typeof discovery.end_session_endpoint
            !== "string"
        ) {
            setSignedOut();
            return;
        }

        const logoutUrl =
            new URL(
                discovery.end_session_endpoint
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
            logoutUrl.searchParams.set(
                "id_token_hint",
                idToken
            );
        }

        window.location.assign(
            logoutUrl.toString()
        );

    } catch (error) {
        clearSession();

        statusElement.textContent =
            `Logout failed: ${error.message}`;

        logoutButton.disabled = false;
    }
}

async function initialize() {
    loginButton.addEventListener(
        "click",
        login
    );

    logoutButton.addEventListener(
        "click",
        logout
    );

    const query =
        new URLSearchParams(
            window.location.search
        );

    const oidcError =
        query.get("error");

    if (oidcError !== null) {
        clearSession();

        statusElement.textContent =
            `Authentication failed: ${oidcError}`;

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
            await exchangeAuthorizationCode(
                code,
                state
            );

            return;

        } catch (error) {
            clearSession();

            statusElement.textContent =
                `Authentication failed: ${error.message}`;

            return;
        }
    }

    const accessToken =
        sessionStorage.getItem(
            storageKeys.accessToken
        );

    if (accessToken === null) {
        setSignedOut();
        return;
    }

    try {
        const claims =
            decodeJwtPayload(
                accessToken
            );

        const now =
            Math.floor(
                Date.now() / 1000
            );

        if (
            typeof claims.exp !== "number"
            || claims.exp <= now
        ) {
            clearSession();
            setSignedOut();
            return;
        }

        setSignedIn(
            accessToken
        );

    } catch {
        clearSession();
        setSignedOut();
    }
}

initialize();