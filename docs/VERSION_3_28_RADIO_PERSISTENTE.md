# Afrogo FM 3.28.0 — radio persistente y búsqueda fija

- La barra superior de búsqueda permanece pegada al borde superior del área de contenido al desplazarse. Incluye un degradado translúcido y desenfoque para suavizar la transición con la página.
- La radio de AzuraCast utiliza un único elemento de audio persistente fuera del contenedor de vistas. Al navegar entre Inicio, Música, Álbumes y otras secciones, se conserva el mismo elemento de audio y el stream no se reinicia.
- Elegir otra canción pausa la radio para evitar que se mezclen dos audios. Cerrar sesión detiene el stream.
- Al entrar a Radio en vivo, el mismo reproductor se vuelve a montar en la pantalla y conserva su estado.
- Android ya mantiene el buscador fuera de la zona desplazable; se suavizó su fondo con un degradado y sombra. Android Auto continúa reproduciendo mediante Media3 MediaLibraryService, independiente de la vista de Activity.

Validación realizada: sintaxis JavaScript y JSON de package.json. Pendiente de confirmar compilación en GitHub Actions y prueba de reproducción real en Windows/Android.
