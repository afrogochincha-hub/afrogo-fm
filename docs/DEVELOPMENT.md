# Desarrollo

## PC
- En Windows, instala Node.js LTS.
- `cd desktop`, `npm install`, `npm start` para ejecución de desarrollo.
- `npm run dist` genera el ejecutable portable en `desktop/dist/`.

## Android/Android Auto
- Instala Android Studio y JDK 17.
- Abre `android/` en Android Studio; sincroniza Gradle.
- Verifica `AzuraConfig.kt`, compila `Build > Build APK(s)` y prueba primero en teléfono.
- La misma aplicación Android provee la sesión multimedia que Android Auto descubre. No se publica una app independiente para la pantalla del coche.

## Git
Usa ramas para cambios y commits breves. No añadas `.env`, llaves de firma, claves API, música ni datos locales. Revisa siempre `git diff --check` antes de subir.
