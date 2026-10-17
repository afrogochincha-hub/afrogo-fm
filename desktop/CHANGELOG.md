# Cambios de Afrogo FM

## 3.27.0
- Barra de búsqueda fijada arriba mientras se desplaza la biblioteca.
- Resultados de búsqueda visuales con portada más grande, artista, álbum, género y acciones.
- Al seleccionar una canción desde los resultados, la búsqueda permanece abierta y la consulta no se borra.
- Filas de canciones con portadas más grandes, badges de tipo y estado visual de reproducción.

## 3.26.1
- Reemplazados los iconos anteriores por el icono oficial proporcionado por el usuario: fondo degradado morado y marca Afrogo blanca.
- Regenerado `afrogo-icon.ico` con múltiples tamaños para ventana, barra de título, ejecutable portable y barra de tareas de Windows.
- Conservada la configuración `appId` y el resto de funciones de la versión 3.26.0.

## 3.26.0
- Actualizado el icono de Windows para la barra de título y la barra de tareas con la identidad visual de Afrogo FM.
- El paquete portable de Windows incluye los recursos PNG e ICO y actualiza el icono incrustado del ejecutable.
- Añadido un identificador de aplicación de Windows estable para mejorar la agrupación del icono en la barra de tareas.

# Cambios recientes

## 3.25.0
- Clasificación automática de géneros para canciones de AzuraCast usando tags disponibles, título, álbum, ruta y reglas de artistas conocidos.
- Categorías de acceso rápido agrupan alias compatibles (por ejemplo Reguetón/Trap en Urbano; Chicha en Cumbia; EDM en Electrónica).
- Las filas de categoría muestran cuántas canciones disponibles detectó Afrogo.
- El gateway devuelve `genreSource` y `genreConfidence` para distinguir metadatos del servidor de inferencias.
- Añadidas pruebas automatizadas para los géneros y sus alias.

## 3.24.0
- La sección Álbumes calcula una huella visual perceptual de las portadas para reunir canciones que usan la misma imagen aunque el título del álbum o el artista estén escritos de forma distinta.
- La agrupación mantiene artista + álbum como base cuando no se puede leer una portada, por ejemplo por restricciones CORS o ausencia de imagen.
- La portada dominante se usa para la tarjeta y el detalle; las pistas se ordenan por disco/número de pista cuando existe esa información.

## 3.23.1
- Fixed packaged Windows app API configuration: main process reads app-config.json and passes non-secret URL to sandboxed preload.
- Removed file-system access from the preload so the HTTPS gateway URL is available in packaged builds.

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
