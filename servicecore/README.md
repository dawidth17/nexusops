# ServiceCore

ServiceCore is the ITSM and asset management backend for NexusOps.

It uses Java 21, Spring Boot, PostgreSQL, Flyway, and Spring Security.

## Authentication

ServiceCore supports Keycloak authentication through OpenID Connect.

When the `oidc` Spring profile is active, ServiceCore operates as an OAuth 2.0 resource server.

JWT access tokens are validated for:

- signature
- issuer
- expiration

Authorization is enforced server-side.

The main ServiceCore roles are:

```text
EMPLOYEE
TECHNICIAN
MANAGER
ADMIN
```

Keycloak realm and client roles are converted to the corresponding ServiceCore roles.

The current authenticated user can be queried through:

```text
GET /api/v1/me
```

The response contains:

- Keycloak subject ID
- username
- mapped ServiceCore roles

## OIDC configuration

The OIDC configuration is stored in:

```text
src/main/resources/application-oidc.properties
```

The main environment variables are:

```text
NEXUSOPS_OIDC_ISSUER_URI
NEXUSOPS_OIDC_JWK_SET_URI
NEXUSOPS_OIDC_CLIENT_ID
```

The default local issuer is:

```text
http://127.0.0.1:8081/realms/nexusops
```

When ServiceCore runs inside Docker Compose, the public issuer remains unchanged while the JWKS endpoint can use the internal Docker network to communicate with Keycloak.

## Local development

Start PostgreSQL and Keycloak from Ubuntu / WSL:

```bash
cd ~/projects/nexusops/infra/compose

docker compose up \
  -d \
  servicecore-db \
  keycloak
```

Load the local configuration:

```bash
cd ~/projects/nexusops/servicecore

set -a
source ../infra/compose/.env
set +a
```

Run ServiceCore with OIDC:

```bash
SPRING_PROFILES_ACTIVE=oidc \
./mvnw spring-boot:run
```

The API is available at:

```text
http://127.0.0.1:8080
```

## Health

Check application health:

```bash
curl \
  http://127.0.0.1:8080/actuator/health
```

## Testing

Run the complete Maven verification pipeline:

```bash
./mvnw \
  --batch-mode \
  --no-transfer-progress \
  clean verify
```

The verification pipeline includes:

- unit tests
- integration tests
- OIDC security tests
- RBAC tests
- Checkstyle
- SpotBugs
- packaging

OIDC integration tests cover cases such as:

- unauthenticated API access
- allowed read access
- insufficient roles
- technician operations
- manager operations
- audit authorization
- current user identity and roles

## Docker

From the NexusOps repository root:

```bash
docker build \
  --tag nexusops-servicecore:dev \
  servicecore
```

The runtime image runs as a non-root user.

## Docker Compose

ServiceCore can run as part of the NexusOps development stack:

```bash
cd ~/projects/nexusops/infra/compose

docker compose up \
  -d \
  --build \
  servicecore-db \
  keycloak \
  servicecore-api
```

Useful local endpoints:

```text
ServiceCore API    http://127.0.0.1:8080
Keycloak           http://127.0.0.1:8081
```