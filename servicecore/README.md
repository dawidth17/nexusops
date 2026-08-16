# ServiceCore

ServiceCore is the ITSM and asset management backend for NexusOps.

It uses Java 21, Spring Boot, PostgreSQL and Flyway.

## Local development

Start PostgreSQL:

```bash
cd ../infra/compose
docker compose up -d servicecore-db
```

Load the local database configuration:

```bash
cd ../../servicecore
set -a
source ../infra/compose/.env
set +a
```

Run the application:

```bash
./mvnw spring-boot:run
```

Run the tests:

```bash
./mvnw clean test
```

Check application health:

```bash
curl http://localhost:8080/actuator/health
```