# Fichestu-Backend

Backend en **Java + Spring Boot** para Fichestu, pensado para conectarse con el frontend en:
[Fichestu-Frontend](https://github.com/MarcMunta/Fichestu-Frontend)

## Funcionalidad implementada (MVP)

- `POST /api/auth/register`
- `POST /api/auth/login`
- Persistencia con Hibernate (Spring Data JPA) en MySQL.

El resto de módulos anteriores han sido eliminados para dejar solo autenticación.

## Ejecutar

Desde la raíz del repo:

- Windows: `./mvnw.cmd spring-boot:run`
- Tests: `./mvnw.cmd test`

## Ejecutar con Docker (backend + MySQL)

Desde la raíz del repo:

- Windows: `run-docker.bat`
- Manual: `docker compose up --build -d`

Servicios levantados:

- Backend: `http://localhost:8081`
- MySQL: `localhost:3306` (db: `fichestu`, user: `root`, pass: `root`)

Parar servicios:

- `docker compose down`

## Base de datos (Hibernate + MySQL)

Variables opcionales para configurar MySQL:

- `DB_URL` (ej: `jdbc:mysql://localhost:3306/fichestu?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`)
- `DB_USERNAME`
- `DB_PASSWORD`
- `DB_DRIVER` (ej: `com.mysql.cj.jdbc.Driver`)
- `HIBERNATE_DDL_AUTO` (por defecto `update`)

En tests se usa H2 automáticamente (`src/test/resources/application.properties`).
