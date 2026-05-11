@echo off
setlocal EnableDelayedExpansion

where docker >nul 2>&1
if errorlevel 1 (
  if exist "C:\Program Files\Docker\Docker\resources\bin\docker.exe" (
    set "PATH=%PATH%;C:\Program Files\Docker\Docker\resources\bin"
  ) else (
    echo [ERROR] Docker no esta instalado o no esta en el PATH.
    exit /b 1
  )
)

where git >nul 2>&1
if errorlevel 1 (
  echo [WARN] Git no esta instalado o no esta en el PATH. Asegurate manualmente de estar en main y actualizado.
) else (
  for /f "tokens=*" %%b in ('git branch --show-current 2^>nul') do set "CURRENT_BRANCH=%%b"
  if not "!CURRENT_BRANCH!"=="main" (
    echo [ERROR] Estas en la rama "!CURRENT_BRANCH!". Cambia a main antes de levantar Docker.
    echo Ejecuta: git checkout main ^&^& git pull origin main
    exit /b 1
  )

  git pull origin main
  if errorlevel 1 (
    echo [ERROR] No se pudo actualizar main desde origin.
    exit /b 1
  )
)

if not exist ".env" (
  if exist ".env.example" (
    copy ".env.example" ".env" >nul
    echo [INFO] Creado .env desde .env.example.
  ) else (
    echo [ERROR] Falta .env y no existe .env.example para crearlo.
    exit /b 1
  )
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "$envPath='.env'; $content=Get-Content -Raw $envPath; $match=[regex]::Match($content,'(?m)^JWT_SECRET=(.*)$'); $current=if($match.Success){$match.Groups[1].Value.Trim()}else{''}; if([string]::IsNullOrWhiteSpace($current) -or $current -eq 'change-this-local-jwt-secret-at-least-32-bytes'){ $bytes=New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes); $secret=[Convert]::ToBase64String($bytes); if($match.Success){$content=[regex]::Replace($content,'(?m)^JWT_SECRET=.*$','JWT_SECRET='+$secret,1)}else{if($content.Length -gt 0 -and $content[$content.Length-1] -ne [char]10){$content+=[Environment]::NewLine}; $content+='JWT_SECRET='+$secret+[Environment]::NewLine}; Set-Content -Path $envPath -Value $content -NoNewline; Write-Host '[INFO] JWT_SECRET generado en .env.' }"
if errorlevel 1 (
  echo [ERROR] No se pudo generar JWT_SECRET en .env.
  exit /b 1
)

docker compose up --build -d
if errorlevel 1 (
  echo [ERROR] No se pudo levantar Docker Compose.
  exit /b 1
)

echo.
echo Backend, MySQL y phpMyAdmin levantados correctamente.
echo - Backend: http://localhost:8080
echo - MySQL:   localhost:3307 (db: fichestu_db, user: root, pass: root)
echo - phpMyAdmin: http://localhost:8082 (user: root, pass: root)
echo.
echo Para ver logs: docker compose logs -f backend
echo Para parar:    docker compose down
echo.
echo Para autorebuild en tiempo real: docker compose watch

exit /b 0
