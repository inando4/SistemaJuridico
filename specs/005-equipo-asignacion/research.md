# Fase 0 — Investigación y decisiones

Todo lo de aquí se comprobó contra el código y el esquema actuales, no de memoria.

---

## Decisión 1 — Esta feature no necesita migración

**Decisión**: no se crea ninguna V10.

**Fundamento**: se comprobaron las dos restricciones que podrían obligar a ello.

- `audit_event.action` es `text NOT NULL` **sin `CHECK`** (`V7__audit.sql:7`). La acción nueva `REASSIGN` no requiere nada.
- El `CHECK` de `entity_type` se sustituyó en `V9__pending_task.sql:112` y ya admite `JUDICIAL_CASE`, `ADMINISTRATIVE_PROCEDURE` y `PENDING_TASK`, que son los tres tipos que esta feature audita.
- `owner_id` ya existe y es `NOT NULL` en las tres tablas (`V4:9`, `V8:33`, `V9:59`). Reasignar es un `UPDATE` de una columna que ya está.
- Los índices `judicial_case_responsable`, `administrative_procedure_responsable` y `pending_task_responsable` ya cubren `(owner_id, id)`.

**Alternativas descartadas**:

- *Un índice nuevo para agrupar por responsable con filtros de fecha*: la vista de equipo es un solo recorrido de `pending_task` con `count(*) FILTER`, la misma forma que `DashboardRepository` mide en 13 ms sobre 5.000 filas. Un índice para 5.000 filas y cinco personas es peso muerto que hay que mantener. Si la medición del presupuesto lo desmiente, se añade entonces y con el número delante.
- *Aprovechar el viaje para el `REVOKE` de `flyway_schema_history`*: ataría un arreglo de permisos a un cambio funcional sin relación. Se anota para la primera feature que sí necesite migración.

---

## Decisión 2 — Qué es «la semana» y cómo se ordena la carga

**Decisión**: la semana es **de lunes a domingo de la semana en curso**, calculada con aritmética de calendario (`hoy.with(DayOfWeek.MONDAY)`), no con días hábiles. La lista se ordena por **vencidos + vence esta semana**, descendente, y esa suma se muestra en pantalla.

**Fundamento**: el insumo pide «la carga de trabajo de la semana» y «quién está saturado esta semana» sin definir ninguna de las dos cosas.

- *Por qué la semana natural y no «los próximos N días hábiles»*: la jefa razona en semanas de calendario, y un rango que se mueve cada día haría que el mismo pendiente entrara y saliera del recuento sin que nada cambiara. El lunes es el inicio de semana habitual en el Perú.
- *Por qué esa suma para ordenar*: lo vencido es trabajo que tiene que ocurrir **esta semana** tanto como lo que vence en ella. Ordenar solo por lo que vence dejaría a alguien con quince cosas vencidas por debajo de otro con dos de mañana.
- *Por qué la suma se ve*: RF-016 dice que el sistema no declara a nadie saturado, solo da números. Una lista ordenada por una cifra invisible obliga a creerse el orden y contradice justo eso.

**Alternativas descartadas**:

- *Un umbral de saturación (p. ej. «más de 10 = saturado»)*: sería una regla de negocio que el cliente no dio, y un número inventado en rojo pesa más en la decisión que los datos reales.
- *Ordenar por total de pendientes activos*: mide «cuánto tiene en total», no «cuánto tiene esta semana», que es lo que el insumo y CE-007 preguntan.
- *Ponderar por prioridad*: exige decidir cuánto vale una prioridad Alta frente a dos Medias. El cliente no lo ha dicho y la vista no lo necesita para cumplir su función.

---

## Decisión 3 — Qué recuentos se degradan sin calendario, y cuáles no

**Decisión**: solo el recuento **«sin plazo, antiguos»** depende del calendario y se sustituye por el aviso cuando el año no está confirmado. Los recuentos de vencidos y de la semana **siguen mostrándose**.

**Fundamento**: es la distinción que el sistema ya hace y que `AlertLevelsIT.sinCalendarioSalenLosQueNoDependenDeEl` deja fijada. Comparar `deadline < hoy` o `deadline` contra un lunes y un domingo es comparar fechas: no interviene ningún día hábil. En cambio, «lleva más de quince días hábiles esperando» sale de `DeadlineEvaluator.restarDiasHabiles`, que devuelve vacío si el intervalo no está cubierto.

Ocultar la vista entera por falta de calendario sería peor que el problema: dejaría a la jefa sin poder repartir trabajo por un dato que solo afecta a una de las cuatro columnas.

**Alternativas descartadas**:

- *Mostrar cero cuando no se puede calcular*: un cero es una afirmación —«nadie lleva mucho esperando»— y sería falsa. El principio VI exige avisar, no calcular en silencio.
- *Bloquear la pantalla entera*: desproporcionado, según lo dicho arriba.

---

## Decisión 4 — Cómo se ejecuta la reasignación

**Decisión**: tres sentencias dentro de una transacción, en este orden:

1. `SELECT id, owner_id FROM pending_task WHERE <vínculo> AND owner_id <> :nuevo` — la foto de quién es responsable de qué **antes** de tocar nada.
2. `UPDATE pending_task SET owner_id = :nuevo, updated_at = :ahora, version = version + 1 WHERE <vínculo> AND owner_id <> :nuevo`, más el `UPDATE` del expediente con su comprobación de versión.
3. Un `INSERT` en bloque en `audit_event`, una fila por registro movido, con el responsable anterior que dio el paso 1.

**Fundamento**: el número de sentencias no depende de cuántos pendientes cuelguen, que es lo que exige el presupuesto. La foto previa es necesaria porque PostgreSQL 17 no tiene `RETURNING OLD.*` (llegó en la 18), y hace falta el responsable **real** de cada pendiente, que no siempre es el del expediente (ver decisión 5).

**Alternativas descartadas**:

- *Una sola sentencia con CTE (`WITH antes AS (...), movidos AS (UPDATE ... RETURNING ...) INSERT ...`)*: cabe y es correcta, pero no ahorra nada medible —la reasignación se ejecuta unas pocas veces al mes con menos de 50 filas— y a cambio cuesta legibilidad y abre una segunda vía de escritura en `audit_event` en SQL crudo, saltándose `AuditRecorder`. El `FOR UPDATE` que necesitaría además es redundante: el `UPDATE` toma sus propios bloqueos de fila.
- *Un `UPDATE` y una llamada a `AuditRecorder` por pendiente*: N+1 escrituras. Con 50 pendientes son 100 sentencias donde bastan 3.
- *Traspasar solo el expediente y dejar los pendientes*: lo prohíbe el insumo («el expediente viaja completo») y dejaría a otra persona con capacidad de escritura sobre el trabajo del expediente.

---

## Decisión 5 — Los pendientes vinculados pueden ser de otra persona (hallazgo)

**Hallazgo**: `PendingTaskService.crear(form, responsable)` deja el pendiente a nombre de quien lo registra, y `PendingTaskValidator` solo rechaza el vínculo doble. **Nada impide que una abogada registre un pendiente colgado del expediente de otro y quede a su nombre.** El sistema nunca ha sostenido el invariante «pendiente vinculado ⇒ del responsable del expediente».

**Consecuencia en la especificación**: obligó a enmendarla antes de planificar (RF-004a y RF-004b). Reasignar un expediente puede retirarle a un tercero un pendiente suyo, y eso no puede pasar en silencio.

**Decisión**: se traspasan **todos** los pendientes del expediente, sea quien sea su responsable actual, y antes de confirmar se dice cuántos son y a quién pertenecen los que no son del responsable saliente. Cada entrada de historial conserva el responsable anterior **real**, no el del expediente.

**Alternativas descartadas**:

- *Traspasar solo los del responsable saliente*: dejaría pendientes de un tercero colgando de un expediente ajeno, que es la situación que la sección 5.3 quiere terminar, y recrearía el trabajo inmovilizado si esa persona deja el área.
- *Prohibir registrar un pendiente sobre el expediente de otro*: es una restricción nueva que el cliente no ha pedido, y no arregla las filas que ya existan.

---

## Decisión 6 — Quién aparece en la vista de equipo

**Decisión**: se filtra por **cuenta activa**, no por rol. La jefa aparece en la lista junto a los abogados.

**Fundamento**: la constitución (principio II) le da todas las capacidades de un abogado más las suyas, y la propia especificación admite reasignarle un expediente. Filtrar por `role = 'LAWYER'` la borraría de una vista cuyo propósito es ver cómo está repartido el trabajo, y ocultaría carga real del área.

Se anota aquí porque es el tipo de filtro que se reintroduce sin pensar al escribir la consulta.

---

## Decisión 7 — Reasignar sube la versión, y eso es lo buscado

**Decisión**: el `UPDATE` incrementa `version` en cada registro movido. Quien estuviera editando uno de esos pendientes recibirá el aviso de conflicto de edición al guardar.

**Fundamento**: es el comportamiento correcto —su formulario se cargó cuando el registro era de otra persona— y encaja con el bloqueo optimista que ya usa todo el sistema (`ConcurrentEditIT`). Se deja escrito para que tenga prueba propia en vez de descubrirse como sorpresa.

---

## Decisión 8 — La comprobación de «sin cambios» es por registro

**Decisión**: no se escribe historial de un registro cuyo responsable ya es el destino, y la comprobación se hace **registro a registro**, no una vez para todo el expediente.

**Fundamento**: el principio VII prohíbe generar historial en guardados sin cambios. Pero un expediente que ya es de B puede tener pendientes de A: reasignarlo a B **sí** es un cambio efectivo sobre esos pendientes, aunque no lo sea sobre el expediente. Una comprobación global no vería la diferencia y no movería nada.

El `AND owner_id <> :nuevo` de la decisión 4 lo resuelve para los pendientes; el expediente lleva su propia comparación.
