# Afrogo FM — actualización de búsqueda y biblioteca visual

Última revisión de fuentes: **PC 3.27.0** y **Android/Android Auto 1.5.0**.

Esta revisión añade una barra de búsqueda fija en la aplicación de PC y móvil, resultados visuales con portadas más grandes, conservación de la consulta después de reproducir una canción y búsqueda multimedia compatible con el navegador de Android Auto.

Consulta `docs/BUSQUEDA_Y_UI.md` para los detalles y el procedimiento para subir los cambios a GitHub.

# Afrogo FM

Repositorio privado con código fuente de Afrogo FM para Windows, Android y Android Auto, conectado a la biblioteca central de AzuraCast mediante una API propia.

## Módulos
- `desktop/`: aplicación Electron para Windows, versión 3.23.0.
- `android/`: app nativa Android, versión 1.4.0. El mismo APK ofrece el servicio multimedia compatible con Android Auto.
- `backend/`: gateway de autenticación, catálogo, administración de accesos y proxy multimedia de AzuraCast.
- `docs/`: instalación, desarrollo, releases e integración segura de AzuraCast.
- `.github/workflows/build.yml`: comprobación del backend y builds de Windows/Android en GitHub Actions.

## Cómo comparte música
La música permanece en el Media Manager de AzuraCast. PC y Android consultan `GET /api/catalog` desde el gateway. El gateway usa la API Key solo en el servidor, y crea URL firmadas temporales para reproducir canciones y portadas. La subida de música desde PC a AzuraCast está controlada por una opción de seguridad y permanece desactivada hasta actualizar AzuraCast a una versión parcheada y comprobar el endpoint de carga. No se guardan canciones en GitHub.

## Estado y requisitos de instalación
El gateway está desplegado en el servidor del usuario y responde a través de HTTPS. Se comprobó el estado público, y la consulta autenticada a `GET /api/catalog` desde Windows devolvió 267 canciones. El audio completo y las portadas deben probarse en cada cliente. Para mantener la instalación, conserva:

1. Una URL HTTPS real para el gateway.
2. El ID numérico correcto de la estación AzuraCast.
3. Una API Key nueva (la clave mostrada anteriormente debe revocarse y regenerarse).
4. Un administrador inicial definido por variables de entorno en el servidor.
5. La URL de la API ya está incluida por defecto en las fuentes y en GitHub Actions; se puede sobrescribir con la variable `AFROGO_API_BASE_URL`.
6. Mantén las subidas desactivadas hasta verificar la versión de AzuraCast y los permisos de la API de archivos.

El stream HTTPS usa el dominio `afrogochincha.duckdns.org`; si AzuraCast tiene un mount point personalizado, confirma la ruta exacta en **AzuraCast → Public → Listen** antes de distribuir el APK.

## Acceso y seguridad
- La autenticación y aprobación de usuarios se valida en el backend; las cuentas nuevas se registran como pendientes.
- No incrustar la API Key de AzuraCast ni `JWT_SECRET` en la app, APK, GitHub o archivos de configuración de cliente.
- En producción el gateway rechaza autenticación y catálogo si la petición no llega por HTTPS.
- El primer administrador se crea con `BOOTSTRAP_ADMIN_EMAIL` y `BOOTSTRAP_ADMIN_PASSWORD` en el servidor. Nunca hay credenciales predeterminadas en el código fuente.
- Mantén el puerto del backend ligado a localhost y expón el API a través del proxy HTTPS que ya utilice el servidor.

## Compilar

- **PC:** instala Node.js 22+, configura `desktop/app-config.json` con `apiBaseUrl` HTTPS y ejecuta `desktop/CREAR_EXE_WINDOWS.bat` en Windows.
- **Android y Android Auto:** en Android Studio o Gradle, define `-PafrogoApiBaseUrl=https://afrogochincha.duckdns.org/afrogo-api` y compila `assembleDebug`. Un APK incorpora móvil y Android Auto; no son paquetes separados.
- **GitHub Actions:** configura la variable del repositorio `AFROGO_API_BASE_URL` y después revisa `Actions`. Los artefactos de depuración no equivalen a una publicación firmada de Google Play.

Consulta [docs/DEPLOY_AZURACAST.md](docs/DEPLOY_AZURACAST.md), [docs/AZURACAST.md](docs/AZURACAST.md), [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) y [docs/ROADMAP.md](docs/ROADMAP.md).

## Versiones fuente
- PC 3.23.0
- Android + Android Auto 1.4.0
- Gateway Afrogo 1.0.1


### Corrección del conector 1.0.1
- La clave de AzuraCast se transmite mediante `X-API-Key`, según la documentación Swagger real del servidor.
- `GET /api/station/1/files` fue verificado manualmente por el usuario con respuesta 200.
- Los archivos sin metadatos enriquecidos usan el nombre de archivo como título de respaldo; esto no inventa artistas ni álbumes.


## Validación AzuraCast (2026-10-16)
La consulta Swagger `GET /api/station/1/files` fue ejecutada por el usuario y devolvió HTTP 200 con archivos de la biblioteca. El gateway de esta revisión envía `X-API-Key`, conserva títulos/artistas/álbumes proporcionados por AzuraCast y usa el nombre de archivo como respaldo si faltan metadatos. La integración sigue necesitando desplegar el gateway y probar el catálogo/audio contra la instalación real desde las apps.


Corrección Android 1.4.1: arregla las referencias de Media3/Guava y la firma de callbacks de la biblioteca multimedia.

## PC 3.24.0: reconocimiento visual de portadas
La sección Álbumes compara visualmente las portadas y reúne pistas que usan la misma imagen, aunque los textos de artista/álbum tengan pequeñas diferencias. Cuando no puede analizarse una imagen por restricciones de acceso, se conserva el agrupamiento por metadatos.

## Versión 3.28.0
Incluye barra de búsqueda fija y suave; en PC el stream de radio se conserva al cambiar de sección porque el elemento de audio persiste fuera de la vista dinámica. Android usa el buscador fijo y Android Auto usa el servicio multimedia de Media3.

## Biblioteca personal en la nube (v1.2)

Playlists, favoritos/canciones guardadas e historial se almacenan en el gateway y están aislados por cuenta. PC y Android consumen `/api/me/library`; Android Auto explora favoritos y playlists desde el servicio multimedia. Los datos persisten en el volumen Docker del gateway; no borrar `afrogo-users`. Véase `docs/CLOUD_LIBRARY_SYNC.md`.
