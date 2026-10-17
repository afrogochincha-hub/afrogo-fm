# Afrogo FM para PC — 3.23.0

Aplicación Electron para Windows. La música del catálogo se consulta desde la biblioteca de AzuraCast a través del gateway HTTPS de Afrogo. Las subidas del administrador se reenvían al Media Manager de AzuraCast, para que la biblioteca sea compartida con dispositivos autorizados.

## Configurar el gateway

Edita `app-config.json` y añade la URL HTTPS pública del gateway que hayas desplegado siguiendo `../docs/DEPLOY_AZURACAST.md`:

```json
{ "apiBaseUrl": "https://afrogochincha.duckdns.org/afrogo-api" }
```

No incluyas la API Key de AzuraCast en este archivo. La clave vive exclusivamente en `backend/.env` del servidor. Sin URL de gateway desplegado, el inicio de sesión y el catálogo central no podrán funcionar.

## Ejecutar en Windows

Instala Node.js 22 o posterior y ejecuta `INICIAR_PREVIEW.bat`. Para crear el ejecutable portable, usa `CREAR_EXE_WINDOWS.bat`; el archivo resultante se crea en `dist/`.

## Importante

La URL del stream y los endpoints de la API se han configurado según el formato común de AzuraCast, pero no han podido validarse contra la instalación real. Comprueba el ID de estación, permisos de API y URL de escucha en AzuraCast antes de publicar.

## API central de Afrogo
La configuración `app-config.json` apunta a `https://afrogochincha.duckdns.org/afrogo-api`. La aplicación utiliza esta API para inicio de sesión, solicitudes de acceso, administración de usuarios y catálogo multimedia de AzuraCast. No se incluye ninguna API Key de AzuraCast en la app.

## Clasificación automática de géneros (3.25.0)

El cliente normaliza el género del catálogo central. Si falta el tag, utiliza señales explícitas del título/álbum/ruta y reglas conservadoras de artistas conocidos. Las categorías de acceso rápido agrupan alias compatibles y muestran el número de canciones detectadas. Lo que no tenga señales suficientes queda como `Sin categoría`; no se presenta una inferencia como una certeza acústica.

## Versión 3.26.0 — Iconos de Windows
La compilación de Windows ahora incluye `afrogo-icon.ico` y `afrogo-icon.png` dentro del paquete, incrusta el ICO en el ejecutable y usa el mismo icono para la ventana de Electron. En Windows, el icono ya fijado en la barra de tareas puede quedar en caché; después de actualizar, desancla el icono antiguo y fija el nuevo ejecutable si fuera necesario.


## Icono oficial 3.26.1
El icono de Windows (`afrogo-icon.png` y `afrogo-icon.ico`) utiliza el archivo oficial morado con marca blanca facilitado para este proyecto. El ICO incluye tamaños de 16 a 256 px y se referencia desde `main.js` y `package.json`. Si Windows muestra el icono anterior, desancla el acceso directo viejo y fija el ejecutable recién compilado.
