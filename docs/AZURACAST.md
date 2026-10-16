# Integración de AzuraCast

## Estación
- Página pública facilitada: `http://129.153.179.138/public/afrogo-fm`
- Stream provisional: `http://129.153.179.138/listen/afrogo-fm/radio.mp3`
- Metadatos de la transmisión: `http://129.153.179.138/api/nowplaying/afrogo-fm`

La página pública no es el catálogo de archivos. El gateway usa la API autenticada de AzuraCast para listar los medios de la estación y reproducir archivos individuales. La documentación oficial confirma que los datos de “Ahora suena” son públicos, mientras que las operaciones sensibles requieren una API Key y que esa clave hereda los permisos de la cuenta que la creó: https://www.azuracast.com/docs/developers/apis/ y https://www.azuracast.com/docs/developers/now-playing-data/.

## Flujo de música
1. El gateway llama a `GET /api/station/{station_id}/files` con `Authorization: Bearer <API_KEY>`.
2. La app consume `GET /api/catalog`, que normaliza título, artista, álbum, género, duración, pista y portada.
3. El audio individual pasa por `GET /api/station/{station_id}/file/{id}/play` desde el gateway. Las URLs entregadas a la app tienen tickets temporales y no contienen la API Key de AzuraCast.
4. La app de PC sube archivos mediante el endpoint del gateway; el gateway los reenvía al Media Manager de AzuraCast.

## Qué debe configurarse en el servidor
- ID numérico real de la estación, `AZURACAST_STATION_ID`.
- API Key recién creada, guardada en `backend/.env` y con permiso para leer medios de esa estación.
- Base URL de AzuraCast accesible desde el servidor/gateway. Si se usa HTTP internamente, mantén el tráfico en una red local de confianza; no envíes la API Key sin cifrar por Internet.
- URL HTTPS pública del gateway (`PUBLIC_API_BASE_URL`).
- La URL de stream debe confirmarse en **AzuraCast → Public → Listen**, pues el mount point puede ser personalizado.

La API Key previamente expuesta debe revocarse. No se guarda ninguna API Key en los repositorios, configuraciones de los clientes, APK ni artefactos de compilación. Sigue la [guía de despliegue](DEPLOY_AZURACAST.md).


## Confirmación de acceso al catálogo
La consulta de Swagger a `GET /api/station/1/files` devolvió HTTP `200 Success` y un listado de archivos de la estación 1. El conector del backend usa la cabecera `X-API-Key` que indica tu documentación de Swagger. La respuesta real de algunos archivos solo contiene `path`, `length`, `art` y campos técnicos, por lo que el gateway extrae título del campo `text` cuando existe y, como respaldo, del nombre de archivo. Artista/álbum/género solo pueden completarse si AzuraCast los entrega o si hay una fuente fiable de metadatos.

## Gateway público validado
- Base API: `https://afrogochincha.duckdns.org/afrogo-api`
- Health: `https://afrogochincha.duckdns.org/afrogo-api/health` (probado desde navegador con HTTP 200).
- Catálogo: `https://afrogochincha.duckdns.org/afrogo-api/api/catalog` (probado desde Windows con sesión de administrador; devolvió 267 canciones).
- Los enlaces `playUrl` y `coverUrl` del catálogo usan esta misma base HTTPS.
- No subir API keys ni contraseñas al repositorio.
