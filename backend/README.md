# Backend central de Afrogo — 1.0.1

Este servicio conecta la app PC y Android/Android Auto con el catálogo de AzuraCast sin incluir la API Key de AzuraCast dentro de las apps.

## Funciones implementadas
- Registro de usuarios con estado pendiente, inicio de sesión y comprobación de sesión.
- Aprobación, rechazo y revocación de usuarios por administradores aprobados.
- Consulta del catálogo mediante `GET /api/station/{station_id}/files`.
- Proxy de reproducción y portadas mediante URL firmadas temporales.
- Subidas administradas hacia AzuraCast desactivadas por defecto.
- La API Key de AzuraCast se envía al upstream mediante la cabecera `X-API-Key`, como se comprobó en la documentación de tu instancia.

## Configuración
1. Copia `.env.example` como `.env`.
2. Coloca la URL raíz del AzuraCast, el ID de estación `1`, una API Key nueva, `JWT_SECRET` y las credenciales del primer administrador.
3. Configura una URL HTTPS pública para Afrogo y mantenlo detrás de un proxy seguro.
4. Inicia con `docker compose up -d --build`.
5. Verifica `GET /health` y después inicia sesión y prueba `GET /api/catalog`.

No compartas `.env` ni lo subas a GitHub. La API Key que aparece en una captura compartida debe rotarse antes del despliegue.
