# Auditoría de código, lógica y UX/UI

**Sistema Jurídico · 26–27 de septiembre de 2026 · zona America/Lima**\
Versión revisada: `26c0428`. Método: revisión de código con Graphify, pruebas automatizadas, exploración con **Playwright MCP** y guía de auditoría de **Impeccable**.

**Actualización:** los once hallazgos de este informe se corrigieron posteriormente. Véase el [informe de correcciones y verificación](correcciones-auditoria-2026-09-27.md). El contenido siguiente conserva la evidencia del estado inicial.

## Dictamen de integridad

**No aprobado todavía.** La interfaz expresa un producto coherente: navegación por trabajo jurídico, textos mayormente en español y dos modos de presentación diferenciados. Sin embargo, se reprodujeron pérdidas silenciosas de valores, una edición que termina creando otra actividad y un cambio de estado que persiste aunque falle su registro de auditoría. Son problemas de comportamiento que requieren corrección antes de dar por validada la experiencia.

**11 hallazgos confirmados: 0 P0, 6 P1 y 5 P2.** No se añadieron observaciones puramente cosméticas al recuento. P1 significa impacto importante; P2, impacto acotado o con alternativa de recuperación. La ausencia de P0 en esta exploración no certifica ausencia de otros defectos.

| ID | Prioridad | Hallazgo | Impacto principal |
|---|---|---|---|
| F01 | P1 | Cancelar/devolver sin transacción completa | Cambio persistido sin evidencia de auditoría |
| F02 | P1 | Se pierde el responsable tras un error de validación | Expediente asignado a otra persona |
| F03 | P1 | Editar borra un valor de catálogo deshabilitado | Pérdida silenciosa de clasificación |
| F04 | P1 | Una corrección inválida pasa al formulario de alta | Duplicación de actividad al reintentar |
| F05 | P1 | Tablas móviles ilegibles o desbordadas | Dificultad importante para consultar el trabajo |
| F06 | P1 | Validación rechazada sin identificar el campo | La persona no sabe cómo corregir el formulario |
| F07 | P2 | Se pierde el contexto de filtros | Trabajo repetido al volver al listado |
| F08 | P2 | Errores generales en inglés y sin navegación | Recuperación confusa ante un fallo |
| F09 | P2 | Desbordamiento del cálculo de paginación | Una página numérica extrema provoca HTTP 500 |
| F10 | P2 | Fechas UTC y acciones internas en pantalla | Interpretación confusa del historial |
| F11 | P2 | Ayuda de asignación contradice las funciones disponibles | Instrucciones incorrectas para la jefatura |

## Evaluación Impeccable

| Dimensión | Puntuación | Evidencia principal |
|---|---:|---|
| Accesibilidad | 2/4 | Etiquetas y foco presentes; recuperación de errores incompleta, F06 |
| Rendimiento | 3/4 | Navegación local rápida y recursos ligeros; sin evaluación de red lenta |
| Adaptación a pantallas | 2/4 | Formularios utilizables; tablas móviles con problemas importantes, F05 |
| Temas y presentación | 3/4 | Cambio de modo funcional; algunos valores visuales fuera de tokens |
| Integridad de implementación | 1/4 | Pérdidas de estado repetidas y mensajes contradictorios, F01–F04 y F11 |
| **Total** | **11/20** | **Banda de la rúbrica: aceptable, con trabajo significativo pendiente** |

La puntuación resume la muestra examinada. No constituye aprobación de publicación ni certificación WCAG.

## Alcance y pruebas

- Aplicación local en `localhost:18090`, PostgreSQL temporal independiente en el puerto `55459`, datos sintéticos y cuentas de jefatura y abogada. No se utilizaron datos de producción.
- Graphify actualizado mediante `graphify . --code-only`: 2.663 nodos y 10.403 relaciones. Se consultaron los recorridos de formularios, acciones, permisos y auditoría antes de comprobar sus implementaciones.
- Playwright MCP real, versión del servidor `1.63.0-alpha-2026-08-31`, con Chromium instalado. Las pruebas Java del proyecto usan su propia dependencia Playwright.
- **112 visitas de pantalla:** 28 rutas × 2 modos × 2 anchuras, 1440 y 390 px. Todas respondieron HTTP 200 durante ese barrido; no se observaron excepciones JavaScript. Los errores provocados en pruebas negativas se registraron por separado.
- Revisión adicional de seis rutas a 1440 y 320 px: dimensiones, contraste de texto mediante estilos calculados, teclado y conservación del borrador al cambiar de modo.
- Flujos explorados: activación y acceso, altas judicial/administrativa/pendiente, importes, edición, validación y reintento, catálogos deshabilitados, actividad diaria, cumplimiento y reversión, reprogramación, cancelación y devolución, reasignación con pendientes vinculados, búsqueda, filtros, paginación, estados vacíos, concurrencia, permisos, CSRF, escape de texto y cierre de sesión.
- Se capturaron 45 imágenes durante la exploración; se conserva una selección de 12 junto a este informe.

### Suite automatizada

Primera ejecución: `./mvnw -B -q verify`, **código de salida 0**.

| Grupo | Clases | Casos | Aprobados | Omitidos | Fallos/errores |
|---|---:|---:|---:|---:|---:|
| Surefire | 59 | 412 | 409 | 3 | 0 |
| Failsafe | 81 | 367 | 367 | 0 | 0 |
| **Total inicial** | **140** | **779** | **776** | **3** | **0** |

Las omisiones iniciales corresponden a dos casos condicionados por `ux.audit` y uno por `ui.capture`. Se ejecutaron después habilitando ambas propiedades: los seis casos de las dos clases seleccionadas pasaron, incluidos los tres inicialmente omitidos. **Resultado acumulado: 779 casos únicos aprobados, sin casos pendientes por omisión.** El detalle está al final de este informe. Las pruebas automatizadas aprobadas no cubren todos los reintentos y transiciones descritos a continuación.

## Hallazgos P1

### F01. Cancelar o devolver puede persistir sin su evidencia de auditoría

**Categoría:** integridad de implementación.\
**Ubicación:** [PendingTaskActionService.java:198](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskActionService.java:198), método `devolver` en la línea 203 y escritura de auditoría en la 221; [AuditRecorder.java:19](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/audit/AuditRecorder.java:19).

`cancelar` y `devolver` no abren una transacción. El auxiliar modifica el pendiente y después registra el evento. `AuditRecorder` depende explícitamente de la transacción de quien lo llama. El bloqueo de fila tampoco queda protegido por una transacción que abarque toda la operación.

**Reproducción:** en la base temporal se instaló un disparador limitado al pendiente de prueba que rechazaba su evento de auditoría. Se pulsó **Cancelar** desde el navegador. La respuesta fue HTTP 500, pero el registro pasó de `active=true, version=5` a `active=false, version=6`; el número de eventos `CANCEL` permaneció en cero. Al volver a la ficha, el pendiente estaba cancelado. Se retiró el disparador y se comprobaron cancelación y devolución normales. El fallo inducido se reprodujo en cancelar; devolver comparte el mismo recorrido sin transacción.

**Impacto:** la pantalla anuncia un fallo aunque el estado cambió, y falta la evidencia de quién realizó la operación. Un reintento puede partir de un estado inesperado.

**Corrección recomendada:** transacción en las entradas públicas de ambas acciones, que abarque bloqueo, autorización, versión, cambio y auditoría. Añadir regresión con fallo forzado del registro de evidencia y comprobar que estado, versión e historial permanecen intactos.

**Evidencia:** [estado anterior](auditoria-2026-09-27/atomicidad-antes.txt), [estado posterior](auditoria-2026-09-27/atomicidad-despues.txt), [captura](auditoria-2026-09-27/cancelar-fallo-auditoria.png), llamada MCP 19. Script de fallo conservado exclusivamente como reproducción para una base descartable. **Comando sugerido:** `$impeccable harden`, acompañado de la corrección transaccional del servicio.

### F02. Se pierde el responsable seleccionado después de una validación fallida

**Categoría:** integridad de implementación.\
**Ubicación:** [JudicialCaseController.java:171](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/judicialcase/JudicialCaseController.java:171) y [AdministrativeProcedureController.java:175](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/administrativeprocedure/AdministrativeProcedureController.java:175).

**Reproducción:** como jefatura, crear un expediente y seleccionar a la abogada como responsable; introducir un número ya existente y guardar. La validación de duplicado aparece correctamente, pero desaparece el selector de responsable. Corregir únicamente el número y volver a guardar: el expediente queda a nombre de **Jefatura Auditoría**. Se reprodujo tanto en judiciales como en administrativos.

**Causa:** el modelo de error conserva el formulario principal, pero no los candidatos ni la selección de `ownerId`. Al reenviar sin este parámetro, el controlador utiliza la identidad de la jefatura.

**Impacto:** una corrección de número cambia silenciosamente la asignación del trabajo.

**Corrección recomendada:** conservar la selección explícita y repoblar sus opciones en todos los retornos de validación. Si el destinatario deja de estar disponible, mostrar un error de responsable; evitar sustituir una selección inválida silenciosamente. Probar error → corrección → guardado en ambos módulos.

**Evidencia:** [captura del formulario](auditoria-2026-09-27/responsable-perdido.png), llamada MCP 16 (`ownerSelectorCount: 0` y responsable final). **Comando sugerido:** `$impeccable harden`.

### F03. Editar observaciones borra el tipo si se deshabilitó en el catálogo

**Categoría:** integridad de implementación.\
**Ubicación:** [PendingTaskCatalogs.java:54](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskCatalogs.java:54), [PendingTaskController.java:326](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskController.java:326) y [pending-tasks/edit.html:27](/home/n4nd0/Documentos/SistemaJuridico/src/main/resources/templates/pending-tasks/edit.html:27).

**Reproducción:** tener un pendiente con tipo **Revisión**; deshabilitar Revisión desde el catálogo; abrir la edición del pendiente. El selector muestra **Sin tipo** porque la opción original ya no existe. Cambiar solamente las observaciones y guardar: la ficha queda definitivamente sin tipo.

**Impacto:** mantener un catálogo provoca pérdida de información histórica al editar otro dato. El mismo patrón de opciones habilitadas aparece en otros catálogos y merece revisión; el caso comprobado en navegador fue el tipo del pendiente.

**Corrección recomendada:** incluir el valor actualmente asociado aunque esté retirado, identificado como tal, y permitir conservarlo. Impedir nuevas asociaciones a opciones retiradas desde el servidor. La opción conservada debe seguir enviándose en el formulario; deshabilitarla mediante el atributo HTML `disabled` también puede omitirla al enviar.

**Evidencia:** [captura](auditoria-2026-09-27/catalogo-perdido.png), llamada MCP 17 (`disabledCatalog`). **Comando sugerido:** `$impeccable harden`.

### F04. Una edición inválida de actividad se transforma en una alta al reintentar

**Categoría:** integridad de implementación.\
**Ubicación:** [DailyActivityController.java:143](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/activity/DailyActivityController.java:143) y [activity/form.html:13](/home/n4nd0/Documentos/SistemaJuridico/src/main/resources/templates/activity/form.html:13), formulario de edición en la línea 55.

**Reproducción:** registrar una actividad; desplegar **Corregir** y cambiar su descripción. Mantener un tipo de catálogo e introducir también **Otro tipo**. Al guardar aparece el error esperado, pero la descripción modificada aparece en **Agregar actividad**; el formulario de corrección vuelve al texto original. Resolver el error en el formulario que conserva lo escrito y enviarlo crea una segunda actividad. Se observaron simultáneamente el original y la corrección como dos registros.

**Causa:** el controlador de edición reutiliza el atributo `form` del alta y vuelve a cargar la página sin preservar el identificador, el destino de edición y su estado abierto.

**Corrección recomendada:** modelar la edición fallida con su ID y versión; volver a mostrarla abierta, con valores y errores propios, y mantener su acción de actualización. El formulario de alta debe conservar su estado independiente.

**Evidencia:** [captura](auditoria-2026-09-27/actividad-error-edicion.png), llamada MCP 17 (`activityValidation`). **Comando sugerido:** `$impeccable harden`.

### F05. Las tablas no ofrecen una consulta legible en móvil

**Categoría:** adaptación a pantallas.\
**Ubicación:** [estandar.css:203](/home/n4nd0/Documentos/SistemaJuridico/src/main/resources/static/css/estandar.css:203), regla de ancho mínimo en la línea 130; [completed.html:18](/home/n4nd0/Documentos/SistemaJuridico/src/main/resources/templates/pending-tasks/completed.html:18) y [today.html:25](/home/n4nd0/Documentos/SistemaJuridico/src/main/resources/templates/pending-tasks/today.html:25).

**Modo Estándar:** a 390 px, las tablas directas de Cumplidos y Hoy comprimen palabras y fechas hasta partirlas repetidamente. A 320 px, la cabecera de Cumplidos mide **328 px de alto**, con columnas de aproximadamente 35–42 px. La regla `overflow-wrap: anywhere` consigue que no desborde el documento, pero no que el contenido resulte legible. El ancho mínimo ya utilizado en tablas dentro de regiones no se aplica a estas tablas directas.

**Modo Rendimiento:** a 390 px, **12 de las 28 rutas** desbordan el documento. Cumplidos alcanza 1.045 px; Hoy, 550 px; Usuarios, 659 px. También ocurre en Alertas, Equipo, cinco catálogos, una ficha judicial y el historial del pendiente.

**Impacto:** es difícil relacionar títulos, fechas y acciones; el usuario necesita recorrer tablas fragmentadas o desplazar toda la página lateralmente.

**Corrección recomendada:** aplicar a las tablas anchas un contenedor de desplazamiento identificado y operable con teclado, conservando anchos legibles, o usar una representación por registro para móvil. Comprobar ambos modos con contenido real. La regresión debe medir legibilidad y acceso a acciones, además de ausencia de desbordamiento del documento. No se considera defecto el desplazamiento horizontal contenido que una tabla necesite para preservar sus relaciones.

**Evidencia:** [Hoy Estándar](auditoria-2026-09-27/hoy-estandar-movil.png), [Cumplidos Estándar](auditoria-2026-09-27/cumplidos-estandar-movil.png), [Cumplidos Rendimiento](auditoria-2026-09-27/cumplidos-rendimiento-movil.png); llamadas MCP 13, 15 y 22. **Comando sugerido:** `$impeccable adapt`.

### F06. Se rechaza una descripción larga sin identificar el campo ni el motivo

**Categoría:** accesibilidad y recuperación de errores.\
**Ubicación:** [pending-tasks/form.html:24](/home/n4nd0/Documentos/SistemaJuridico/src/main/resources/templates/pending-tasks/form.html:24) y [PendingTaskValidator.java:64](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskValidator.java:64).

**Reproducción:** registrar un pendiente con título válido y descripción de **10.001 caracteres**. El servidor conserva el texto y rechaza el guardado. La página muestra solamente el aviso genérico de revisar los campos; no aparece el error específico de descripción ni su límite. El campo carece de `maxlength`, `aria-invalid` y asociación a un mensaje de error.

**Impacto:** la persona no puede localizar la causa del rechazo, especialmente si ha pegado un texto extenso. El aviso promete campos señalados que no están señalados.

**Estándar:** el caso observado incumple la identificación textual del campo y del error requerida por [WCAG 2.2, 3.3.1 — Error Identification](https://www.w3.org/WAI/WCAG22/Understanding/error-identification.html). La ausencia de un atributo ARIA por sí sola no fundamenta esta conclusión; la evidencia es la falta de identificación y explicación en pantalla.

**Corrección recomendada:** representar todos los errores producidos por el validador, asociarlos a sus controles e indicar el límite antes de enviar. Mantener la validación del servidor y el texto ingresado. Ofrecer un resumen que permita llegar al campo. Revisar también observaciones y datos del documento de salida, que tienen validación propia.

**Evidencia:** [captura](auditoria-2026-09-27/validacion-sin-campo.png), llamada MCP 16 (`longDescription`). **Comando sugerido:** `$impeccable harden`.

## Hallazgos P2

### F07. Se pierden los filtros al ejecutar acciones o volver desde una ficha

**Categoría:** integridad de implementación y navegación.\
**Ubicación:** [VueltaAlListado.java:28](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/shared/VueltaAlListado.java:28), [PendingTaskFilters.java:159](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskFilters.java:159) y [pending-tasks/detail.html:12](/home/n4nd0/Documentos/SistemaJuridico/src/main/resources/templates/pending-tasks/detail.html:12).

**Reproducción:** buscar `AUD-PAG-001 Revisar` y cancelar la fila. El formulario contiene `?q=AUD-PAG-001+Revisar`, pero se termina en `/pendientes` sin filtros. `URLEncoder` representa espacios mediante `+`, carácter que la expresión regular de retorno rechaza. Independientemente, abrir una ficha desde una consulta con búsqueda y alerta y pulsar **Volver a pendientes** también elimina el contexto porque el enlace es fijo.

**Impacto:** revisar varios resultados obliga a repetir búsquedas y filtros.

**Corrección recomendada:** reconstruir de forma segura el contexto de filtros permitido y conservarlo en acciones y fichas. Alinear codificación y validación; mantener la protección frente a destinos externos. Probar espacios, tildes, filtros combinados y página actual.

**Evidencia:** llamadas MCP 17 y 21. **Comando sugerido:** `$impeccable harden`.

### F08. Los errores HTTP generales muestran una página técnica en inglés

**Categoría:** integridad de implementación y claridad.\
**Ubicación:** [ErrorHandling.java:41](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/shared/ErrorHandling.java:41); los manejadores cubren excepciones del dominio, pero falta una presentación general de errores HTTP.

**Reproducción:** con sesión abierta, visitar una ruta inexistente devuelve 404 y `/pendientes?ownerId=no-es-uuid` devuelve 400. Ambos muestran **Whitelabel Error Page**, explicación en inglés, documento sin idioma y sin enlaces de recuperación. Los errores 500 provocados durante esta auditoría tienen la misma presentación. Las excepciones de dominio probadas sí muestran páginas en español.

**Impacto:** la persona pierde el contexto de la aplicación y no recibe una acción clara para continuar.

**Corrección recomendada:** vistas generales localizadas para errores del framework, manteniendo su estado HTTP y ofreciendo volver al inicio o al contexto válido. Reservar detalles técnicos para el registro del servidor.

**Evidencia:** [captura](auditoria-2026-09-27/error-no-personalizado.png), llamadas MCP 18 y 19. **Comando sugerido:** `$impeccable clarify`.

### F09. Una página numérica extrema desborda el desplazamiento SQL

**Categoría:** integridad de implementación.\
**Ubicación:** [Paging.java:22](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/shared/Paging.java:22).

**Reproducción:** acceder a `/pendientes?page=100000000`. El parámetro cabe en un entero, pero `page * size` desborda y genera `OFFSET -1794967296`; PostgreSQL lo rechaza y la aplicación responde HTTP 500.

**Impacto y alcance:** es un caso límite al modificar la URL, no un fallo observado al pulsar la paginación normal. Esta última funcionó correctamente con 25 resultados en la primera página y 2 en la segunda.

**Corrección recomendada:** calcular el desplazamiento con un tipo suficiente y validar límites de página. Dar una respuesta controlada para valores fuera del rango admitido. Añadir casos de frontera sin ejecutar consultas con desplazamientos negativos.

**Evidencia:** llamada MCP 18 y [extracto del error de paginación](auditoria-2026-09-27/paginacion-error.txt), procedente de `target/auditoria-2026-09-26/application.log`. **Comando sugerido:** `$impeccable harden`, junto con la validación del cálculo en Java.

### F10. El historial mezcla códigos internos y fechas UTC sin formato local

**Categoría:** claridad e integridad de presentación.\
**Ubicación:** [pending-tasks/history.html:34](/home/n4nd0/Documentos/SistemaJuridico/src/main/resources/templates/pending-tasks/history.html:34), [detail.html:28](/home/n4nd0/Documentos/SistemaJuridico/src/main/resources/templates/pending-tasks/detail.html:28) y [completed.html:40](/home/n4nd0/Documentos/SistemaJuridico/src/main/resources/templates/pending-tasks/completed.html:40).

**Reproducción:** completar un pendiente el 26 de septiembre a las 23:58 en Lima. La ficha muestra `Cumplido el 2026-09-27T04:58:09.840476Z`. Cancelar y devolver normalmente produce entradas visibles con las acciones **CANCEL** y **RESTORE**.

**Impacto:** la fecha aparente puede diferir del día que maneja el usuario y los códigos no explican claramente el cambio. El instante almacenado es correcto; el problema verificado corresponde a su presentación, no al cálculo de plazos.

**Corrección recomendada:** formatear instantes en la zona de la aplicación con fecha y hora legibles; traducir todas las acciones, incluidas cancelar y devolver. Mantener la representación temporal de almacenamiento.

**Evidencia:** [historial](auditoria-2026-09-27/historial-fecha-y-acciones.png), llamadas MCP 17 y 20. **Comando sugerido:** `$impeccable clarify`.

### F11. La ayuda de los formularios contradice la asignación y reasignación existentes

**Categoría:** integridad de implementación y claridad.\
**Ubicación:** [judicial-cases/form.html:99](/home/n4nd0/Documentos/SistemaJuridico/src/main/resources/templates/judicial-cases/form.html:99) y [administrative-procedures/form.html:75](/home/n4nd0/Documentos/SistemaJuridico/src/main/resources/templates/administrative-procedures/form.html:75).

El formulario informa que el responsable será quien registra y que la reasignación entre abogados no forma parte de esta versión. La jefatura ve en ese mismo formulario un selector de responsable. La reasignación existe en la ficha y se comprobó que mueve también los pendientes vinculados.

**Impacto:** la ayuda hace dudar sobre el resultado de guardar y presenta como inexistente una función disponible.

**Corrección recomendada:** texto según rol y selección efectiva. Para jefatura, explicar a quién se asignará y dónde podrá cambiarlo. Mantener las pruebas de contenido alineadas con la capacidad real.

**Evidencia:** plantillas citadas, capturas de creación y comprobación de reasignación en los resultados de exploración. **Comando sugerido:** `$impeccable clarify`.

## Patrones que conviene corregir de forma conjunta

1. **Estado incompleto al volver por un error:** F02 y F04 muestran que conservar texto no basta; también deben persistir responsable, identidad del registro y tipo de operación.
2. **Opciones históricas fuera del formulario:** F03 reproduce en catálogos el riesgo que el código ya contempla para expedientes vinculados archivados. Es útil aplicar el mismo principio de conservación a cada selector.
3. **Cambios que requieren evidencia indivisible:** F01 revela que una acción nueva puede quedar fuera del límite transaccional seguido por las otras acciones del servicio.
4. **Pruebas visuales demasiado indirectas:** comprobar `scrollWidth` no detecta palabras apiladas verticalmente. F05 requiere mirar contenido poblado y acciones utilizables.
5. **Presentación compartida incompleta:** errores, fechas, acciones y ayudas necesitan reglas comunes para evitar F06, F08, F10 y F11 en pantallas nuevas.

## Comprobaciones satisfactorias

- Los permisos probados coinciden con la consulta compartida y edición restringida: una abogada pudo ver un expediente ajeno, pero editarlo, editar su pendiente o abrir Usuarios devolvió 403. Un POST directo para cancelar un pendiente ajeno también fue rechazado.
- Un POST sin CSRF devolvió 403. Un título con una etiqueta y un manejador de evento se mostró como texto, sin ejecutar código. Son comprobaciones concretas, no una certificación completa de seguridad.
- Dos formularios abiertos del mismo pendiente produjeron 409 en el segundo guardado; se conservó la modificación de la primera pestaña.
- Funcionaron las altas normales, el importe con coma decimal, la edición propia, el cumplimiento y reversión, la cancelación y devolución normales y la reasignación con pendientes vinculados.
- La reprogramación de una fecha vencida terminó en el lunes 28 de septiembre según el calendario sintético disponible. Cuando faltaba cobertura de calendario, la interfaz avisaba y mostraba datos desconocidos explícitamente.
- La paginación y el estado vacío ofrecieron resultados y opciones de recuperación coherentes. El cierre de sesión mostró confirmación y el acceso posterior a una ruta protegida volvió al inicio de sesión.
- No se encontraron controles visibles sin etiqueta en el barrido de 112 visitas. La navegación por teclado examinada mostró foco visible; el selector de modo respondió a teclado, actualizó su estado accesible y conservó el borrador.
- La muestra de contraste de texto no produjo fallos con el cálculo utilizado. El escritorio Estándar mantiene una jerarquía visual consistente: [captura del panel](auditoria-2026-09-27/panel-estandar-escritorio.png).
- Las duraciones de navegación del barrido local estuvieron aproximadamente entre 24 y 178 ms. Son medidas locales de navegación, no métricas de experiencia en producción ni una prueba de carga completa.

## Detector Impeccable y observaciones secundarias

El detector emitió **31 avisos**: 13 de colores, 10 de tamaños de letra, 3 de radios y 5 de uso de raya. Se ejecutó en modo degradado mediante expresiones regulares porque faltaban `htmlparser2`, `css-select`, `css-tree` y `domutils`. Su salida no equivale a 31 defectos confirmados ni aporta una medición de contraste calculado.

Se descartaron como defectos las rayas usadas para datos ausentes y los valores de la identidad visual Rendimiento conservada intencionalmente. Los tamaños adaptativos se contrastaron con el contexto de diseño. Quedan algunas oportunidades de unificar colores de estado con tokens, de prioridad inferior a los fallos reproducidos.

Las acciones compactas de filas miden unos 32 px de alto y el control de presentación, 28 px. Aumentar sus áreas táctiles puede mejorar el uso móvil; no se afirma por esas medidas una infracción automática del criterio AA de tamaño de objetivo. La navegación expandida también lleva el inicio del contenido hasta aproximadamente 450–503 px en algunas pantallas móviles; conviene reducir el esfuerzo para llegar a la tarea después de resolver las tablas.

## Orden de corrección recomendado

1. **P1 — `$impeccable harden`:** F01–F04. Resolver atomicidad y conservación de datos; añadir regresiones del error y el reintento, además del recorrido exitoso.
2. **P1 — `$impeccable adapt`:** F05. Unificar el tratamiento de tablas anchas en ambos modos y comprobar 320/390 px con registros poblados.
3. **P1 — `$impeccable harden`:** F06. Hacer visibles y accesibles todos los errores de validación.
4. **P2 — `$impeccable harden`:** F07 y F09. Conservar contexto y validar límites de paginación.
5. **P2 — `$impeccable clarify`:** F08, F10 y F11. Corregir recuperación, fechas, nombres de acciones e instrucciones.
6. **Comprobación — `$impeccable audit`:** repetir los recorridos fallidos y la matriz de ambos modos tras las correcciones.
7. **Acabado — `$impeccable polish`:** ajustar áreas táctiles, navegación móvil y consistencia de tokens.

Estos grupos pueden ejecutarse individualmente, juntos o en otro orden acordado. La nueva auditoría permitirá comparar resultados después de corregir los fallos.

## Evidencias y límites

- [Resultados de Playwright MCP](auditoria-2026-09-27/resultados-exploracion.json): respuestas estructuradas de las llamadas indicadas; valores CSRF efímeros omitidos.
- [Resumen de la suite inicial](auditoria-2026-09-27/test-summary.json).
- [Relaciones consultadas con Graphify](auditoria-2026-09-27/graphify-relationships.txt).
- [Detector Impeccable](auditoria-2026-09-27/impeccable-detector.json) y [advertencia de modo degradado](auditoria-2026-09-27/impeccable-detector.stderr.log).
- Scripts conservados: [barrido de pantallas](auditoria-2026-09-27/exploracion-pantallas.js), [validaciones](auditoria-2026-09-27/exploracion-validaciones.js), [edición](auditoria-2026-09-27/exploracion-edicion.js), [accesibilidad](auditoria-2026-09-27/exploracion-accesibilidad.js). Son funciones usadas desde MCP sobre la sesión y los datos sintéticos creados durante la auditoría, no una suite autónoma lista para otro entorno.
- [Inyección acotada de fallo](auditoria-2026-09-27/inyectar-fallo-auditoria.sql): únicamente para reproducir F01 en una base temporal descartable.
- Registros completos, capturas adicionales y transcripción MCP en `target/auditoria-2026-09-26/`; ese directorio puede desaparecer con una limpieza de Maven.

No se evaluaron Firefox/WebKit, lectores de pantalla reales, red móvil lenta ni integraciones de producción. El muestreo de contraste no utilizó axe ni sustituye una auditoría WCAG completa. Los días no laborables cargados eran sintéticos: se verificó la lógica de cobertura y reprogramación, no la exactitud jurídica de un calendario oficial.

Un fallo inicial al reemplazar el JAR durante la compilación se descartó como incidencia del montaje de la auditoría. La exploración válida utilizó una copia inmutable del artefacto. Tampoco se contabilizaron errores de selectores de los propios guiones exploratorios.

Se documentan los hallazgos; **no se ha modificado el código funcional**. Los cambios del espacio de trabajo corresponden a la actualización solicitada del grafo y a los archivos de esta auditoría. Se dejó actualizada la caché de Graphify.

## Comprobación adicional y cierre

Se ejecutó también:

```sh
./mvnw -B -q -Dtest=RevisionPantallasTest,ModosPresentacionTest -Dux.audit=true -Dui.capture=true test
```

**Código de salida 0.** `RevisionPantallasTest`: 2 aprobadas; `ModosPresentacionTest`: 4 aprobadas; cero fallos, errores u omisiones. Tres casos repiten pruebas ya aprobadas en la suite inicial y tres completan sus omisiones: el total acumulado es **779 casos únicos aprobados**. [Resultado adicional](auditoria-2026-09-27/pruebas-ux-opcionales.json).

Se cerraron el navegador y el servidor Playwright MCP de la exploración. La aplicación temporal fue detenida y el contenedor PostgreSQL `sj-auditoria-20260926`, creado con eliminación automática, fue detenido y eliminado. El disparador de fallo ya había sido retirado antes de las comprobaciones normales finales. Las evidencias seleccionadas permanecen en `docs/auditoria-2026-09-27/`.
