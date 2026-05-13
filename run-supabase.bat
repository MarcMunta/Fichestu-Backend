@echo off
setlocal

if not exist ".env" (
  echo Falta .env. Copia .env.supabase.example a .env y rellena secretos.
  exit /b 1
)

docker compose -f docker-compose.supabase.yml up --build -d
