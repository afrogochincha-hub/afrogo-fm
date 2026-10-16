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
