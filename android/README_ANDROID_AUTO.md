# Afrogo FM para Android Auto — 1.4.0

Android Auto usa la misma aplicación Android instalada en el teléfono. El servicio `AfrogoMediaService` (Media3 `MediaLibraryService`) publica las ramas `Radio en vivo` y `Biblioteca musical`; los álbumes están agrupados por artista + nombre de álbum y las canciones sueltas también se pueden reproducir.

La biblioteca requiere una sesión Afrogo aprobada. La URL HTTPS del gateway ya viene configurada por defecto: https://afrogochincha.duckdns.org/afrogo-api. Compila el mismo APK:

```bash
gradle --no-daemon -p android -PafrogoApiBaseUrl=https://afrogochincha.duckdns.org/afrogo-api assembleDebug
```

Prueba el APK en teléfono, Android Auto Desktop Head Unit y un coche/dispositivo real. La ruta de escucha de radio es provisional hasta confirmar AzuraCast > Public > Listen. Este ZIP es código fuente, no un APK listo para instalar.

La aplicación usa el mismo catálogo central de AzuraCast. No configures la API Key de AzuraCast en Android.


## Búsqueda en Android Auto
La biblioteca implementa búsqueda de títulos, artistas, álbumes y géneros a través de las callbacks de MediaLibraryService, para que Android Auto pueda usar su interfaz de búsqueda/voz compatible. La interfaz en pantalla del automóvil la presenta Android Auto, no una vista personalizada de la app, por diseño de seguridad.

## Sincronización de biblioteca personal

Con sesión iniciada, el servicio multimedia consulta la biblioteca personal del gateway para mostrar Favoritos y Mis playlists. Los cambios se guardan usando la cuenta autenticada.
