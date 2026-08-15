# Local infrastructure

The local infrastructure is managed with Docker Compose.

## PostgreSQL

ServiceCore uses PostgreSQL for its database.

### 1. Create the local environment file

Copy the example configuration:

```bash
cp .env.example .env
```

The `.env` file is local and is not committed to Git.

Example:

```env
SERVICECORE_DB_NAME=servicecore
SERVICECORE_DB_USER=servicecore
SERVICECORE_DB_PASSWORD=change_me
SERVICECORE_DB_PORT=5433
```

Change the password before starting the database.

Port `5433` is used on the local machine because port `5432` may already be used by another PostgreSQL instance.

PostgreSQL still runs on port `5432` inside the Docker container.

### 2. Validate the Docker Compose configuration

```bash
docker compose config --quiet
```

If no error is displayed, the configuration is valid.

### 3. Start PostgreSQL

```bash
docker compose up -d servicecore-db
```

Check the container status:

```bash
docker compose ps
```

The database should eventually show:

```text
healthy
```

The expected port mapping is:

```text
127.0.0.1:5433->5432/tcp
```

### 4. Check PostgreSQL logs

```bash
docker compose logs servicecore-db
```

### 5. Connect to PostgreSQL

```bash
docker compose exec servicecore-db psql -U servicecore -d servicecore
```

Useful checks:

```sql
SELECT version();
SELECT current_database();
SELECT current_user;
```

Exit `psql` with:

```text
\q
```

### 6. Test data persistence

Create a temporary table:

```bash
docker compose exec servicecore-db psql -U servicecore -d servicecore -c "CREATE TABLE foundation_check (id INTEGER PRIMARY KEY);"
```

Insert a value:

```bash
docker compose exec servicecore-db psql -U servicecore -d servicecore -c "INSERT INTO foundation_check VALUES (1);"
```

Check the value:

```bash
docker compose exec servicecore-db psql -U servicecore -d servicecore -c "SELECT * FROM foundation_check;"
```

Stop and remove the container:

```bash
docker compose down
```

Start PostgreSQL again:

```bash
docker compose up -d servicecore-db
```

After the database becomes healthy, check that the value still exists:

```bash
docker compose exec servicecore-db psql -U servicecore -d servicecore -c "SELECT * FROM foundation_check;"
```

Remove the temporary table:

```bash
docker compose exec servicecore-db psql -U servicecore -d servicecore -c "DROP TABLE foundation_check;"
```

The data survives container recreation because PostgreSQL uses a Docker named volume.

### 7. Stop PostgreSQL

```bash
docker compose down
```

Do not use `docker compose down -v` unless the database volume should also be deleted.
