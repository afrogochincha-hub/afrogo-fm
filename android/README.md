# Afrogo FM para Android — 1.9.0

Proyecto fuente Android nativo (Kotlin + Media3). El mismo APK incluye el servicio multimedia que Android Auto utiliza; no se debe distribuir como dos aplicaciones independientes.

## Catálogo central de AzuraCast

La app inicia sesión o solicita acceso en el gateway HTTPS de Afrogo y lee `GET /api/catalog`. El servicio multimedia también usa ese catálogo para navegación por álbumes/canciones en Android Auto. La API Key de AzuraCast está solo en el backend, no en el APK.

Configura el gateway al compilar:

```bash
gradle --no-daemon -p . -PafrogoApiBaseUrl=https://afrogochincha.duckdns.org/afrogo-api assembleDebug
```

Se requiere el Android SDK 35, JDK 17 y una instalación Gradle compatible con el Android Gradle Plugin del proyecto. Este ZIP es código fuente, no APK ya compilado. Antes de publicar, configura HTTPS, valida la ID de estación y prueba reproducción en un teléfono real.

La aplicación usa el mismo catálogo central de AzuraCast. No configures la API Key de AzuraCast en Android.


## Corrección de compilación 1.4.1
- `ImmutableList` usa el paquete de Guava que requiere Media3.
- `MediaLibrarySession` usa la clase anidada de `MediaLibraryService`.
- `onSetMediaItems` incluye `ControllerInfo`, de acuerdo con la firma Media3.
- La interfaz de reproducción lee los metadatos del elemento actual.
