# Conectar PC, Android y Android Auto a la biblioteca de AzuraCast

## Arquitectura

```text
PC / Android / Android Auto
          │ HTTPS + sesión del usuario
          ▼
   Gateway de Afrogo (este backend)
          │ API Key solo en el servidor
          ▼
      AzuraCast Media API
```

El gateway consulta `GET /api/station/{station_id}/files`, normaliza artista, título, álbum, género, pista y portada, y publica enlaces multimedia firmados para que los clientes reproduzcan el audio sin conocer la API Key. La API de AzuraCast usa autenticación para endpoints de medios y la clave hereda permisos de la cuenta que la creó. Consulta la documentación oficial antes de ajustar permisos o rutas: https://www.azuracast.com/docs/developers/apis/ y https://www.azuracast.com/docs/user-guide/mount-points/.

## 1. Antes de instalar

1. En AzuraCast, revoca la API Key que se mostró en una captura anterior y crea una nueva.
2. Localiza el ID numérico de la estación Afrogo FM en la URL de administración de la estación (por ejemplo, la parte `/station/1`; no asumas que siempre será `1`).
3. Confirma que la nueva API Key puede leer los medios de esa estación. No le des permisos de escritura que no necesite.
4. Actualiza AzuraCast a la última versión parcheada disponible antes de habilitar operaciones de Media Manager. Hay avisos de seguridad publicados en 2026 sobre permisos de endpoints de archivos y el endpoint Flow de subida en versiones antiguas; revisa las versiones afectadas y corregidas en las fuentes oficiales.
5. Configura una URL HTTPS pública para el gateway (dominio/subdominio con certificado válido). El servidor de radio proporcionado originalmente está en HTTP; no envíes contraseñas ni tokens de sesión por esa URL.
6. Ejecuta el gateway en el mismo servidor que AzuraCast o en una red privada confiable. La configuración de Compose utiliza `host.docker.internal` para que el backend llegue a un servicio web expuesto en el host; si tu despliegue de AzuraCast usa otra red/puerto, cambia `AZURACAST_BASE_URL` por el nombre interno/URL de esa red. Evita mandar la API Key por Internet sin cifrar.

## 2. Configurar variables en el servidor

Dentro de `backend/`:

```bash
cp .env.example .env
openssl rand -hex 32
nano .env
```

Configura al menos:

- `PUBLIC_API_BASE_URL`: URL HTTPS pública completa del gateway (`https://afrogochincha.duckdns.org/afrogo-api`, sin barra final).
- `AZURACAST_BASE_URL`: raíz segura/interna de tu instalación AzuraCast, no añadas `/public/afrogo-fm`.
- `AZURACAST_STATION_ID`: ID numérico real.
- `AZURACAST_API_KEY`: la nueva API Key. No la pegues en el chat ni en GitHub.
- `JWT_SECRET`: cadena aleatoria larga generada con `openssl rand -hex 32`.
- `BOOTSTRAP_ADMIN_EMAIL` y `BOOTSTRAP_ADMIN_PASSWORD`: correo y contraseña segura (mínimo 12 caracteres) que crearán al primer administrador en la base de usuarios del gateway.
- `AZURACAST_UPLOAD_ENABLED=false` por defecto. Mantén las subidas desactivadas hasta actualizar AzuraCast a una versión corregida, verificar la API de tu instalación y comprobar que los permisos están limitados correctamente. Solo entonces activa `true`.

El primer administrador del gateway se crea una sola vez cuando se inicializa el volumen de usuarios. Si ya existe el usuario, cambiar estas variables no le cambia la contraseña automáticamente.

## 3. Iniciar el gateway

Usa el proxy HTTPS que ya tenga tu servidor; no levantes un segundo proxy en los puertos públicos 80/443 si AzuraCast ya los está ocupando. `Caddyfile.example` muestra el destino local que debe recibir las peticiones HTTPS, si Caddy es el proxy elegido.

```bash
docker compose up -d --build
docker compose logs -f afrogo-api
curl -s https://afrogochincha.duckdns.org/afrogo-api/health
```

`/health` debe devolver `ok: true` y `azuracastConfigured: true`. Si no, revisa las variables y los logs. La ruta de salud no confirma todavía que la clave pueda leer archivos: entra a la app con el administrador y prueba el catálogo.

## 4. Configurar la app de PC

Edita `desktop/app-config.json`:

```json
{ "apiBaseUrl": "https://afrogochincha.duckdns.org/afrogo-api" }
```

Abre `desktop/INICIAR_PREVIEW.bat` (requiere que inicie Electron) o compila con `CREAR_EXE_WINDOWS.bat`. Las cuentas se validan en el gateway. Los registros nuevos quedan pendientes; el administrador los aprueba desde **Administrar → Solicitudes de acceso**. Las subidas solo funcionarán después de habilitar explícitamente la opción del servidor `AZURACAST_UPLOAD_ENABLED=true`, tras actualizar y validar AzuraCast; por defecto están desactivadas por seguridad.

## 5. Configurar Android y Android Auto

Android Auto usa el mismo APK Android: no se genera una aplicación separada para el coche. Al compilar define la URL HTTPS:

```bash
gradle --no-daemon -p android -PafrogoApiBaseUrl=https://afrogochincha.duckdns.org/afrogo-api assembleDebug
```

El teléfono permite iniciar sesión/solicitar acceso y muestra el catálogo. Android Auto publica **Radio en vivo** y **Biblioteca musical**, con álbumes agrupados por artista y álbum. Haz pruebas reales en Android y Android Auto antes de distribuir la app.

## 6. Automatizar compilaciones en GitHub

La URL `https://afrogochincha.duckdns.org/afrogo-api` ya viene configurada por defecto en el código y en GitHub Actions. Si más adelante cambia el dominio, puedes definir la variable de repositorio `AFROGO_API_BASE_URL` con la nueva URL. No la marques como secreto (solo es una dirección), pero **nunca pongas ahí la API Key de AzuraCast**. Los APK de depuración generados por CI son para pruebas; para Google Play hace falta preparar firma de lanzamiento protegida y la ficha de la aplicación.

## Endpoints que expone el gateway

- `GET /health`: estado no sensible del servicio.
- `POST /api/auth/register`: solicita una cuenta (pendiente por defecto).
- `POST /api/auth/login`, `GET /api/auth/me`: sesión central.
- `GET /api/catalog`: catálogo de AzuraCast para usuarios aprobados.
- `POST /api/admin/users/{id}/approve|reject|revoke`: gestión administrativa.
- `POST /api/admin/tracks/upload`: sube archivos al Media Manager de AzuraCast; requiere rol de administrador.
- `GET /api/tracks/{id}/play?ticket=...` y `/art?ticket=...`: proxy multimedia con ticket firmado de corta duración.

## Limitaciones conocidas

- El endpoint de subida multipart de AzuraCast puede variar según la versión. Las subidas están desactivadas por defecto; no actives `AZURACAST_UPLOAD_ENABLED` hasta actualizar a una versión corregida y verificar el endpoint y sus permisos. El gateway informa errores y no almacena los archivos de audio.
- En abril de 2026 AzuraCast publicó avisos relacionados con permisos de endpoints de archivos en versiones antiguas (afectadas hasta 0.23.5; corregidas desde 0.23.6). Antes de conectar el gateway, verifica que tu instancia tenga una versión corregida. Consulta https://github.com/AzuraCast/AzuraCast/security/advisories/GHSA-qff7-q5fm-8p76 y https://github.com/AzuraCast/AzuraCast/security/advisories/GHSA-vp2f-cqqp-478j.
- Si los metadatos de la biblioteca de AzuraCast no tienen álbum/artista/género, la app no inventa esos datos; se deben corregir en AzuraCast o añadir una mejora de metadatos en el backend.
- Una clave API previamente expuesta debe rotarse. Nunca se debe incluir en APK, Electron, repositorio, artefactos de GitHub Actions ni archivos `app-config.json`.


## Confirmación de acceso al catálogo
La consulta de Swagger a `GET /api/station/1/files` devolvió HTTP `200 Success` y un listado de archivos de la estación 1. El conector del backend usa la cabecera `X-API-Key` que indica tu documentación de Swagger. La respuesta real de algunos archivos solo contiene `path`, `length`, `art` y campos técnicos, por lo que el gateway extrae título del campo `text` cuando existe y, como respaldo, del nombre de archivo. Artista/álbum/género solo pueden completarse si AzuraCast los entrega o si hay una fuente fiable de metadatos.

## Estado de conexión comprobado (2026-10-16)
- `https://afrogochincha.duckdns.org/afrogo-api/health` respondió HTTP 200 desde un navegador externo.
- Inicio de sesión admin y `GET /api/catalog` se probaron desde Windows; la respuesta indicó fuente AzuraCast y 267 canciones.
- PC y Android ya tienen esta URL configurada por defecto. La reproducción completa de una pista y la lectura de portada deben verificarse en los clientes compilados.
