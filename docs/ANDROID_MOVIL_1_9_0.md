# Android móvil 1.9.0

## Cambios de interfaz

- Inicio de sesión con identidad visual AfroGo y opción de radio en vivo sin iniciar sesión.
- Navegación inferior: Inicio, Buscar, Biblioteca, AfroGo+ y Crear.
- Inicio con carrusel de canciones y tarjeta para escuchar la emisora.
- Buscar con accesos rápidos y resultados de la biblioteca central AzuraCast.
- Biblioteca con filtros de canciones, favoritos y playlists del usuario.
- Mini reproductor fijo y pantalla ampliada de reproducción.
- Se conserva el servicio Media3 existente para Android Auto.

## Cambios técnicos

- `versionName` 1.9.0 y `versionCode` 12.
- Se mantiene el gateway HTTPS configurable y la renovación de sesión.
- No se han añadido nuevas API keys ni credenciales a la app.

## Verificación necesaria

El código se sometió a comprobaciones estructurales y las cinco pruebas del backend pasan. El APK todavía debe compilarse en GitHub Actions. Una vez compilado, probar inicio de sesión, radio, catálogo, favoritos, playlists, reproducción y Android Auto en un dispositivo real.
