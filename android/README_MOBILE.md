# Afrogo FM para Android — 1.4.0

Esta app móvil usa la misma biblioteca central de AzuraCast que PC y Android Auto mediante el gateway de Afrogo. El paquete incluye inicio de sesión central, solicitud de acceso, lista de canciones del catálogo y reproducción de canciones mediante enlaces firmados de corta duración.

## Configurar el gateway HTTPS

Antes de compilar, usa la URL pública HTTPS real del backend de Afrogo:

```bash
gradle --no-daemon -p android -PafrogoApiBaseUrl=https://afrogochincha.duckdns.org/afrogo-api assembleDebug
```

No configures HTTP para autenticación ni incrustes API keys de AzuraCast en la app. Si la cuenta está pendiente, el administrador debe aprobarla desde el panel de PC.

El botón de radio en vivo sigue usando la URL del mount point de AzuraCast; confirma ese enlace en AzuraCast > Public > Listen.

La aplicación usa el mismo catálogo central de AzuraCast. No configures la API Key de AzuraCast en Android.


## Versión 1.5.0
- Barra de búsqueda fija fuera del área desplazable de la biblioteca.
- Lista musical con portada grande, título, artista, álbum, género y botón de reproducción.
- El texto de búsqueda permanece después de reproducir una canción; la lista conserva el filtro.

## Versión 1.7.0
- Playlists creadas y editadas en el móvil se guardan en la biblioteca personal del gateway.
- Favoritos e historial de reproducción asociados a la cuenta y sincronizados con PC.
- El servicio multimedia Android Auto puede explorar favoritos y playlists de la cuenta.
