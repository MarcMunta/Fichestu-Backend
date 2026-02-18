@echo off
setlocal

where docker >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Docker no esta instalado o no esta en el PATH.
  exit /b 1
)

docker compose up --build -d
if errorlevel 1 (
  echo [ERROR] No se pudo levantar Docker Compose.
  exit /b 1
)

echo.
echo Backend y MySQL levantados correctamente.
echo - Backend: http://localhost:8081
echo - MySQL:   localhost:3306 (db: fichestu_db, user: root, pass: root)
echo.
echo Para ver logs: docker compose logs -f backend
echo Para parar:    docker compose down
echo.
echo Para autorebuild en tiempo real: docker compose watch

exit /b 0
