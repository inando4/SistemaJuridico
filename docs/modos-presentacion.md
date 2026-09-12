# Modos de presentación

El interruptor está arriba a la derecha en todas las pantallas completas, incluido el acceso.

- **Estándar** es la presentación inicial: navegación lateral en escritorio y superior en móvil, campos y botones uniformes, avisos de plazos y tablas legibles con desplazamiento interno cuando hace falta.
- **Rendimiento** conserva `app.css` sin modificaciones. Al recargar con esa preferencia, el navegador no solicita `estandar.css`.

La elección se guarda en este navegador y se sincroniza entre sus pestañas. Cambiar de modo no recarga la pantalla ni descarta campos sin guardar. El interruptor admite Tab, Espacio y Enter. Si el navegador bloquea el almacenamiento, sigue funcionando para la página abierta. Sin JavaScript se utiliza el estilo ligero y se oculta el interruptor; el acceso y la navegación siguen funcionando.

Los estilos, el script y HTMX se sirven localmente. No se agregan frameworks, fuentes externas ni imágenes decorativas. El modo visual no cambia permisos, datos ni reglas de trabajo.

## Verificación

- Suite completa: `./mvnw verify`.
- Preferencia, conservación del formulario, teclado, dos pestañas y degradación: `./mvnw -Dtest=ModosPresentacionTest test`.
- Capturas de escritorio (1440 px) y móvil (390 px), en ambos modos: `./mvnw -Dtest=ModosPresentacionTest -Dui.capture=true test`.
- Evidencia visual y revisión independiente: `.impeccable/review/` (artefactos locales).

Las pruebas usan PostgreSQL real y datos sintéticos. Se necesita Docker disponible y Chromium de Playwright instalado. No son una medición del rendimiento del equipo de producción.

## Resultado de esta entrega

Se ejecutó la suite de 412 casos de Surefire. Se corrigió una espera de navegador del recorrido de agenda y se repitieron sus 10 casos, junto con los 4 del selector y la captura visual. Los informes finales reúnen 410 casos aprobados y 2 omitidos de la auditoría opcional. Failsafe ejecutó 367 pruebas de integración aprobadas. El comando de confirmación `./mvnw -Dtest=RecorridoAgendaTest,ModosPresentacionTest -Dui.capture=true verify` terminó correctamente y generó el JAR.

La revisión independiente inspeccionó las 24 capturas de ambos modos. Solicitó corregir el contraste del foco en la navegación oscura; su pase final marcó ese hallazgo como resuelto, con una medición de 10,23:1 en escritorio y móvil. El detector mecánico funcionó con capacidades reducidas por falta de módulos de análisis: no sustituye esa revisión ni certifica todos los estados posibles.
