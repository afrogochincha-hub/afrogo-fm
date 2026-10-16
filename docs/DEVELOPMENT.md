# Desarrollo local

## Herramientas
- Git y una cuenta con acceso al repositorio privado.
- Node.js LTS para la app Electron (la versión exacta se documentará con package.json).
- Android Studio estable con Android SDK y JDK compatible con Gradle.
- Dispositivo Android o emulador; para Android Auto, entorno de pruebas compatible.

## Flujo recomendado
1. Clonar `https://github.com/afrogochincha-hub/afrogo-fm.git`.
2. Crear una rama por función, por ejemplo `feat/android-auto-library`.
3. Realizar cambios pequeños y ejecutar las comprobaciones locales.
4. Crear un Pull Request hacia `main`.
5. Revisar CI y probar antes de publicar una versión.
6. Etiquetar lanzamientos como `v3.16.1` o `v1.0.0-android-beta.1`, sin reutilizar números de versión.

## Antes de hacer commit
- Nunca incluir claves API, `.env`, keystores, contraseñas o archivos privados.
- No añadir la carpeta de biblioteca musical ni archivos de audio.
- Revisar los cambios con `git status` y `git diff --check`.
- No modificar ni reemplazar la clave de firma Android de producción sin un plan de migración.

## Sincronizar cambios
```bash
git pull --rebase
git switch -c feat/descripcion-corta
git add .
git commit -m "feat: describe el cambio"
git push -u origin feat/descripcion-corta
```

Después abre un Pull Request en GitHub. Las ramas y revisiones permiten recuperar cambios y evitar perder funciones anteriores.
