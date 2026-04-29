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

## Ejecutar con Docker (backend + MySQL + phpMyAdmin)

Desde la raiz del repo:

- Windows: `run-docker.bat`
- Manual: `docker compose up --build -d`

Servicios:

- Backend: `http://localhost:8080`
- MySQL: `localhost:3307` (db: `fichestu_db`, user: `root`, pass: `root`)
- phpMyAdmin: `http://localhost:8082` (server: `db`, user: `root`, pass: `root`)

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

- `src/main/resources/db/migration/V1__...sql`
- `src/main/resources/db/migration/V2__...sql`
- `src/main/resources/db/migration/V3__...sql`

Flyway lo detecta y lo ejecuta al arrancar el backend.

## API Contract

Todos los endpoints salvo registro/login requieren:

```http
Authorization: Bearer <jwt>
```

Auth:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/me`
- Alias legacy: `GET /api/auth/me`

Ejemplo login:

```json
{ "email": "alice@test.com", "password": "secret123" }
```

Respuesta:

```json
{ "token": "eyJ...", "message": "Login correcto", "success": true }
```

Market:

- `GET /api/market`
- `POST /api/market/buy`
- `POST /api/market/sell`
- Aliases legacy: `/api/game/market`, `/api/game/market/buy`, `/api/game/market/sell`

Ejemplo trade:

```json
{ "tokenId": 1, "quantity": 1 }
```

Rewards:

- `POST /api/rewards/claim`
- Alias legacy: `POST /api/game/rewarded/claim`

Ball Room:

- `POST /api/games/ball-room/join`
- `GET /api/games/ball-room/{matchId}`
- `POST /api/games/ball-room/{matchId}/pick`
- `POST /api/games/ball-room/{matchId}/reveal`

Ejemplo pick:

```json
{ "ballId": 7 }
```

Battle:

- `GET /api/games/battle/{matchId}`
- `POST /api/games/battle/{matchId}/action`
- `POST /api/games/battle/{matchId}/resolve-round`

Ejemplo action:

```json
{ "action": "ATTACK", "tokenId": 1 }
```

Estados principales:

- `WAITING`
- `PICKING`
- `READY_REVEAL`
- `REVEALED`
- `IN_PROGRESS`
- `FINISHED`
- `CLOSED`

Reglas implementadas:

- JWT firmado con expiracion.
- Passwords nuevos con BCrypt.
- Mercado transaccional, precio/saldo calculado en backend.
- Bonus persistido con cooldown.
- Sala real de 2 usuarios para primera version playable sin bots obligatorios.
- Multiplicadores generados en backend al reveal.
- Acciones de battle persistidas por ronda; no se resuelve hasta que todos los vivos envian accion.
- Impacto de ganador al mercado se aplica una sola vez.
