@echo off
setlocal
cd /d "%~dp0"
title Afrogo FM - PC
where node >nul 2>nul
if errorlevel 1 (
  echo ERROR: Instala Node.js LTS desde https://nodejs.org/ antes de abrir Afrogo FM.
  pause
  exit /b 1
)
if not exist "node_modules\electron\dist\electron.exe" (
  echo Preparando Afrogo FM por primera vez...
  call npm install
  if errorlevel 1 (
    echo ERROR al instalar las dependencias de Afrogo FM.
    pause
    exit /b 1
  )
)
call npm start
if errorlevel 1 (
  echo Afrogo FM se cerro con un error. Conserva esta ventana para revisar el mensaje.
  pause
)
