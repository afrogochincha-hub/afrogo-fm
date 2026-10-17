# Barra de búsqueda y biblioteca visual

## PC
- `.topbar` usa `position: sticky` dentro del contenedor desplazable principal.
- Los resultados muestran portada, título, artista, tipo de contenido, álbum/género y botones de reproducción/adición a playlist.
- Al reproducir desde un resultado se mantiene el valor de búsqueda, el foco del campo y el desplegable. El tema reproducido queda resaltado.
- La vista de biblioteca usa portadas más grandes, hover de fila y etiquetas de álbum/canción.

## Android
- El campo de búsqueda se encuentra fuera del `ScrollView`, así permanece anclado mientras se desplaza la biblioteca.
- Filtra localmente el catálogo sincronizado por título, artista, álbum y género.
- Las filas muestran portada descargada en segundo plano, artista, álbum/género, etiqueta del tipo y botón de reproducción. La consulta permanece al reproducir.

## Android Auto
- Android Auto no permite reemplazar su propio diseño de búsqueda por una vista de la app. El servicio Media3 ofrece `onSearch` y `onGetSearchResult` para que el selector/search UI de Android Auto pueda buscar el catálogo por título, artista, álbum y género.

## Publicación
Copia las carpetas `desktop/` y `android/` de este paquete sobre las carpetas homónimas del repositorio clonado, realiza `Commit to main` y `Push origin`. Después revisa `Actions` hasta que `windows-desktop` y `android-debug` finalicen correctamente.
