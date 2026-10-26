# AfroGo FM

Código fuente de AfroGo FM para Windows, Android y Android Auto, conectado a la biblioteca central de AzuraCast mediante un gateway HTTPS.

## Componentes y versiones de esta entrega

- **PC Windows (Electron):** 3.32.0. La sesión se guarda cifrada con Electron `safeStorage` y se borra cuando el usuario elige **Cerrar sesión**.
- **Android y Android Auto:** 1.10.0 (`versionCode` 13). La app móvil tiene navegación inferior, Inicio, Buscar, Biblioteca, AfroGo+, Crear, mini reproductor y reproductor ampliado. La sesión se guarda en el almacenamiento privado de la aplicación, se renueva al volver a abrirla y se elimina con **Cerrar sesión**.
- **Gateway/backend:** 1.3.0. Emite token de acceso de 7 días y token de renovación de hasta 365 días.

## Carpetas

- `desktop/`: aplicación Windows con Electron.
- `android/`: aplicación nativa Android y servicio multimedia compatible con Android Auto.
- `backend/`: gateway de autenticación, biblioteca personal y proxy de AzuraCast.
- `docs/`: documentación de configuración, actualización y desarrollo.
- `.github/workflows/build.yml`: pruebas del gateway y compilaciones automáticas de Windows y Android.

## Funciones nuevas: Para ti, playlists sociales y AfroGo DJ

- **Recomendaciones personales:** `GET /api/me/recommendations` clasifica canciones del catálogo usando géneros, artistas, favoritos e historial reciente de la cuenta.
- **Playlists compartibles:** cada lista admite `private`, `unlisted` (enlace secreto) o `public`. Las playlists nuevas son privadas por defecto. El endpoint `GET /api/playlists/shared/:shareToken` expone únicamente listas no privadas y no devuelve correo ni ID del propietario.
- **AfroGo DJ:** `POST /api/me/dj-session` crea una sesión ordenada con canciones reales del catálogo. Si `OPENAI_API_KEY` se configura solo en el backend, la IA puede ordenar las canciones existentes según el ambiente; sin clave usa el selector local. No inventa títulos/IDs.
- **Límites actuales:** esta versión aún no hace crossfade/mezcla de audio ni locuciones con voz. La app móvil muestra la selección en una lista informativa; la reproducción continua de la sesión y la página pública visual de playlist requieren trabajo adicional.

Para activar la ordenación generativa de AfroGo DJ, configura `OPENAI_API_KEY` en el `.env` privado del gateway (nunca en la app ni en GitHub) y opcionalmente `OPENAI_DJ_MODEL`. La clave es opcional y el proveedor puede generar costos. Consulta `docs/AFROGO_DJ_PLAYLISTS_RECOMENDACIONES.md`.

## API

La URL HTTPS usada por defecto es `https://afrogochincha.duckdns.org/afrogo-api`. En GitHub Actions puede sobrescribirse con la variable de repositorio `AFROGO_API_BASE_URL`, siempre que empiece por `https://`.

## Subir a GitHub Desktop

Sigue `SUBIR_A_GITHUB_PASO_A_PASO.txt`. Importa estos archivos en una copia limpia clonada con GitHub Desktop; conserva la carpeta oculta `.git` de esa copia. Este ZIP no contiene `.git`, claves privadas, `.env` real ni datos reales de usuarios.

## Comprobaciones ejecutadas al preparar el ZIP

- Sintaxis de `desktop/main.js` y `desktop/preload.js`: correcta.
- Estructura del código Kotlin revisada y versión Android aumentada a 1.9.0; este entorno no incluye Android SDK/Gradle para confirmar el APK.
- Sintaxis de los scripts JavaScript incluidos en `desktop/app/index.html`: correcta.
- `npm run check` en `backend/`: correcto, 5 pruebas pasaron incluyendo sesión persistente, renovación, aislamiento por cuenta y acceso a playlist compartida.
- `node --check` en los scripts JavaScript de la app de escritorio: correcto.
- El APK/EXE no se compiló aquí: falta Android Gradle/SDK y no se ejecutó electron-builder en este entorno.
- Marcadores de conflicto de Git (`<<<<<<<`, `=======`, `>>>>>>>`): no encontrados en los archivos fuente.

La compilación final del EXE y el APK debe confirmarse en **GitHub → Actions** tras subir esta versión, ya que este entorno no dispone del Android SDK/Gradle instalado para compilar el APK localmente.

## Seguridad

- No subas `backend/.env` real, contraseñas, `JWT_SECRET`, API keys ni claves de firma.
- No borres el volumen Docker `afrogo-users` del servidor: ahí se conserva la biblioteca personal de los usuarios.
- Los tokens de renovación de esta versión son JWT sin una lista de revocación por dispositivo; para cerrar la sesión en un dispositivo, usa **Cerrar sesión** en esa aplicación y evita dejarla abierta en equipos compartidos.
- Para desplegar el gateway, consulta `docs/ACTUALIZAR_GATEWAY_NUBE.md`. No reemplaces `.env` ni elimines el volumen de datos del servidor.
