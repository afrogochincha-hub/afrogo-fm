# AfroGo DJ, recomendaciones y playlists compartibles

## Qué incluye esta versión

- Recomendaciones por cuenta con señales de favoritos, género/artista del historial y descubrimiento. Endpoint autenticado: `GET /api/me/recommendations?limit=24`.
- Sesiones DJ por ambiente (`personalizado`, `relajado`, `fiesta`, `energía`). Endpoint autenticado: `POST /api/me/dj-session` con `{ "mood": "fiesta", "limit": 20 }`.
- Si existe `OPENAI_API_KEY` en el entorno del backend, el servidor envía al modelo únicamente los metadatos de las canciones candidatas y géneros recientes para ordenar IDs ya existentes. No envía email, ID de usuario ni timestamps de reproducción. Si falla el proveedor, se usa el ranking local.
- Privacidad de playlist: `private` (predeterminada), `unlisted` (solo por enlace secreto) y `public`. Endpoint de enlace: `GET /api/playlists/shared/:shareToken`. Las listas privadas no se devuelven por este endpoint.

## Despliegue del backend

1. Haz copia de seguridad del directorio del gateway y del volumen de datos antes de actualizar.
2. Copia el contenido de `backend/server.js`, `backend/package.json` y demás archivos backend actualizados a tu carpeta de despliegue usando tu procedimiento habitual. No reemplaces `.env` ni el volumen Docker.
3. Si deseas IA generativa, agrega `OPENAI_API_KEY` y `OPENAI_DJ_MODEL=gpt-4o-mini` al `.env` privado del servidor. La clave nunca debe ponerse en `desktop/app-config.json`, código Android, frontend o repositorio. Puedes omitirla: el DJ seguirá funcionando con el ranking local.
4. Reconstruye solo el contenedor `afrogo-api` con el compose de tu instalación, después de revisar `docker compose config -q`. No ejecutes `docker compose down -v` ni borres `afrogo-users`.
5. Comprueba `/health`, inicia sesión en una cuenta de prueba y valida recomendaciones, creación de sesión DJ y los tres niveles de privacidad.

## Pruebas

En `backend/`: `npm run check`. La prueba de nube valida que una playlist `unlisted` se abre con el token de compartir y que editarla sin mandar los campos de privacidad no elimina su token ni cambia su visibilidad.

## Pendiente antes de un lanzamiento público

- La pantalla de enlace compartido es actualmente una respuesta JSON del API; falta una página pública atractiva y un flujo dentro de la app para importar/seguir una playlist.
- Android muestra las recomendaciones/sesión en diálogos informativos; falta conectar la cola DJ al reproductor para reproducirla como una sesión completa.
- No hay crossfade, beat matching ni locutor de voz todavía. Para ello se requiere implementar un motor de mezcla/voz y probarlo con el servicio multimedia y Android Auto.
- Probar en un dispositivo real y compilar el APK/EXE mediante GitHub Actions antes de distribuir.
