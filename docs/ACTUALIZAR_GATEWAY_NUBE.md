# Actualizar el gateway de Afrogo para sincronización en la nube

Este archivo contiene la versión 1.2.0 del gateway. No incluye `.env`, claves API, contraseñas ni datos de usuarios.

## Importante

- No reemplazar ni editar `.env` durante la actualización.
- No borrar el volumen Docker `afrogo-users`. Allí se guardan las cuentas existentes y se guardará `user-libraries.json`.
- La actualización recrea solo el contenedor `afrogo-api`, no el contenedor de AzuraCast.
- El `docker-compose.yml` conecta el gateway a la red externa `azuracast_default` que existe en el servidor de este proyecto.

## Pasos en Windows

1. Descargar `afrogo_gateway_cloud_library_v1_2.zip` en la carpeta Descargas.
2. Abrir PowerShell y ejecutar (ajustar la ruta de la clave si se guardó en otra carpeta):

```powershell
scp -i "C:\Users\Viniles Flores\Documents\Corel\ssh-key-2026-07-01.key" "$HOME\Downloads\afrogo_gateway_cloud_library_v1_2.zip" ubuntu@129.153.179.138:~/
```

## Pasos en Warp, conectado por SSH a Ubuntu

Ejecutar un comando a la vez:

```bash
cd ~/afrogo-gateway/afrogo_azuracast_gateway_1_0_1
cp server.js server.js.backup-before-cloud-library
cp Dockerfile Dockerfile.backup-before-cloud-library
cp package.json package.json.backup-before-cloud-library
cp docker-compose.yml docker-compose.yml.backup-before-cloud-library
unzip -o ~/afrogo_gateway_cloud_library_v1_2.zip -d .
docker compose config -q
docker compose up -d --build afrogo-api
curl -fsS http://127.0.0.1:8787/health
docker exec azuracast sh -lc 'curl -fsS --max-time 5 http://afrogo-api:8787/health'
curl -fsS --resolve afrogochincha.duckdns.org:443:127.0.0.1 https://afrogochincha.duckdns.org/afrogo-api/health
```

Las tres comprobaciones deben devolver JSON con `"ok":true` y versión `1.2.0`. Si `docker compose config -q` muestra un error, parar y no ejecutar `docker compose up`. No usar `docker compose down -v`.

## Qué se guarda en la nube

Los endpoints `/api/me/library`, `/api/me/playlists`, `/api/me/favorites`, `/api/me/saved-tracks` y `/api/me/history` guardan playlists, favoritos/canciones guardadas e historial bajo el ID de la cuenta autenticada. Los datos se escriben en `/data/user-libraries.json` dentro del volumen persistente del gateway. Las rutas requieren usuario aprobado y HTTPS en producción.
