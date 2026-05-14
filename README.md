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

## Email automatico

El backend puede enviar emails tras eventos clave: registro, movimientos de cuenta y notificaciones importantes.

Variables:

- `EMAIL_NOTIFICATIONS_ENABLED=true`
- `EMAIL_NOTIFICATIONS_FROM=noreply@tudominio.com`
- `MAIL_HOST=<smtp-host>`
- `MAIL_PORT=<smtp-port>`
- `MAIL_USERNAME=<smtp-user>`
- `MAIL_PASSWORD=<smtp-password>`
- `MAIL_SMTP_AUTH=true`
- `MAIL_SMTP_STARTTLS_ENABLE=true`
- `MAIL_SMTP_STARTTLS_REQUIRED=true`

La recuperacion de contrasena mantiene su interruptor separado:

- `PASSWORD_RESET_MAIL_ENABLED=true`
- `PASSWORD_RESET_FROM=noreply@tudominio.com`

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
