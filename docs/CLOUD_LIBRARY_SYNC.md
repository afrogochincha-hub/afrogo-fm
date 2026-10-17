# Biblioteca personal sincronizada en la nube

Afrogo guarda playlists, favoritos/canciones guardadas e historial en el gateway, asociado al identificador de la cuenta autenticada. Los datos se guardan en `DATA_DIR/user-libraries.json`, dentro del volumen persistente Docker `afrogo-users`. No se debe borrar ese volumen al actualizar el gateway.

## Endpoints

- `GET /api/me/library`: devuelve solo la biblioteca de la cuenta autenticada.
- `PUT /api/me/library`: inicialización/migración o actualización parcial de la biblioteca personal.
- `POST /api/me/playlists`: crea o actualiza una playlist.
- `DELETE /api/me/playlists/:id`: elimina una playlist de la cuenta.
- `POST /api/me/favorites`: marca/desmarca un favorito y sincroniza la lista de guardadas.
- `POST /api/me/saved-tracks`: marca/desmarca una canción guardada.
- `POST /api/me/history`: añade una escucha al historial.

Todas las rutas `/api/me/*` requieren sesión aprobada y HTTPS en producción. El servidor determina la cuenta mediante el token; no acepta un `userId` proporcionado por el cliente.

## Despliegue en el servidor actual

1. Hacer copia de seguridad de `server.js`, `Dockerfile`, `package.json` y `docker-compose.yml`.
2. Copiar los archivos actualizados del gateway sobre la carpeta existente sin reemplazar `.env`.
3. Ejecutar `docker compose config -q`.
4. Ejecutar `docker compose up -d --build afrogo-api`.
5. Probar `curl -fsS http://127.0.0.1:8787/health` y desde el contenedor AzuraCast `curl -fsS http://afrogo-api:8787/health`.

El `docker-compose.yml` de esta suite se refiere a la red externa `azuracast_default` que existe en el servidor del proyecto. Si se usa en otro servidor, adaptar el nombre de esa red. No utilizar `docker compose down -v`; borraría el volumen persistente y los datos.

## Migración desde la app de PC

En el primer inicio de sesión del administrador desde la versión con sincronización, la app intenta migrar sus playlists, favoritos e historial locales antiguos. Los nuevos usuarios no importan las listas globales antiguas de ese equipo y tienen su propio espacio en la nube.
