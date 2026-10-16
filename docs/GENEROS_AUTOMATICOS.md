# Clasificación automática de géneros

Afrogo asigna un género a cada pista del catálogo central en este orden:
1. Usa el género o los tags que entrega AzuraCast, normalizados a nombres consistentes.
2. Si no hay un tag útil, busca indicadores explícitos en el título, álbum o ruta del archivo.
3. Si aún no hay información, usa reglas conservadoras para artistas conocidos.
4. Si no hay señales suficientes, deja `Sin categoría` en vez de inventar una clasificación.

Las categorías de acceso rápido agrupan alias relacionados; por ejemplo, la categoría Urbano incluye Reguetón, Trap, Hip-Hop, Rap y Dembow; Cumbia incluye Chicha y Tecnocumbia; Electrónica incluye EDM, House, Techno y Trance.

Es una clasificación por metadatos y reglas, no un análisis acústico/huella digital de la señal de audio. Para pistas desconocidas sin tags ni artista identificable, será necesario enriquecer metadatos o añadir un servicio de reconocimiento musical.
