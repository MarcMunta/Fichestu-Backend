# Fichestu-Backend

Backend en Java + Spring Boot para Fichestu.

## Estado actual

- `POST /api/auth/register`
- `POST /api/auth/login`
- Persistencia con Spring Data JPA + MySQL.
- Esquema completo de Fichestu gestionado por migraciones Flyway.

## Ejecutar local (sin Docker)

- Windows: `./mvnw.cmd spring-boot:run`
- Tests: `./mvnw.cmd test`

## Ejecutar con Docker (backend + MySQL + phpMyAdmin)

Desde la raiz del repo:

Antes de levantar contenedores, usa siempre `main` como fuente de verdad:

- `git checkout main`
- `git pull origin main`
- Copia `.env.example` a `.env` si aun no existe.
- Si usas `run-docker.bat`, el script crea `.env` y genera `JWT_SECRET` automaticamente si falta o sigue con el valor de ejemplo.

- Windows: `run-docker.bat`
- Manual: `docker compose up --build -d`

Servicios:

- Backend: `http://localhost:8080`
- MySQL: `localhost:3307` (db: `fichestu_db`, user: `root`, pass: `root`)
- phpMyAdmin: `http://localhost:8082` (server: `db`, user: `root`, pass: `root`)

Parar servicios:

- `docker compose down`

Si ya tenias una base de datos local creada antes de estos cambios y Flyway falla al arrancar, recrea los volumenes locales:

- `docker compose down -v`
- `docker compose up --build -d`

## Actualizacion en tiempo real (Docker)

Con los contenedores levantados:

- `docker compose watch`

Esto reconstruye/reinicia el backend cuando hay cambios en el repo.
Las migraciones nuevas de Flyway se aplican automaticamente al reiniciar.

## Usar Supabase como base de datos

Supabase hospeda PostgreSQL. Este backend sigue siendo una app Spring Boot: Supabase no ejecuta directamente un servidor Java. Flujo recomendado:

- Supabase: base de datos PostgreSQL.
- Backend Spring Boot: desplegado en Render/Railway/Fly/VPS o ejecutado local.
- Android: conectado al URL publico del backend Spring Boot.

Se ha anadido perfil `supabase`:

- Config: `src/main/resources/application-supabase.properties`
- Migraciones PostgreSQL: `src/main/resources/db/migration-postgres`
- Ejemplo de secretos: `.env.supabase.example`

Variables necesarias:

- `SPRING_PROFILES_ACTIVE=supabase`
- `DB_URL=jdbc:postgresql://aws-0-eu-west-3.pooler.supabase.com:5432/postgres?sslmode=require`
- `DB_USERNAME=fichestu_app.mzzbrrmgmsjvuycuazjl`
- `DB_PASSWORD=<password-del-role-fichestu_app>`
- `JWT_SECRET=<secret-real-largo>`
- `FLYWAY_ENABLED=false`

Proyecto creado para Fichestu:

- Supabase ref: `mzzbrrmgmsjvuycuazjl`
- API URL: `https://mzzbrrmgmsjvuycuazjl.supabase.co`
- DB host: `db.mzzbrrmgmsjvuycuazjl.supabase.co`
- Runtime DB role: `fichestu_app`
- Runtime connection: Supavisor session pooler, SSL, permisos limitados.

Arranque local contra Supabase:

- Docker: `run-supabase.bat`
- Manual: `docker compose -f docker-compose.supabase.yml up --build -d`
- Windows PowerShell sin Docker: carga esas variables y ejecuta `./mvnw.cmd spring-boot:run`

En modo Supabase, Flyway queda apagado por defecto (`FLYWAY_ENABLED=false`) porque el backend usa el role limitado `fichestu_app`. Las migraciones se aplican desde Supabase/Codex con permisos admin, no desde la app.

## Base de datos oficial del proyecto

La estructura completa esta en:

- `src/main/resources/db/migration/V0__fichestu_schema.sql`

Incluye tablas:

- `users`, `badges`, `user_badges`
- `tokens`, `user_wallets`, `token_price_history`
- `game_sessions`, `match_participants`, `match_cards`
- `transactions_log`

Tambien incluye seed inicial de tokens y usuarios (`SuperAdmin`, `Jugador1`).

## Regla de cambios de esquema

No edites migraciones ya aplicadas en entornos con datos.
Para cambios de BBDD, agrega un nuevo archivo:

- `src/main/resources/db/migration/V1__...sql`
- `src/main/resources/db/migration/V2__...sql`
- `src/main/resources/db/migration/V3__...sql`

Flyway lo detecta y lo ejecuta al arrancar el backend.
