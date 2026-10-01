# Alta de expediente desde un pendiente

Superficie **Operate**: extensión de `/pendientes/nuevo` con la identidad existente Estándar/Rendimiento y recursos locales. Reutiliza tokens, formularios y controles del sistema.

En «Vincular a un expediente» se elige Sin expediente, Judicial o Administrativo. Solo se envía el vínculo activo. «Crear nuevo», junto al selector correspondiente, abre un diálogo con todos los campos del alta habitual. La edición del pendiente comparte el selector.

«Crear y seleccionar» guarda el expediente, incorpora la opción y la selecciona. **Después debe guardarse el pendiente para completar el vínculo**. Abandonar el pendiente conserva el expediente ya creado. El responsable del pendiente sigue siendo quien lo registra, aunque la jefatura asigne otro responsable al expediente.

El pendiente permanece en el DOM; cada tipo conserva su borrador en memoria de la página. Cerrar o pulsar Escape conserva lo escrito y devuelve el foco. Recargar o abandonar la página pierde esos borradores. Durante el POST se bloquean campos, botones y cierre. Los errores de validación señalan campos; los fallos de conexión conservan datos. Una creación sin confirmación exige comprobar el resultado antes de repetirla. La recuperación de sesión permite iniciar sesión en otra pestaña y recargar el formulario preservando el borrador.

Las solicitudes incluyen CSRF y reutilizan autorización, validación e historial del alta habitual. GET `/judiciales/nuevo?modal=true` y `/administrativos/nuevo?modal=true` entregan fragmentos; POST `/judiciales?modal=true` y `/administrativos?modal=true` devuelven JSON: 201 o errores 422.

«N.º correlativo» deja de capturarse en alta y edición administrativa. Los valores históricos se conservan: el UPDATE no modifica `sequence_number`.

Archivos centrales: `fragments/vinculo-expediente.html`, `pending-tasks/form.html` y `edit.html`, `judicial-cases/form.html`, `administrative-procedures/form.html` y `edit.html`, `static/js/pendiente-expediente.js`, `static/css/expediente-modal.css`, `JudicialCaseController` y `AdministrativeProcedureController`.

Validación previa a subir a `main` (2026-09-30): compilación, empaquetado y `verify` correctos, con 83 pruebas sin fallos. Incluyen formularios, permisos, vínculos, números únicos, concurrencia, historial y conservación del correlativo histórico al editar. También pasó `node --check` en los dos archivos JavaScript afectados.

Se añadieron seis recorridos con Chromium y PostgreSQL aislado: alta judicial y administrativa en Estándar y Rendimiento, conservación del borrador al cerrar y abrir, selección y guardado del vínculo, responsables distintos, retirada del vínculo desde edición y rechazo de duplicados sin perder datos. Se actualizó el contrato que esperaba la antigua opción «Ninguno» para comprobar «Sin expediente» en el selector actual.
