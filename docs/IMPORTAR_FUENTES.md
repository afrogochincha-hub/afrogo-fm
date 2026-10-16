# Importar las fuentes al repositorio

Las fuentes se preparan en el archivo `afrogo_fm_fuentes_para_github_v1.zip` entregado en la conversación. Este ZIP contiene `desktop/` (fuente de PC 3.16.0) y `android/` (prototipo nativo inicial); no contiene música ni API keys.

## Importar con GitHub Desktop

1. Descarga el ZIP y extráelo a una carpeta temporal.
2. En GitHub Desktop usa **File > Clone repository... > URL** y clona `https://github.com/afrogochincha-hub/afrogo-fm`.
3. Abre la ubicación local del repositorio desde GitHub Desktop.
4. Copia las carpetas `desktop/` y `android/` del ZIP a esa ubicación, aceptando reemplazar solo los README de marcador de posición de esas carpetas. Conserva los documentos y el `.gitignore` de la raíz.
5. En GitHub Desktop revisa los cambios. No debe haber música, archivos `.env`, API keys, tokens, contraseñas privadas ni certificados de firma.
6. Escribe el resumen `Importar fuentes de PC y Android`, pulsa **Commit to main** y luego **Push origin**.
7. Confirma que GitHub muestra `desktop/main.js`, `desktop/app/index.html` y `android/app/build.gradle.kts`.

## Validaciones pendientes

- El código de PC debe instalar dependencias y probarse en Windows.
- El prototipo Android debe sincronizarse y compilarse en Android Studio; el ZIP no incluye Gradle Wrapper.
- La URL de stream de AzuraCast es provisional. Verifica la ruta exacta desde **AzuraCast > Public > Listen**.
- La biblioteca de Android Auto solo ofrece emisora en vivo en este prototipo; aún falta integrar el catálogo de canciones individuales.
- La clave API de AzuraCast que se compartió previamente debe revocarse y regenerarse. Guardar la nueva clave solo en el servidor/back-end, nunca en el repositorio ni el APK.
