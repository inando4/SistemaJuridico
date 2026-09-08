# Especificación: Trabajo en equipo — vista de carga y asignación de expedientes

**Rama**: `005-equipo-asignacion`

**Creada**: 2026-09-08

**Estado**: Borrador

**Entrada**: descripción del usuario: "Trabajo en equipo segun las secciones 5.2 y 5.3 del insumo del cliente"

**Origen en el insumo**: secciones 5.1 (permisos y atribución, ya implementada), 5.2 (vista de equipo) y 5.3 (asignación de expedientes). La 5.2 y la 5.3 provienen de la entrevista con el cliente y no estaban en la versión original del documento.

## Escenarios de usuario y pruebas *(obligatorio)*

### Historia 1 — Reasignar un expediente completo (Prioridad: P1)

Un abogado deja el área, se va de vacaciones o queda sobrecargado. La jefa abre la ficha del expediente, elige a otro abogado y confirma. El expediente entero cambia de responsable: el registro en sí y **todos** sus pendientes, los activos y los ya cumplidos. El nuevo responsable puede desde ese momento modificarlos, reprogramarlos y revertir un cumplido que el anterior había marcado.

**Por qué esta prioridad**: es la capacidad que hoy no existe de ninguna forma. Sin ella, el trabajo de una persona ausente queda inmovilizado: nadie salvo la jefa puede tocarlo, y la jefa tendría que operar registro por registro cada vez. Es además la operación con riesgo real sobre los datos, porque toca varias tablas a la vez.

**Prueba independiente**: se reasigna un expediente con pendientes activos y cumplidos, se comprueba que todos cambiaron de responsable y que el nuevo puede operar sobre ellos. Entrega valor por sí sola aunque no exista la vista de equipo.

**Escenarios de aceptación**:

1. **Dado** un expediente judicial de la abogada A con tres pendientes activos y dos cumplidos, **cuando** la jefa lo reasigna al abogado B, **entonces** el expediente y los cinco pendientes quedan a nombre de B.
2. **Dado** ese mismo expediente ya reasignado, **cuando** B entra en un pendiente que A había marcado como cumplido, **entonces** puede revertirlo indicando el motivo.
3. **Dado** ese mismo expediente ya reasignado, **cuando** A intenta modificar uno de sus antiguos pendientes, **entonces** el sistema lo rechaza: ha dejado de ser suyo.
4. **Dado** un expediente administrativo, **cuando** la jefa lo reasigna, **entonces** ocurre exactamente lo mismo que con uno judicial.
5. **Dado** un abogado que no es jefa, **cuando** intenta reasignar cualquier expediente, **entonces** el sistema lo rechaza, incluso si el expediente es suyo.
6. **Dado** un expediente reasignado, **cuando** se consulta su historial, **entonces** aparece quién era el responsable anterior, quién es el nuevo, cuándo se hizo y qué usuario lo ejecutó.
7. **Dado** un expediente reasignado, **cuando** se consulta el historial de uno de sus pendientes, **entonces** también consta ahí su cambio de responsable.
8. **Dado** un intento de reasignación que falla a mitad, **cuando** se consulta el estado, **entonces** ni el expediente ni ninguno de sus pendientes ha cambiado de responsable: o cambia todo, o no cambia nada.
9. **Dado** un pendiente de la abogada A que no cuelga de ningún expediente, **cuando** la jefa lo reasigna al abogado B, **entonces** queda a nombre de B y consta en su historial quién lo movió.
10. **Dado** un pendiente que sí cuelga de un expediente, **cuando** se intenta reasignarlo por separado, **entonces** el sistema no lo permite y explica que se mueve con su expediente.

---

### Historia 2 — Ver la carga de trabajo del equipo (Prioridad: P2)

La jefa necesita saber, antes de repartir un caso nuevo, quién tiene más encima esta semana. Abre la vista de equipo y ve a cada abogado con cuánto tiene: lo que vence esta semana, lo vencido, lo que no tiene plazo y lleva tiempo esperando. Desde cada persona puede llegar a sus pendientes, y desde un expediente puede reasignarlo.

**Por qué esta prioridad**: responde a las dos preguntas que el insumo cita literalmente —«¿en qué está trabajando cada quien?» y «¿quién está saturado esta semana?»— y es lo que da criterio a la reasignación de la Historia 1. Va después porque la reasignación funciona sin ella; ella sin la reasignación solo informa.

**Prueba independiente**: con cinco abogados y pendientes repartidos de forma desigual, la vista muestra los recuentos correctos por persona y ordena de mayor a menor carga.

**Escenarios de aceptación**:

1. **Dado** un equipo con pendientes repartidos de forma desigual, **cuando** se abre la vista de equipo, **entonces** cada abogado aparece con sus recuentos y la lista va del más cargado al menos cargado.
2. **Dado** un abogado sin ningún pendiente activo, **cuando** se abre la vista, **entonces** aparece igualmente, con ceros: quien no tiene carga es precisamente a quien se le puede asignar.
3. **Dado** un abogado dado de baja, **cuando** se abre la vista, **entonces** no aparece entre los candidatos a recibir trabajo.
4. **Dado** un recuento cualquiera de la vista, **cuando** se pulsa sobre él, **entonces** se llega al listado de esos mismos pendientes, y el listado muestra exactamente los que el número contaba.
5. **Dado** que el calendario del año no está confirmado, **cuando** se abre la vista, **entonces** los recuentos que dependen de días hábiles se sustituyen por el aviso de calendario sin revisar, en vez de mostrar un número calculado en silencio.
6. **Dado** un abogado cualquiera, **cuando** abre la vista de equipo, **entonces** puede consultarla: la lectura es compartida y no depende del rol.

---

### Historia 3 — Asignar un expediente nuevo a otro abogado (Prioridad: P3)

La jefa registra un expediente que va a llevar otra persona, o corrige la atribución de uno recién creado. Elige el responsable en el momento del alta, en lugar de quedar ella como responsable por ser quien lo escribió.

**Por qué esta prioridad**: hoy se puede lograr lo mismo dando de alta y reasignando acto seguido, así que aporta comodidad y no capacidad nueva. Se hace al final porque reutiliza la maquinaria de la Historia 1.

**Escenarios de aceptación**:

1. **Dado** que la jefa da de alta un expediente, **cuando** elige a otro abogado como responsable, **entonces** el expediente queda a nombre de esa persona y no de ella.
2. **Dado** que un abogado da de alta un expediente, **cuando** guarda, **entonces** queda a su nombre sin poder elegir otro responsable.
3. **Dado** un expediente dado de alta con responsable elegido, **cuando** se consulta su historial, **entonces** consta que se asignó a esa persona y que **no había responsable anterior** —lo que lo distingue de una reasignación—.

---

### Casos límite

- **Reasignar al mismo responsable que ya tiene**: no es un cambio efectivo. No debe generar entrada de historial (principio VII: los guardados sin cambios no generan historial).
- **Reasignar a un abogado dado de baja**: debe rechazarse. Un registro cuyo responsable no puede entrar al sistema queda sin nadie que lo trabaje.
- **Reasignar a la propia jefa**: permitido. La jefa tiene todas las capacidades de un abogado y puede llevar expedientes.
- **Dos reasignaciones simultáneas del mismo expediente**: la segunda debe detectar que el registro cambió y avisar, sin dejar los pendientes a nombre de una persona y el expediente a nombre de otra.
- **Expediente sin ningún pendiente**: se reasigna igual; no es un error.
- **Pendientes archivados y cumplidos**: viajan con el expediente. El insumo es explícito: «el expediente viaja completo, con su historial de trabajo».
- **El último abogado activo**: si solo queda una persona activa además de la jefa, la lista de destinos puede quedar vacía. Debe explicarse, no mostrar un desplegable vacío.
- **Semana a caballo entre dos años**: la carga semanal se cuenta en días hábiles y puede cruzar el 31 de diciembre; el cálculo debe cubrir ambos años o avisar.
- **Reasignar un pendiente suelto y después vincularlo a un expediente**: el pendiente pasa a seguir la regla del expediente. No hay que deshacer nada; a partir de ahí se mueve con él.
- **Un abogado que deja el área con expedientes y pendientes sueltos**: entre RF-001, RF-002 y RF-025 no queda ningún registro suyo sin vía de traspaso. Es la comprobación que cierra el caso que motivó la pregunta al cliente.

## Requisitos *(obligatorio)*

### Requisitos funcionales

**Reasignación (sección 5.3)**

- **RF-001**: El sistema DEBE permitir a la jefa cambiar el responsable de un **expediente judicial**.
- **RF-002**: El sistema DEBE permitir a la jefa cambiar el responsable de un **procedimiento administrativo**. Se enuncia aparte de RF-001 a propósito: el insumo dice «expediente» de forma genérica y el sistema tiene dos tipos; implementar solo uno dejaría la mitad del área sin la capacidad.
- **RF-003**: El sistema DEBE rechazar la reasignación a cualquier usuario que no tenga rol de jefa, comprobándolo en el servidor. Ocultar el control en la interfaz no constituye autorización.
- **RF-004**: Al reasignar, el sistema DEBE traspasar al nuevo responsable **todos** los pendientes vinculados a ese expediente, sin excluir los cumplidos ni los archivados.
- **RF-005**: El cambio del expediente y el de todos sus pendientes DEBEN aplicarse de forma atómica: si algo falla, no cambia nada. Una reasignación a medias dejaría pendientes cuyo responsable ya no tiene el expediente, y daría permiso de escritura a quien no corresponde.
- **RF-006**: El sistema DEBE registrar en el historial del expediente el responsable anterior, el nuevo, el momento del cambio y qué usuario lo ejecutó.
- **RF-007**: El sistema DEBE registrar el cambio de responsable **en el historial de cada pendiente traspasado**, no solo en el del expediente. Se elige la entrada por registro y no un resumen en el expediente porque quien consulta un pendiente tiene que poder saber por qué cambió de manos sin adivinar de qué expediente colgaba.
- **RF-008**: Tras la reasignación, el nuevo responsable DEBE poder modificar, reprogramar, cancelar y **revertir un cumplido** de esos pendientes, incluidos los que marcó el responsable anterior.
- **RF-009**: Tras la reasignación, el responsable anterior DEBE perder toda capacidad de modificación sobre ese expediente y sus pendientes, conservando la de consulta.
- **RF-010**: El sistema DEBE rechazar la reasignación a un usuario inactivo o dado de baja.
- **RF-011**: Reasignar al responsable que ya consta NO DEBE generar entrada de historial.

**Asignación en el alta (sección 5.3)**

- **RF-012**: El sistema DEBE permitir a la jefa elegir el responsable al dar de alta un expediente judicial o un procedimiento administrativo.
- **RF-013**: Un abogado que da de alta un expediente DEBE quedar como responsable, sin opción de elegir otro.
- **RF-014**: El historial de una asignación en el alta DEBE distinguirse del de una reasignación, conservando la **ausencia de responsable anterior**.

**Vista de equipo (sección 5.2)**

- **RF-015**: El sistema DEBE ofrecer una vista que muestre, por cada abogado activo, la carga de trabajo de la semana en curso.
- **RF-016**: La vista DEBE mostrar los recuentos por persona sin declarar a nadie «saturado». El sistema aporta los números; quién está saturado lo decide la jefa. Un umbral fijo sería una regla de negocio que el cliente no ha dado.
- **RF-017**: La vista DEBE ordenar a los abogados de mayor a menor carga, para que el más cargado se vea primero sin buscarlo.
- **RF-018**: La vista DEBE incluir a los abogados sin carga, con sus ceros: son los candidatos naturales a recibir trabajo.
- **RF-019**: La vista NO DEBE incluir usuarios inactivos entre los posibles destinatarios de trabajo.
- **RF-020**: Cada recuento de la vista DEBE llevar al listado de esos mismos pendientes, y ese listado DEBE contener exactamente los que el recuento contaba.
- **RF-021**: La vista DEBE ser consultable por cualquier usuario del sistema, con independencia de su rol. La lectura es compartida (sección 5.1).
- **RF-022**: Desde la vista de equipo DEBE poder llegarse a la reasignación de un expediente.
- **RF-023**: Los recuentos que dependan de días hábiles DEBEN calcularse con la función centralizada del sistema, y DEBEN sustituirse por el aviso de calendario sin revisar cuando el año no esté confirmado, en vez de mostrar un número calculado en silencio.
- **RF-024**: Ningún recuento de carga DEBE almacenarse; todos se calculan en el momento de la consulta.

**Pendientes sin expediente**

> **Decisión del cliente (2026-09-08)**: la jefa sí puede reasignar un pendiente suelto de forma individual. Sin esto, los pendientes que no cuelgan de ningún expediente quedarían inmovilizados cuando su responsable deja el área, porque las secciones 5.2 y 5.3 solo describen mover expedientes.

- **RF-025**: El sistema DEBE permitir a la jefa cambiar el responsable de un pendiente **que no esté vinculado a ningún expediente**, de forma individual.
- **RF-026**: El sistema DEBE registrar ese cambio en el historial del pendiente, con el responsable anterior, el nuevo, el momento y quién lo ejecutó, igual que RF-006 para los expedientes.
- **RF-027**: Un pendiente **sí vinculado** a un expediente NO DEBE poder reasignarse por separado: cambia de responsable únicamente cuando se reasigna su expediente. El insumo es explícito en que «el expediente viaja completo»; permitir la separación dejaría expedientes cuyo responsable visible no predice quién puede editar sus tareas, que es justo lo que RF-004 evita. Si el área necesita delegar una tarea suelta de un expediente, la vía es reasignar el expediente o registrar un pendiente independiente.
- **RF-028**: La reasignación individual DEBE estar sujeta a las mismas restricciones que la de expedientes: solo la jefa (RF-003), nunca a un usuario inactivo (RF-010) y sin historial si el responsable no cambia (RF-011).

### Entidades clave

- **Expediente judicial**: ya existe. Tiene un usuario responsable, que a partir de ahora puede cambiar.
- **Procedimiento administrativo**: ya existe. Mismo caso.
- **Pendiente**: ya existe. Su responsable pasa a cambiar en bloque cuando se reasigna el expediente del que cuelga.
- **Usuario**: ya existe, con rol abogado o jefa y marca de activo. La vista de equipo lo usa como eje de agrupación; solo los activos reciben trabajo.
- **Entrada de historial**: ya existe, y ya distingue el autor del cambio del responsable del registro. La reasignación añade un tipo de cambio nuevo, que conserva responsable anterior y nuevo.
- **Carga de trabajo**: **no es una entidad almacenada**. Es el resultado de contar pendientes por responsable en el momento de mirar. No tiene tabla ni columna.

## Criterios de éxito *(obligatorio)*

### Resultados medibles

- **CE-001**: La jefa puede reasignar un expediente completo en menos de 30 segundos desde su ficha, sin pasar por ningún listado intermedio.
- **CE-002**: Reasignar un expediente con 50 pendientes deja los 51 registros a nombre del nuevo responsable, sin excepción y sin que ninguno quede a nombre del anterior.
- **CE-003**: Una reasignación interrumpida no deja ningún registro cambiado: la comprobación posterior encuentra el mismo responsable en el expediente y en los 50 pendientes.
- **CE-004**: El historial permite reconstruir, para cualquier expediente, la cadena completa de responsables que ha tenido y quién ejecutó cada cambio.
- **CE-005**: La vista de equipo se abre con un **número fijo de consultas a la base de datos, independiente del tamaño del equipo**: cinco abogados y quince producen el mismo número de consultas.
- **CE-006**: La vista de equipo se muestra en menos de 1,5 segundos en el equipo lento de referencia, con 5 abogados y 5.000 pendientes registrados.
- **CE-007**: La jefa identifica al abogado más cargado de la semana en la primera pantalla, sin desplazarse ni ordenar manualmente.
- **CE-008**: Un abogado que intenta reasignar un expediente recibe un rechazo del servidor, tanto desde la interfaz como enviando la petición directamente.
- **CE-009**: Ningún recuento de carga de trabajo queda almacenado en la base de datos: una revisión del esquema no encuentra columnas ni tablas de agregados.
- **CE-010**: Dado un abogado con expedientes judiciales, procedimientos administrativos y pendientes sueltos, la jefa puede traspasar **todos** sus registros a otras personas sin que quede ninguno sin vía de reasignación.

## Supuestos

- **«La semana» son los días hábiles de la semana en curso**, calculados con la misma función centralizada que ya usa el resto del sistema (principio VI: no se reimplementa el cálculo por pantalla). El insumo dice «la carga de trabajo de la semana» sin definirla, y esta es la lectura coherente con un área que cuenta plazos en días hábiles.
- **La carga se mide sobre pendientes activos y no cumplidos**, agrupados por responsable. Un pendiente cumplido ya no es carga.
- **«Expediente» abarca tanto los judiciales como los administrativos.** El insumo usa la palabra de forma genérica en la 5.3, y la 5.1 atribuye responsable a ambos.
- **No se define un umbral de saturación.** El insumo pide «identificar quién está saturado», no que el sistema lo declare. Un número por persona, ordenado, cumple lo pedido sin inventar una regla que el cliente no dio.
- **Los permisos de la sección 5.1 ya están implementados** (features 001–004): lectura para todos, escritura sobre lo propio, la jefa sobre todo, y el historial ya distingue autor de responsable. Esta feature no los redefine, los usa.
- **La reasignación no altera fechas, estados ni contenido** de los registros traspasados. Solo cambia de quién son.
- **La vista de equipo es una pantalla nueva**, no una pestaña del panel existente. El panel responde «¿qué tengo que hacer hoy?» en primera persona; la vista de equipo responde «¿cómo está repartido el trabajo?» y son preguntas distintas.
- **No entra en esta feature** la reasignación masiva de todos los expedientes de un abogado a otro de una sola vez. Si el cliente la pide, se abordará por separado; la operación por expediente es la que el insumo describe.
