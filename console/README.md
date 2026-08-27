# NexusOps Console

The NexusOps Console is the shared web interface used by NexusOps operators.

The Console is implemented with:

```text
React
TypeScript
Vite
React Router
Vitest
React Testing Library
Playwright
```

## Authentication

Authentication uses Keycloak through OpenID Connect.

The browser uses:

```text
OAuth 2.0 Authorization Code + PKCE
```

PKCE uses the `S256` challenge method.

The Console stores session authentication data in `sessionStorage`.

Frontend role checks are used only to control the user interface.

ServiceCore and OpsSight independently validate the access token and enforce authorization on every protected backend request.

## Pages

The Console provides:

```text
Overview
Hosts
Alerts
Incidents
```

Overview aggregates the current platform state from OpsSight and ServiceCore.

Hosts shows monitored hosts and derives useful operational state from their configured checks.

Alerts shows OpsSight alert lifecycle state and correlation IDs.

Incidents shows ServiceCore incident state and monitoring context.

Monitoring incidents expose the link back to their originating OpsSight alert through the stored source alert ID and correlation ID.

## Backend access

The production Console container uses Nginx as both the static file server and same-origin reverse proxy.

Browser requests use:

```text
/opssight/api/v1/...
/servicecore/api/v1/...
```

Nginx forwards those requests to the corresponding backend service on the Docker Compose network.

This avoids requiring permissive browser CORS configuration for the local NexusOps stack.

## Local development

Install dependencies:

```bash
npm install
```

Start the Vite development server:

```bash
npm run dev
```

The development server listens on:

```text
http://127.0.0.1:3001
```

The complete Docker Compose Console is also exposed on the same default address.

## Quality checks

Run TypeScript validation:

```bash
npm run typecheck
```

Run unit and component tests:

```bash
npm test
```

Build the production application:

```bash
npm run build
```

Install the Playwright Chromium browser:

```bash
npx playwright install --with-deps chromium
```

Run the browser tests:

```bash
npm run test:e2e
```

## Browser flow

Playwright covers the critical operator workflow:

```text
authenticated Console
    ->
OpsSight alert
    ->
linked ServiceCore incident
    ->
authorized incident lifecycle action
```

Backend requests in the browser test are controlled fixtures so the Console flow can be validated independently from the final full-platform end-to-end scenario.

The complete live NexusOps integration flow is covered by the later integration milestone.