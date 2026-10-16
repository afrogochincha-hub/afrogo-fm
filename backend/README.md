# Backend de Afrogo

Módulo reservado para una API propia, necesaria si Afrogo requiere permisos de usuario, biblioteca compartida y acceso autenticado a AzuraCast.

## Responsabilidades
- Autenticar usuarios y comprobar su aprobación.
- Aplicar permisos de administrador/usuario en el servidor.
- Guardar la API Key de AzuraCast en secretos del servidor.
- Exponer solo endpoints necesarios para catálogo, metadatos y operaciones autorizadas.
- Configurar HTTPS, límites, validación, auditoría y copias de seguridad.

No se ha implementado ni desplegado aún un backend. No colocar credenciales en el cliente PC ni Android.
