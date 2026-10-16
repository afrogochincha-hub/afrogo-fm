# Importar fuentes de Afrogo FM

Este paquete contiene las fuentes de escritorio v3.20.0 y el proyecto Android/Android Auto v1.2.0.

## Subir con GitHub Desktop
1. Descarga este ZIP y extráelo.
2. Abre GitHub Desktop y clona `https://github.com/afrogochincha-hub/afrogo-fm`.
3. Copia el contenido de esta carpeta al directorio local del repositorio. Si GitHub ya tiene documentación en `docs/`, conserva o combina esos archivos en lugar de borrar información importante.
4. Revisa los cambios: no deben incluir claves, `.env`, contraseñas, archivos de firma, música ni credenciales.
5. Commit: `release: import PC 3.20 and Android 1.2 sources`. Después pulsa Push origin.

## Archivos clave
- `desktop/app/index.html`, `desktop/main.js`, `desktop/preload.js`, `desktop/package.json`.
- `android/app/src/main/AndroidManifest.xml`, `android/app/build.gradle.kts`, código Kotlin bajo `android/app/src/main/java/com/afrogo/fm/`.
- `.github/workflows/build.yml` genera artefactos de prueba en cada push.

## Autenticación y acceso
No hay clave fija de administrador. La primera cuenta de una instalación local limpia se configura como administradora local. Sin embargo, usuarios y permisos siguen siendo locales: la API central todavía debe desarrollarse para compartir permisos entre dispositivos y asegurar el rol en el servidor. No publiques como sistema multiusuario hasta terminar esa parte.
