# Integración segura con AzuraCast

## Servidor configurado por el proyecto
URL pública facilitada: `http://129.153.179.138/public/afrogo-fm`

Esta URL es la página pública de la estación, no necesariamente la URL directa del stream ni la base de la API. Antes de activar reproducción, verificar en AzuraCast la URL exacta de Listen/Stream y los endpoints públicos de metadatos.

## Enfoque de conexión
1. Para escuchar la emisora, usar la URL de stream directa que proporciona AzuraCast.
2. Para mostrar título, artista y portada de lo que suena, consultar la API pública de metadatos de la estación cuando esté disponible.
3. Para explorar archivos individuales del catálogo o realizar operaciones autenticadas, validar qué API soporta la versión instalada de AzuraCast y qué permisos tiene la clave.
4. Si el acceso necesita credenciales, colocar una API propia entre clientes y AzuraCast. La API propia autentica al usuario de Afrogo y expone solo operaciones permitidas.

## Seguridad crítica
- No guardar la API Key de AzuraCast en Kotlin, JavaScript, archivos de configuración versionados, GitHub Actions ni APK.
- La API key que se compartió anteriormente en una captura debe revocarse y regenerarse.
- Guardar la nueva clave como secreto del servidor. Si el backend no está desplegado aún, no implementar acciones que requieran la clave desde la aplicación cliente.
- Usar HTTPS con certificado válido antes de enviar credenciales o distribuir la app públicamente. Evitar enviar claves por HTTP.
- Aplicar privilegio mínimo, límites de solicitudes, validación de entradas y registros sin secretos.

## Biblioteca central
La intención es que audio y metadatos vivan en el servidor, no en GitHub ni duplicados entre apps. Antes de diseñar la navegación de archivos, comprobar si la instalación usa biblioteca local de AzuraCast, playlists de la estación, AutoDJ o una combinación. No asumir que la URL de la página pública puede listar todos los archivos.
