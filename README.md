# Afrogo FM

Repositorio privado para organizar el desarrollo de Afrogo FM para PC y Android, incluida la compatibilidad con Android Auto y la conexión con AzuraCast.

## Estado actual

Este repositorio comienza como la base de organización del proyecto. Los directorios de plataforma se irán completando con los proyectos fuente existentes; no representan todavía una aplicación Android lista para instalar ni una integración de servidor ya validada.

## Estructura

- `desktop/`: aplicación para Windows basada en Electron.
- `android/`: aplicación nativa Android con Kotlin y Media3; punto de integración de Android Auto.
- `backend/`: API propia para autenticación, permisos y acceso seguro a AzuraCast.
- `docs/`: decisiones de arquitectura, desarrollo, publicación y despliegue.
- `.github/workflows/`: automatización de comprobaciones y compilaciones.

## Requisitos importantes

- No guardar claves API, contraseñas, tokens, certificados de firma ni archivos `.env` con secretos en el repositorio.
- No subir música con copyright ni archivos grandes de la biblioteca. La música debe permanecer en AzuraCast o en el almacenamiento previsto.
- Las claves de AzuraCast deben revocarse y rotarse si alguna vez se muestran o comparten.
- Android Auto requiere una integración nativa compatible con las reglas multimedia para vehículos.
- Mantener el mismo applicationId y proteger la clave de firma de lanzamiento es necesario para actualizar una app Android existente sin perder continuidad.
- La distribución desde GitHub no actualiza automáticamente las aplicaciones instaladas. Para distribución general se recomienda Google Play; GitHub Actions puede generar las compilaciones.

## Inicio

Consulta [docs/ROADMAP.md](docs/ROADMAP.md), [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) y [docs/AZURACAST.md](docs/AZURACAST.md).

## Versionado

Usaremos Git para registrar cambios incrementales y etiquetas como `vX.Y.Z` para versiones de lanzamiento. No reemplazar el historial por archivos ZIP sucesivos: se deben incorporar los fuentes y los cambios en commits revisables.
