# Integración segura con AzuraCast

## Radio oficial configurada
- Página pública: `http://129.153.179.138/public/afrogo-fm`
- Stream configurado según el patrón estándar de AzuraCast: `http://129.153.179.138/listen/afrogo-fm/radio.mp3`
- Endpoint público de metadatos: `http://129.153.179.138/api/nowplaying/afrogo-fm`

La URL de stream está configurada en la aplicación de PC v3.17.0 y en el prototipo Android Auto v1.1. Esta URL sigue el formato habitual de AzuraCast para el station URL stub y el mount point MP3 predeterminado, pero debe confirmarse en **AzuraCast > Public > Listen** si se configuró un mount point personalizado. No se pudo verificar la conexión real al servidor desde el entorno de desarrollo.

## Enfoque de conexión
1. Para escuchar la emisora, usar la URL de stream directa que proporciona AzuraCast.
2. Para mostrar título y artista de lo que suena, consultar la API pública de metadatos cuando esté disponible. La aplicación maneja el fallo de metadatos sin depender de ellos para reproducir el stream.
3. Para explorar archivos individuales del catálogo o realizar operaciones autenticadas, validar qué API soporta la versión instalada de AzuraCast y qué permisos tiene la clave.
4. Si el acceso necesita credenciales, colocar una API propia entre clientes y AzuraCast. La API propia autentica al usuario de Afrogo y expone solo operaciones permitidas.

## Seguridad crítica
- No guardar la API Key de AzuraCast en Kotlin, JavaScript, archivos de configuración versionados, GitHub Actions ni APK.
- La API key que se compartió anteriormente en una captura debe revocarse y regenerarse.
- Guardar la nueva clave como secreto del servidor. Si el backend no está desplegado aún, no implementar acciones que requieran la clave desde la aplicación cliente.
- El servidor de radio usa actualmente HTTP. Para una distribución pública, se recomienda configurar HTTPS con certificado válido y actualizar las URLs.
- No enviar credenciales por HTTP.
- Aplicar privilegio mínimo, límites de solicitudes, validación de entradas y registros sin secretos.

## Biblioteca central
La intención es que audio y metadatos vivan en el servidor, no en GitHub ni duplicados entre apps. Antes de diseñar la navegación de archivos, comprobar si la instalación usa biblioteca local de AzuraCast, playlists de la estación, AutoDJ o una combinación. No asumir que la URL de la página pública puede listar todos los archivos.
