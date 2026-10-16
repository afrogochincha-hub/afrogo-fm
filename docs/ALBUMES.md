# Álbumes en Afrogo FM

El catálogo de AzuraCast se normaliza por canción y PC deriva los álbumes a partir de los metadatos remotos:

- Agrupa por artista y título del álbum.
- Usa la portada disponible del álbum y muestra el total de canciones.
- Ordena por número de disco y pista cuando los datos existen; si no, ordena por título.
- Las canciones sin álbum permanecen como pistas sueltas para no crear agrupaciones falsas.
- Android Auto navega por álbum y luego por las canciones correspondientes.

Si un archivo no tiene artista, álbum o género en AzuraCast, el gateway no inventa esa información. Corrige los metadatos en el Media Manager/etiquetas y actualiza el catálogo.
