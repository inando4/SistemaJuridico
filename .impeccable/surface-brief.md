# Panel del día y presentación compartida

## Dirección construida

Tablero de coordinación del área: identificar urgencias, abrir el registro y resolver el siguiente paso. La primera vista contiene cabecera institucional, selector arriba a la derecha, navegación lateral y seis recuentos. En móvil la navegación pasa arriba y las métricas usan dos columnas.

## Dos presentaciones

Estándar es el valor inicial cuando el navegador no guarda Rendimiento. Rendimiento conserva app.css; presentacion.css aporta el selector común. presentacion.js añade estandar.css únicamente cuando se necesita Estándar y lo deshabilita al volver a Rendimiento. Una carga con Rendimiento guardado no solicita estandar.css.

Al iniciar en Estándar, su hoja bloquea el primer pintado con blocking="render". Como respaldo para navegadores que no admiten ese atributo, el contenido espera oculto hasta que la hoja cargue o falle; cambiar a Rendimiento también libera la espera. Este mecanismo solo se activa antes de crear el body, evitando el destello del estilo ligero entre secciones.

El botón nativo admite teclado, mantiene su foco y actualiza aria-checked. El cambio no recarga la página ni reconstruye los formularios. La preferencia usa localStorage en el navegador y se sincroniza entre pestañas; si el almacenamiento falla, sigue disponible durante la página. Sin JavaScript permanece la base ligera y el control oculto.

## Evidencia y alcance

Fuente: css/estandar.css, css/presentacion.css, css/app.css, js/presentacion.js y fragments/presentacion.html bajo src/main/resources. El contrato de dirección está en templates/dashboard/index.html. El sistema compartido se integra en las plantillas completas; las funciones y los permisos existentes siguen determinando el contenido.

DESIGN.md y .impeccable/design.json documentan la implementación. Las capturas en .impeccable/review son evidencia de revisión, no dependencias de producto. No se añaden fuentes, imágenes raster ni recursos visuales externos. No se establece una preferencia permanente de buildPath.
