# NexusOps Console

The NexusOps Console is the web interface for NexusOps operators.

The current implementation provides the initial shared authentication flow used by the platform.

## Authentication

The Console authenticates users through Keycloak using OpenID Connect.

The browser uses:

```text
OAuth 2.0 Authorization Code + PKCE
```

PKCE uses the `S256` code challenge method.

The Console does not store a Keycloak client secret.

Authentication currently supports:

- OIDC discovery
- login redirect
- PKCE code verifier and challenge generation
- authorization callback handling
- authorization code exchange
- access token storage for the browser session
- display of basic authenticated identity information
- Keycloak logout

Backend authorization is not performed by the Console.

ServiceCore and OpsSight independently validate access tokens and enforce roles server-side.

## Current implementation

The current Console is intentionally minimal and uses:

```text
HTML
CSS
JavaScript
```

It provides the authentication foundation before the full operator interface is implemented.

## Configuration

Browser configuration is stored in:

```text
config.js
```

The default local configuration uses:

```text
issuer:
http://127.0.0.1:8081/realms/nexusops

client:
nexusops-console
```

## Local development

The Console is served through Nginx in Docker Compose.

From Ubuntu / WSL:

```bash
cd ~/projects/nexusops/infra/compose

docker compose up \
  -d \
  keycloak \
  console
```

Open:

```text
http://127.0.0.1:3001
```

The login flow redirects the browser to Keycloak and then back to the Console.

## Security

The Console:

- uses Authorization Code rather than the implicit flow
- uses PKCE with SHA-256
- validates the OAuth state value
- does not contain a client secret
- stores session authentication data in `sessionStorage`
- relies on backend services for authorization decisions

The current JavaScript JWT parsing is used only to display user information.

It is not used to authorize backend operations.