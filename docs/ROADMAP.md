# Hoja de ruta Afrogo FM

## Preparado en las fuentes
- PC Electron 3.23.0 con inicio de sesión central, catálogo de AzuraCast, agrupación por álbum y carga masiva hacia el Media Manager a través del gateway.
- Android 1.4.0 con solicitud/login de cuentas, lista de canciones y reproducción.
- El mismo APK Android ofrece Media3 MediaLibraryService para Android Auto, con navegación por radio y biblioteca (álbumes y pistas individuales).
- Gateway de backend 1.0.1: usuarios pendientes/aprobados, roles, listado de medios, tickets temporales y proxy de streaming/portadas.
- CI en GitHub Actions para verificar backend y compilar Windows/Android.

## Pendiente de puesta en marcha
1. Revocar la API Key expuesta, generar una nueva y configurar `.env` en el servidor.
2. Identificar el ID numérico real de la estación AzuraCast.
3. Publicar el gateway detrás del HTTPS del servidor y confirmar las URL de audio y de API.
4. Configurar la variable de GitHub `AFROGO_API_BASE_URL` y `desktop/app-config.json`.
5. Importar/actualizar los fuentes en el repositorio GitHub y ejecutar Actions.
6. Probar upload a AzuraCast, catálogo, audio, portadas y aprobación de usuarios con el servidor real.
7. Preparar firma Android de lanzamiento y distribución Google Play, después de pruebas Android Auto reales.

## Criterios antes de lanzamiento
- No se guardan secretos en las apps ni en GitHub.
- El gateway solo acepta sesión de usuario aprobada para el catálogo; acciones de administración requieren rol admin.
- HTTPS validado y API Key con privilegio mínimo.
- Las canciones subidas desde PC aparecen en el Media Manager y luego en Android/Android Auto al refrescar.
- Pruebas de archivos largos, rangos/seek, metadatos ausentes, rutas inválidas y fallos de red.
