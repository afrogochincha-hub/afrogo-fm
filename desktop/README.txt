AFROGO FM 3.23.0 — VERSIÓN DE ESCRITORIO

INICIO EN WINDOWS
1. Extrae toda la carpeta del ZIP.
2. Edita app\app-config.json y coloca la URL HTTPS del gateway central de Afrogo.
3. Ejecuta INICIAR_PREVIEW.bat para abrir la aplicación con Electron.
4. Solicita tu cuenta desde la pantalla de acceso. El administrador central debe aprobarla.

FUNCIONES
- Catálogo musical central consultado desde AzuraCast a través del gateway de Afrogo.
- Artistas, álbumes, portadas, géneros y números de pista cuando esos metadatos están disponibles en AzuraCast.
- Carga masiva administrada por el gateway hacia el Media Manager de AzuraCast.
- Evita mezclar la biblioteca local antigua con la biblioteca central.
- Radios, incluida la estación Afrogo FM (verificar el mount point real en AzuraCast > Public > Listen).
- Playlists, favoritos e historial de usuario se mantienen como datos locales de la aplicación.

ACCESO Y SEGURIDAD
- Se quitó el acceso de administrador de prueba y la contraseña fija del código.
- Usuarios, contraseñas y aprobaciones se validan en el backend central; los registros nuevos quedan pendientes.
- La app no incluye la API Key de AzuraCast. Guárdala únicamente en backend/.env del servidor.
- Configura un gateway HTTPS antes de enviar credenciales o distribuir la aplicación.
- La API Key expuesta anteriormente debe revocarse y regenerarse.

CREAR EJECUTABLE WINDOWS
1. Instala Node.js LTS en Windows.
2. Ejecuta CREAR_EXE_WINDOWS.bat.
3. El ejecutable portable se genera en dist\.

NOTA
La biblioteca musical compartida proviene de AzuraCast, pero la reproducción del catálogo depende de que se configure y despliegue el gateway, el ID correcto de la estación y una API Key con permisos suficientes. La integración no se ha validado contra el servidor real del usuario.

API central configurada: https://afrogochincha.duckdns.org/afrogo-api
Las cuentas requieren aprobación del servidor; la biblioteca musical se consulta desde AzuraCast.
