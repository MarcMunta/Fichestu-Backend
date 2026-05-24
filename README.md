# Fichestu Backend

Backend Java + Spring Boot para Fichestu. Expone API REST, seguridad JWT, WebSocket de partidas, persistencia JPA y migraciones Flyway.

## Estado maximo realizado (5.2)

- Auth: registro, login, Google Sign-In, logout con revocacion JWT, roles, `/api/auth/me` y admin ping.
- Recuperacion de contrasena: solicitud, confirmacion, expiracion y envio opcional por SMTP/Resend/Google Apps Script.
- Juego: bootstrap, mercado, compra/venta, recompensas, sala de bolas, matchmaking, picks, revelado, batalla por rondas, cierre e impacto de ganador.
- Perfil: datos publicos, idioma, cambio de contrasena, avatar, fondo, estilos, badges y estadisticas.
- Notificaciones: consulta, marcar una/todas como leidas y borrado.
- Minijuegos: acceso, inicio de intento y finalizacion.
- Realtime: WebSocket `/ws/matches` para avisar cambios de partida.
- DB: MySQL local/Docker, PostgreSQL Supabase, migraciones Flyway separadas.
- CI: GitHub Actions ejecuta `mvnw test`.

## Stack

- Java 17.
- Spring Boot 4.0.2.
- Spring Web, WebSocket, Security, Validation, Mail.
- Spring Data JPA + Hibernate.
- Flyway.
- MySQL 8.4, PostgreSQL/Supabase y H2 para tests.
- Maven Wrapper.

## Requisitos

- JDK 17.
- Docker Desktop opcional para MySQL/phpMyAdmin.
- PowerShell en Windows.

## Comandos

Tests:

```powershell
.\mvnw.cmd test
```

Arranque local sin Docker:

```powershell
.\mvnw.cmd spring-boot:run
```

Build jar:

```powershell
.\mvnw.cmd clean package
```

## Variables principales

Produccion/local con MySQL:

```env
PORT=8080
DB_URL=jdbc:mysql://localhost:3306/fichestu_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USERNAME=root
DB_PASSWORD=root
JWT_SECRET=<secret-real-largo>
JWT_EXPIRATION_SECONDS=3600
HIBERNATE_DDL_AUTO=validate
```

Email opcional:

```env
EMAIL_NOTIFICATIONS_ENABLED=false
EMAIL_NOTIFICATIONS_FROM=fichestu.soporte@gmail.com
PASSWORD_RESET_MAIL_ENABLED=false
PASSWORD_RESET_FROM=fichestu.soporte@gmail.com
MAIL_HOST=localhost
MAIL_PORT=1025
MAIL_USERNAME=
MAIL_PASSWORD=
RESEND_API_KEY=
GOOGLE_MAIL_SCRIPT_URL=
GOOGLE_MAIL_SCRIPT_SECRET=
```

Mercado:

```env
MARKET_DAILY_RESET_CRON=0 0 0 * * *
MARKET_RESET_ZONE=Europe/Madrid
MARKET_TICK_INTERVAL_MS=300000
```

## Docker local

Desde `Fichestu-Backend`:

```powershell
.\run-docker.bat
```

Manual:

```powershell
docker compose up --build -d
```

Servicios:

- Backend: `http://localhost:8080`
- MySQL: `localhost:3307` (`fichestu_db`, user `root`, pass `root`)
- phpMyAdmin: `http://localhost:8082`

Parar:

```powershell
docker compose down
```

Recrear DB local si Flyway falla por datos antiguos:

```powershell
docker compose down -v
docker compose up --build -d
```

Hot reload Docker:

```powershell
docker compose watch
```

## Supabase/PostgreSQL

Perfil:

- Config: `src/main/resources/application-supabase.properties`
- Migraciones: `src/main/resources/db/migration-postgres`
- Secretos ejemplo: `.env.supabase.example`

Variables:

```env
SPRING_PROFILES_ACTIVE=supabase
DB_URL=jdbc:postgresql://aws-0-eu-west-3.pooler.supabase.com:5432/postgres?sslmode=require
DB_USERNAME=fichestu_app.mzzbrrmgmsjvuycuazjl
DB_PASSWORD=<password-del-role-fichestu_app>
JWT_SECRET=<secret-real-largo>
FLYWAY_ENABLED=false
```

Proyecto Supabase:

- Ref: `mzzbrrmgmsjvuycuazjl`
- API URL: `https://mzzbrrmgmsjvuycuazjl.supabase.co`
- DB host: `db.mzzbrrmgmsjvuycuazjl.supabase.co`
- Runtime role: `fichestu_app`

Arranque:

```powershell
.\run-supabase.bat
```

o:

```powershell
docker compose -f docker-compose.supabase.yml up --build -d
```

En modo Supabase, Flyway queda apagado por defecto porque el runtime role tiene permisos limitados. Migraciones se aplican con rol admin.

## Endpoints

Health:

- `GET /api/health`

Auth:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/google`
- `POST /api/auth/logout`
- `POST /api/auth/password-reset/request`
- `POST /api/auth/password-reset/confirm`
- `GET /api/auth/me`
- `GET /api/auth/admin/ping`

Game:

- `GET /api/game/bootstrap`
- `GET /api/game/market`
- `GET /api/game/match/state`
- `POST /api/game/market/buy`
- `POST /api/game/market/sell`
- `POST /api/game/ball-room/enter`
- `POST /api/game/matches/{matchId}/join`
- `POST /api/game/matches/{matchId}/matchmaking/cancel`
- `POST /api/game/matches/{matchId}/matchmaking/abandon`
- `POST /api/game/matches/{matchId}/abandon`
- `POST /api/game/matches/{matchId}/pick-ball`
- `POST /api/game/matches/{matchId}/reveal`
- `POST /api/game/matches/{matchId}/battle/round`
- `POST /api/game/matches/{matchId}/winner-impact`
- `POST /api/game/matches/{matchId}/close`
- `POST /api/game/rewarded/claim`

Profile:

- `GET /api/profile`
- `PUT /api/profile`
- `GET /api/profile/badges`
- `GET /api/profile/stats`
- `POST /api/profile/avatar`
- `GET /api/profile/avatar/{fileName}`
- `PUT /api/profile/style`
- `POST /api/profile/background`
- `GET /api/profile/background/{fileName}`
- `POST /api/profile/change-password`
- `PUT /api/profile/language`

Notifications:

- `GET /api/notifications`
- `POST /api/notifications/{notificationId}/read`
- `POST /api/notifications/read-all`
- `DELETE /api/notifications`

Minigames:

- `GET /api/minigames/access`
- `GET /api/minigames/{gameType}/access`
- `POST /api/minigames/attempts/start`
- `POST /api/minigames/attempts/{attemptId}/finish`

Realtime:

- `WS /ws/matches`

## DB y migraciones

MySQL:

- `src/main/resources/db/migration`

PostgreSQL/Supabase:

- `src/main/resources/db/migration-postgres`

Regla: no editar migraciones ya aplicadas en entornos con datos. Agregar nueva version `V19__descripcion.sql`, `V20__descripcion.sql`, etc.

## Tests

Tests usan H2 en memoria:

- `src/test/resources/application.properties`

Cobertura actual:

- Auth.
- Email automatizado.
- Contexto Spring.
- Juego/integracion.
- Perfil.

Ejecutar:

```powershell
.\mvnw.cmd test
```

## CI

Workflow:

- `.github/workflows/ci.yml`

Dispara en:

- `push` a `main` o `master`.
- `pull_request` a `main` o `master`.
- `workflow_dispatch`.

Job:

- checkout.
- setup Java 17.
- cache Maven.
- `./mvnw test`.
