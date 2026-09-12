# Reorganización de navegación y listados

Se conserva la hoja de estilos, los permisos y todas las opciones existentes.
La navegación deja visibles Panel, Hoy, Pendientes, Buscar y Configuración.
Seguimiento reúne Alertas, Calendario, Actividad diaria, Cumplidos y Equipo;
Expedientes reúne Judiciales y Administrativos. El grupo de la sección actual
se abre al entrar y su enlace conserva `aria-current`.

Los listados de pendientes, judiciales y administrativos muestran primero el
registro y la búsqueda. Los filtros adicionales y el orden están en un control
HTML nativo que se abre automáticamente si alguno de sus valores no es el
predeterminado. Los campos conservan nombres, valores y envío GET.
Las fichas, formularios e historiales incorporan navegación común; los accesos
a editar se sitúan junto al título de las fichas.

Las tablas de los tres listados y el calendario mensual conservan sus columnas
en una región desplazable con acceso por teclado. Esto evita que la página
completa se ensanche en móvil. No se añaden dependencias de frontend ni JavaScript.

## Verificación reproducible

`RevisionConNavegadorTest` recorre las pantallas con Chromium mediante Playwright.
Su prueba `organizacionEnEscritorioYMovil` captura siete pantallas a 1280 y 390 px,
comprueba que permanecen los doce destinos, abre los filtros, aplica una selección,
comprueba su persistencia y navega por el grupo Expedientes con teclado.
Las capturas se guardan en `target/ux-review/`.

También se ejecutan los recorridos de configuración, filtros y cancelación,
expedientes y pendientes, agenda, equipo y recorrido completo, junto con
`HealthContractTest` para comprobar ping, Actuator y acceso protegido.

El detector mecánico de Impeccable funciona en modo reducido por falta de sus
módulos de análisis HTML. Sus avisos sobre guiones corresponden a marcadores de
celdas vacías preexistentes; la comprobación de layout se realiza con Playwright.

Resultado: 58 pruebas aprobadas en las ocho clases indicadas. Las catorce
capturas de escritorio y móvil no presentan desborde horizontal de página.
