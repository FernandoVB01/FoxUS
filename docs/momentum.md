# Enganches y perfil

El primer enganche conserva la microacción breve. Al guardar un avance se abre una
cadena ligada a esa actividad. En **Perfil → Seguir el enganche**, la persona elige
la siguiente meta y su tiempo (1–120 minutos). FOX elige una técnica con el historial
del usuario, la hora local y la cercanía de la entrega. Rechaza tiempos que invadan
un evento del calendario sincronizado, incluyendo cinco minutos de margen.

- Cada avance aporta 10 de energía más 5 por eslabón anterior, hasta 35.
- Un momento añade 10 XP y 10 de energía, una sola vez por avance.
- Las categorías son Chispa (1–39), Llama (40–99) y Fuego (100+).
- La energía activa caduca tras 24 horas sin otro avance. Los XP y momentos permanecen.
- Continuar nunca marca automáticamente como completa la actividad original.

Los momentos admiten texto, foto JPEG/PNG, audio (M4A, MP3, WAV, OGG) y video MP4.
Los archivos de audio/video están limitados a 12 MB; se almacenan en PostgreSQL
en esta versión de demostración y se descargan por separado. La foto y el texto
se exportan a PNG 1080×1920 con el formato FOXUS. Android abre su selector para que
la persona elija la aplicación de destino; no hay publicación automática ni
integración directa con las cuentas de Instagram o TikTok.

El perfil usa la identidad de demostración existente del repositorio y requiere
el token configurado. No es todavía una red social con cuentas independientes,
buscador de perfiles o feed público. Las estadísticas están separadas por user_id;
el inicio de sesión multiusuario sigue siendo una ampliación del sistema de identidad.
Adjuntar un momento no equivale a verificación automática de su contenido. El árbitro
existente mantiene la verificación de foto/voz y su bonificación por separado.

## API

El perfil incluye un dashboard de los últimos 30 días: minutos registrados en avances,
retos completados frente a completados o detenidos, actividades distintas y evidencias
verificadas. No estima atención mental ni considera una evidencia adjunta como verificada.
La velocidad usa las unidades declaradas al guardar un momento y la duración real
registrada de esa sesión; cada tipo de unidad se calcula por separado. Sin duración
registrada no se calcula velocidad.

La continuación ofrece temporizador o cronómetro. El cronómetro cuenta desde cero
hasta guardar/detener el avance, con límite de dos horas o del espacio libre del calendario.
El reloj usa tiempo monotónico para recuperar el tiempo transcurrido durante pausas de
ejecución; no recupera sesiones tras terminar el proceso Android.

El zorrito permite nombre, tres colores y tres accesorios guardados en este dispositivo.
El dashboard se comparte como PNG 1080×1920 con esa personalización mediante el selector
de Android. La personalización aún no se sincroniza entre dispositivos.

- `GET /api/v1/profile`: XP, categoría, cadenas y momentos del usuario.
- `POST /api/v1/profile/chains/{id}/continue`: meta, minutos y técnica opcional.
- `POST /api/v1/profile/moments`: sesión completada, descripción y evidencia opcional.
- `GET /api/v1/profile/moments/{id}/media`: descarga protegida de audio/video.

Aplicar `python -m alembic -c backend/alembic.ini upgrade head` antes de usar
esta versión. La migración 0004 añade los adjuntos multimedia sin alterar datos previos.

Validación: pruebas de cadena, reintentos, puntos, caducidad, calendario, autenticación,
adjuntos y aprendizaje; compilación debug y pruebas unitarias de Android.
