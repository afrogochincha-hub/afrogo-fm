@echo off
setlocal
title Afrogo FM - Crear ejecutable Windows
cd /d "%~dp0"

echo ===========================================
echo   Afrogo FM - Generador de ejecutable
echo ===========================================
echo.
if not exist "package.json" (
  echo ERROR: No se encuentra package.json.
  echo Descomprime el ZIP completo y ejecuta este archivo desde la carpeta afrogo_fm_pc.
  pause
  exit /b 1
)

where node >nul 2>nul
if errorlevel 1 (
  echo ERROR: No se encontro Node.js. Instala Node.js LTS desde https://nodejs.org/
  pause
  exit /b 1
)

echo Instalando dependencias...
call npm install
if errorlevel 1 (
  echo ERROR al instalar dependencias.
  pause
  exit /b 1
)

echo.
echo Generando ejecutable portable sin firma digital...
call npm run dist
if errorlevel 1 (
  echo.
  echo ERROR al generar el ejecutable. Conserva esta ventana y envia una captura.
  pause
  exit /b 1
)

echo.
echo EXE generado en:
echo %CD%\dist
pause
