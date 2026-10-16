## 3.23.0
- Configurada por defecto la API HTTPS pública de Afrogo para iniciar sesión y consultar el catálogo central AzuraCast.
- La reproducción y las portadas de canciones remotas utilizan enlaces multimedia del gateway.
- Las preferencias personales y playlists siguen siendo locales en esta versión.

# Historial de cambios

## 3.21.0
- Nueva sección Álbumes, generada automáticamente a partir de artista y álbum de los metadatos de cada canción.
- Álbumes agrupados por artista y título, con portada, número de canciones, duración y página de detalle.
- Reproducción de álbum en cola siguiendo el orden de las pistas cuando los metadatos incluyen número de pista.
- Al importar canciones sin etiqueta de álbum, Afrogo también intenta recuperar el nombre del álbum mediante la búsqueda de metadatos.
- El panel Álbumes incluye una acción administrativa para volver a analizar canciones ya importadas que todavía no tengan álbum identificado.


- Eliminado el bloque de demostración y el botón de acceso de administrador de prueba.
- Retiradas del código las credenciales predeterminadas conocidas.
- El primer usuario creado en una instalación local obtiene el rol de administrador con su propia contraseña; las cuentas posteriores quedan pendientes de aprobación.
- Se han retirado las etiquetas visibles de modo demo y actualizado los mensajes del catálogo.
- Se conserva la conexión configurada a la radio Afrogo FM y las funciones de biblioteca, playlists y categorías.

## Pendiente antes de producción multiusuario
La autenticación y las aprobaciones aún se guardan localmente. Se debe implementar y probar la API central de Afrogo con validación de roles en servidor antes de desplegar una experiencia de cuentas compartidas entre equipos.
