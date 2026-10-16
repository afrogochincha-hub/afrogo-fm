# Gateway AzuraCast 1.0.1

- Cambia la autenticación upstream a `X-API-Key`, confirmada por Swagger en la instancia del usuario.
- Conserva la lectura de `GET /api/station/{id}/files`.
- Lee el identificador de arte completo, incluida la extensión del archivo.
- Extrae título/artista de `text` cuando AzuraCast lo proporciona; si faltan, usa metadatos válidos o el nombre de archivo sin el prefijo de número de pista.
- Verificado con un servidor mock: login, petición de catálogo con `X-API-Key`, enlace temporal de reproducción y enlace temporal de portada.
- No se ha comprobado streaming real directamente contra el servidor del usuario.
