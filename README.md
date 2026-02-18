# Fichestu-Backend

Backend en Java + Spring Boot para Fichestu.

## Estado actual

- `POST /api/auth/register`
- `POST /api/auth/login`
- Persistencia con Spring Data JPA + MySQL.
- Esquema completo de `Casino-BitRoyale` gestionado por migraciones Flyway.

## Ejecutar local (sin Docker)

- Windows: `./mvnw.cmd spring-boot:run`
- Tests: `./mvnw.cmd test`

## Ejecutar con Docker (backend + MySQL)

Desde la raiz del repo:

- Windows: `run-docker.bat`
- Manual: `docker compose up --build -d`

Servicios:

- Backend: `http://localhost:8081`
- MySQL: `localhost:3306` (db: `fichestu_db`, user: `root`, pass: `root`)

Parar servicios:

- `docker compose down`

## Actualizacion en tiempo real (Docker)

Con los contenedores levantados:

- `docker compose watch`

Esto reconstruye/reinicia el backend cuando hay cambios en el repo.
Las migraciones nuevas de Flyway se aplican automaticamente al reiniciar.

## Base de datos oficial del proyecto

La estructura completa esta en:

- `src/main/resources/db/migration/V0__casino_bitroyale_schema.sql`

Incluye tablas:

- `users`, `badges`, `user_badges`
- `tokens`, `user_wallets`, `token_price_history`
- `game_sessions`, `match_participants`, `match_cards`
- `transactions_log`

Tambien incluye seed inicial de tokens y usuarios (`SuperAdmin`, `Jugador1`).

## Regla de cambios de esquema

No edites migraciones ya aplicadas en entornos con datos.
Para cambios de BBDD, agrega un nuevo archivo:

- `src/main/resources/db/migration/V2__...sql`
- `src/main/resources/db/migration/V3__...sql`

Flyway lo detecta y lo ejecuta al arrancar el backend.
