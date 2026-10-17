# Sesión persistente de Afrogo FM

## Qué cambia
- Cerrar y volver a abrir la app no cierra la sesión.
- El botón **Cerrar sesión** elimina las credenciales guardadas en ese dispositivo.
- PC guarda la sesión cifrada mediante Electron safeStorage.
- Android guarda el acceso en preferencias privadas de la app y lo renueva al abrirse; Android Auto utiliza esa misma sesión.
- El gateway v1.3 devuelve tokens de acceso de 7 días y refresh tokens renovables de 365 días. La renovación al abrir la app extiende la sesión mientras el usuario siga abriendo Afrogo y su cuenta permanezca activa.
- Un fallo temporal de red no elimina la sesión. Un 401/403 del refresh indica que la sesión o el acceso ya no es válido.

## Orden de actualización
1. Actualiza el gateway de Oracle Cloud a v1.3 con `afrogo_gateway_sesion_persistente_v1_3.zip`.
2. En tu repositorio de GitHub, copia las carpetas `desktop/`, `android/`, `backend/` y la documentación del paquete de actualización. No copies ningún `.env` real ni carpetas de datos.
3. Haz Commit y Push.
4. Espera a que GitHub Actions compile `windows-desktop`, `android-debug` y `backend-check`.
5. Instala el nuevo EXE y APK. La primera vez después de cambiar el gateway, inicia sesión una vez para recibir el refresh token.
6. Cierra la app normalmente y vuelve a abrirla: debe entrar directamente. Usa **Cerrar sesión** para terminar la sesión de ese dispositivo.

## Actualizar gateway sin perder datos
- Mantén el `.env` actual.
- Conserva el volumen Docker `afrogo-users` que contiene usuarios y bibliotecas.
- No uses `docker compose down -v`.
- Reemplaza los archivos del gateway con el ZIP, ejecuta `docker compose config -q` y luego `docker compose up -d --build afrogo-api`.
- Comprueba `curl -fsS http://127.0.0.1:8787/health`, que debe mostrar la versión `1.3.0`.

## Límites
El refresh token tiene una validez máxima de un año desde la renovación más reciente. Si la cuenta se revoca, los datos de la app se borran, el token se invalida o no se inicia sesión durante un periodo superior a ese plazo, el usuario tendrá que autenticarse de nuevo. Para dispositivos compartidos, siempre se debe pulsar **Cerrar sesión** al terminar.
