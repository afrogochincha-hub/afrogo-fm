# Hoja de ruta de Afrogo FM

## Fase 1 — Consolidar las fuentes existentes
- Importar el código fuente real de la última versión de PC (3.16.0) en `desktop/`.
- Importar el proyecto Android inicial en `android/` y revisar sus dependencias.
- Registrar la versión de cada fuente y mantener los ZIP fuera del código de producción, salvo como artefactos de release.
- Revisar que no se incluyan claves, contraseñas, música ni datos personales.

## Fase 2 — PC
- Mantener el comportamiento probado de la app de escritorio.
- Validar carga masiva, detección de duplicados, metadatos/portadas, playlists, eliminación y reproducción completa.
- Automatizar comprobaciones de sintaxis y empaquetado.

## Fase 3 — Android y Android Auto
- Mantener una app nativa Kotlin con Media3, MediaSession y navegación multimedia compatible con Android Auto.
- Asegurar que el audio continúe con la pantalla apagada y que los controles del vehículo funcionen.
- Probar en emulador, Android Auto Desktop Head Unit y dispositivos reales.
- Mantener estable el applicationId y la clave de firma de lanzamiento.

## Fase 4 — AzuraCast y API propia
- Consultar el catálogo y metadatos mediante endpoints públicos soportados.
- Para biblioteca autenticada, crear una API propia con autorización por usuario y operaciones limitadas.
- Guardar API keys de AzuraCast solo en el servidor, nunca en los clientes.
- Configurar HTTPS antes de usar credenciales o distribuir la app públicamente.
- Probar audio, metadatos, portadas, errores de red y permisos.

## Fase 5 — Actualizaciones
- Usar GitHub Actions para compilar, verificar y guardar los APK como artefactos de prueba.
- Para distribución estable, configurar Google Play y conservar protegida la clave de firma.
- Publicar changelog y etiquetas `vX.Y.Z`.
- Las actualizaciones del catálogo en AzuraCast no deben requerir actualizar la app. Los cambios de código sí requieren una nueva compilación.

## Criterios de aceptación
- No hay secretos en el repositorio ni en los APK.
- Las compilaciones CI son reproducibles y terminan correctamente.
- La app muestra únicamente la música autorizada y trata los fallos de conexión de forma segura.
- Las actualizaciones mantienen el mismo identificador y están firmadas con la misma clave de lanzamiento.
