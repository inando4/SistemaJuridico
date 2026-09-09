---

description: "Tareas de la 008 — pendientes relacionados en la ficha del expediente"
---

# Tareas: Pendientes relacionados en la ficha del expediente

**Entrada**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/pantallas.md](contracts/pantallas.md)

**Pruebas**: incluidas. El principio IV de la constitución hace del presupuesto medido una condición de aceptación, no un extra.

**Sin migración**: las columnas del vínculo existen desde la V9.

## Formato: `[ID] [P?] [Historia] Descripción`

---

## Fase 1: Preparación

- [X] T001 Comprobar que la rama `008-pendientes-del-expediente` parte de `main` con `./mvnw verify` en verde. Cualquier fallo previo se arregla antes de empezar, para que un rojo posterior sea atribuible a esta feature

---

## Fase 2: Fundacional (bloquea a las tres historias)

**Por qué bloquea**: las tres historias comparten un mismo camino de consulta. Si el filtro, la visibilidad y el orden no existen antes, cada historia se inventaría el suyo y volveríamos al problema de la 004 —dos condiciones que se separan— que este plan elimina por construcción.

### Pruebas

- [X] T002 [P] `src/test/java/.../web/PendingTaskFilterContractTest.java`: `GET /pendientes?judicialCaseId=X` devuelve solo los de X; `?administrativeProcedureId=Y` solo los de Y; **los dos a la vez se rechazan** como filtro inválido; un identificador inexistente da lista vacía con aviso y **no** un error de sistema (RF-008, RF-011)
- [X] T003 [P] `src/test/java/.../web/PendingTaskVisibilityContractTest.java`: `visibility=notArchived` devuelve cumplidos **y no** archivados; `visibility=active` sigue excluyendo cumplidos; `visibility=all` sigue trayéndolo todo. Los tres en la misma prueba, porque lo que importa es que los conjuntos sean distintos entre sí (R3)
- [X] T004 [P] `src/test/java/.../integration/PendingTaskOrderIT.java`: con `sort=pendingFirst`, los que tienen `completed_at` nulo salen todos antes que los cumplidos, y dentro de cada grupo por fecha límite ascendente con nulos al final. Comprobar además que **dos pendientes con la misma fecha límite no se repiten ni se saltan** al paginar, que es para lo que está el desempate por `t.id` (R4)

### Implementación

- [X] T005 `src/main/java/.../pendingtask/PendingTaskFilters.java`: añadir los componentes `judicialCaseId` y `administrativeProcedureId` (record de 13 → 15). Actualizar `porDefecto()` con dos `null`. En `valido()`, rechazar que lleguen **los dos a la vez**: un pendiente no puede colgar de ambos y la combinación devolvería vacío sin explicar por qué. Añadir `notArchived` a `VISIBILIDADES` y `pendingFirst` a `ORDENES`
- [X] T006 `src/main/java/.../pendingtask/PendingTaskFilters.java`: en `comoQuery(int)`, dos `anadir(sb, "judicialCaseId", ...)` y `anadir(sb, "administrativeProcedureId", ...)`. **Es uno de los dos portadores del filtro** (R7): sin esto, el filtro se pierde al paginar y al ordenar
- [X] T007 `src/main/java/.../pendingtask/PendingTaskRepository.java`: dos `anadirIgual(condiciones, params, "t.judicial_case_id", "judicialCaseId", filtros.judicialCaseId())` y su equivalente administrativo, junto a los de `ownerId`
- [X] T008 `src/main/java/.../pendingtask/PendingTaskRepository.java`: en el `switch` de `filtros.visibility()`, el caso `"notArchived" -> condiciones.add("t.active = true")`. **Sin** la cláusula de `completed_at`: esa es justamente la diferencia con `active` (R3)
- [X] T009 `src/main/java/.../pendingtask/PendingTaskRepository.java`: en `orden()`, el caso `"pendingFirst" -> " ORDER BY (t.completed_at IS NULL) DESC, t.deadline ASC NULLS LAST, t.id ASC"`. El `sentido` no se aplica aquí: el orden es fijo por definición, y dejarlo invertible daría un «cumplidos primero» que nadie pidió
- [X] T010 `src/main/java/.../pendingtask/PendingTaskController.java`: dos `@RequestParam(required = false) UUID judicialCaseId` / `administrativeProcedureId` en el listado, pasados al único `new PendingTaskFilters(...)` del controlador

**Punto de control**: `./mvnw verify` en verde. El listado acepta el filtro nuevo y lo conserva al paginar. Nada visible ha cambiado todavía para el usuario.

---

## Fase 3: Historia 1 — Ver los pendientes de un expediente (P1) 🎯 MVP

**Objetivo**: las dos fichas muestran sus pendientes vinculados.

**Prueba independiente**: abrir la ficha de un expediente con pendientes y verlos, con el nombre de su responsable, sin salir de la página.

### Pruebas

- [X] T011 [P] [US1] `src/test/java/.../web/ExpedientePendientesContractTest.java`: la ficha judicial y la administrativa muestran el bloque con título, **responsable**, estado, prioridad y fecha límite de cada fila, y enlace a `/pendientes/{id}` (RF-003). **Incluir un pendiente de otro abogado** y comprobar que aparece con su nombre: una implementación que filtrara por `owner_id = actual` pasaría con datos de un solo usuario (RF-004)
- [X] T012 [P] [US1] En la misma prueba: un expediente **sin** pendientes muestra el bloque con mensaje de lista vacía, no un hueco (RF-005); un cumplido **sí** aparece y un archivado **no** (RF-002, RF-007)
- [X] T013 [P] [US1] `src/test/java/.../integration/ExpedienteFichaQueryBudgetIT.java` con `ContadorDeConsultas`: la ficha judicial cuesta **exactamente una consulta más** que antes de esta feature, y **el mismo número con 2 vínculos que con 50**. Idéntico para la administrativa. La invariancia es lo que delata una consulta por fila (CE-004, CE-005)
- [X] T014 [P] [US1] `src/test/java/.../integration/FichaYListadoCoincidenIT.java`: para un mismo expediente, lo que devuelve la ficha y lo que devuelve `/pendientes?judicialCaseId=X&visibility=notArchived&sort=pendingFirst` es **el mismo conjunto en el mismo orden** (CE-003). Es la prueba que detectaría que los dos caminos se han separado

### Implementación

- [X] T015 [US1] `src/main/resources/templates/fragments/pendientes-relacionados.html`: fragmento compartido por las dos fichas. Recibe la lista, la marca de «hay más» y los dos enlaces. **`th:if` y `th:with` nunca en el mismo elemento** — precedencia 300 contra 400, la condición vería la variable sin definir; es el fallo que costó la rejilla del mes en la 006, que compilaba limpio y pintaba cero (R9)
- [X] T016 [US1] `src/main/java/.../judicialcase/JudicialCaseController.java`, método `ficha`: construir los filtros con `judicialCaseId` del expediente, `visibility=notArchived` y `sort=pendingFirst`, y llamar a `pendientes.listar(filtros, Paging.of(0), hoy)`. **Sin método de repositorio propio**: es el mismo camino que el listado (D4). Poner en el modelo la lista recortada a `Paging.TAMANO`, `hayMasPendientes` a partir del sondeo, y los dos enlaces
- [X] T017 [US1] `src/main/java/.../administrativeprocedure/AdministrativeProcedureController.java`, método `ficha`: lo mismo con `administrativeProcedureId`
- [X] T018 [US1] `src/main/resources/templates/judicial-cases/detail.html`: insertar el fragmento como sección «Pendientes relacionados», después del seguimiento, según el orden de la sección 28 del insumo
- [X] T019 [US1] `src/main/resources/templates/administrative-procedures/detail.html`: insertar el fragmento **sustituyendo el comentario de las líneas 66-67**, que desde la feature 002 anuncia que esta sección llegaría con la funcionalidad de pendientes. Borrar el comentario: deja de ser cierto
- [X] T020 [US1] Los plazos de las filas se calculan con el mismo servicio central que usan las demás pantallas (`poblarPlazos` o equivalente), **nunca con aritmética de fechas en la plantilla** (principio VI)

**Punto de control**: las dos fichas muestran sus pendientes. La historia 1 es entregable por sí sola.

---

## Fase 4: Historia 2 — Crear un pendiente ya vinculado (P2)

**Objetivo**: desde la ficha se llega al formulario de alta con el expediente puesto.

**Prueba independiente**: pulsar «+ Crear nuevo pendiente relacionado» y encontrar el expediente ya elegido en el desplegable.

### Pruebas

- [X] T021 [P] [US2] `src/test/java/.../web/AltaVinculadaContractTest.java`: `GET /pendientes/nuevo?judicialCaseId=X` trae X pre-seleccionado (RF-012) y el desplegable **sigue permitiendo cambiarlo o vaciarlo** (RF-013); los dos identificadores a la vez se rechazan (RF-014)
- [X] T022 [P] [US2] En la misma prueba, **el caso del archivado**: un expediente con `active = false` llega igualmente como opción del desplegable. Sin esto, `th:selected` no encaja con nada, el `<select>` envía vacío y **el vínculo se pierde al guardar sin dar ningún error** (R5)
- [X] T023 [P] [US2] `src/test/java/.../web/AltaVinculadaContractTest.java`: `GET /pendientes/nuevo?judicialCaseId=<uuid inexistente>` se rechaza, y `POST /pendientes` con ese identificador devuelve **error de validación en el formulario**, no un 500 desde la clave foránea, que es lo que ocurre hoy (RF-016, R6)
- [X] T024 [P] [US2] En la misma prueba: llegando con el vínculo puesto y siendo el expediente **de otra persona**, la pantalla nombra a su responsable antes de guardar (RF-015)
- [X] T025 [P] [US2] `src/test/java/.../integration/PendingTaskAuditIT.java` (o el existente de auditoría): el alta desde la ficha deja **el mismo rastro** que el alta desde el listado (RF-018)

### Implementación

- [X] T026 [US2] `src/main/java/.../pendingtask/ExpedienteVinculado.java`: record con `id`, `numero`, `responsableId`, `responsableNombre`, `activo`, y el componente que lo resuelve con **una sola consulta**. Que la fila exista es la comprobación de RF-016; su número alimenta la opción del desplegable y el aviso del listado; su responsable alimenta el aviso de RF-015. Tres preguntas, una consulta (D5, D6, D9)
- [X] T027 [US2] `src/main/java/.../pendingtask/PendingTaskCatalogs.java`: `poblar` acepta el vínculo pre-seleccionado y **añade su opción si el desplegable no la trae** —por archivado o por caer fuera del `LIMIT 500`—. No quitar el `WHERE active = true` ni subir el límite: lo uno ofrecería archivados a cualquiera y lo otro solo aplaza el problema
- [X] T028 [US2] `src/main/java/.../pendingtask/PendingTaskValidator.java`: comprobar que el vínculo **existe**, además del `vinculoDoble()` que ya comprueba. Del lado del POST, porque un formulario hecho a mano no pasa por el desplegable
- [X] T029 [US2] `src/main/java/.../pendingtask/PendingTaskController.java`, `formularioNuevo`: aceptar `judicialCaseId` / `administrativeProcedureId`, resolver el expediente, rechazar si no existe, y pasar el formulario con el vínculo puesto en lugar de `PendingTaskForm.nuevo()` a secas
- [X] T030 [US2] `src/main/resources/templates/pending-tasks/form.html`: junto a «El responsable sera usted» (línea 88), el aviso que nombra al responsable del expediente **solo** cuando se llega con vínculo y es de otra persona (RF-015)
- [X] T031 [US2] Añadir «+ Crear nuevo pendiente relacionado» al fragmento de T015, con el identificador del expediente en la URL

**Punto de control**: se crea un pendiente vinculado sin buscar el expediente a mano, y el vínculo no se pierde en ningún caso.

---

## Fase 5: Historia 3 — Ver todos en el listado filtrado (P3)

**Objetivo**: lo que no cabe en la ficha se alcanza, con el expediente ya filtrado.

**Prueba independiente**: un expediente con más de 25 pendientes avisa de que hay más y su enlace lleva al listado filtrado.

### Pruebas

- [X] T032 [P] [US3] `src/test/java/.../web/VerTodosContractTest.java`: con 26 pendientes vinculados, la ficha muestra 25 y avisa; con 3, **no** avisa (RF-006). El aviso sale del sondeo de una fila extra, sin `count(*)` (principio V)
- [X] T033 [P] [US3] En la misma prueba: con el filtro puesto, el listado **nombra el expediente** y ofrece quitar el filtro (RF-010)
- [X] T034 [P] [US3] `src/test/java/.../web/PendingTaskFilterContractTest.java`: el filtro **sobrevive a los dos caminos** — paginar y ordenar (cadena de consulta) y **aplicar otro filtro desde el formulario** (campo oculto). El segundo es el que se rompe si se olvida el `hidden`, y es el caso de RF-009 (R7)
- [X] T035 [P] [US3] `src/test/java/.../integration/PendingTaskQueryBudgetIT.java`: añadir el caso **filtrado**. `/pendientes` sin filtro no cambia de coste; con filtro sube exactamente en una consulta, la del nombre del expediente. Sin este caso el parámetro nuevo no se mediría nunca (D8)

### Implementación

- [X] T036 [US3] `src/main/java/.../pendingtask/PendingTaskController.java`, listado: cuando el filtro por expediente viene puesto, resolver su número con el componente de T026 y pasarlo al modelo. **Solo cuando viene puesto**: sin filtro, el coste no cambia
- [X] T037 [US3] `src/main/resources/templates/pending-tasks/list.html`: `<input type="hidden" name="judicialCaseId" th:value="${filtros.judicialCaseId}">` y su par administrativo dentro del `<form method="get">`. **El formulario GET descarta todo lo que no sean sus campos** — es el portador que falta (R7)
- [X] T038 [US3] `src/main/resources/templates/pending-tasks/list.html`: aviso «Pendientes del expediente NNN» con enlace para quitar el filtro, visible solo cuando hay filtro (RF-010)
- [X] T039 [US3] `src/main/resources/templates/pending-tasks/list.html`: la opción «Visibles, incluidos los cumplidos» en el `<select>` de visibilidad y «Por hacer primero» en el de orden, para que los valores nuevos sean alcanzables desde la interfaz y no solo por URL
- [X] T040 [US3] Añadir «Ver todos» al fragmento de T015, visible solo cuando el sondeo encontró más

**Punto de control**: ningún pendiente vinculado queda inalcanzable desde su expediente (CE-007).

---

## Fase 6: Acabado

- [ ] T041 [P] `src/test/java/.../acceptance/AccessibilityAcceptanceTest.java`: el bloque nuevo se recorre con el teclado y sus encabezados encajan en la jerarquía de la ficha
- [ ] T042 [P] `src/test/java/.../acceptance/InterfazEnEspanolTest.java`: los textos nuevos —incluidas las dos etiquetas de los desplegables— están en español con tildes (principio I)
- [ ] T043 `src/test/java/.../acceptance/RecorridoExpedientePendientesTest.java` con Playwright: el recorrido de [quickstart.md](quickstart.md) de punta a punta, con los pasos 7, 9.3 y 10 —los tres fallos silenciosos— como afirmaciones explícitas. Localizadores por texto de botón o acotados a un `li`, no `form[action$=...]`: en la 006 eso violó el modo estricto dos veces
- [ ] T044 `src/test/java/.../acceptance/PendingTaskPerformanceTest.java`: una ficha con 50 vínculos por debajo de 500 ms (CE-006)
- [ ] T045 Anotar en [plan.md](plan.md) las cifras **medidas** de consultas, sustituyendo los techos provisionales de la tabla de presupuesto, como en las features 004, 006 y 007
- [ ] T046 Ejecutar `./mvnw verify` completo y comprobar que ninguna prueba anterior a esta feature se ha roto. Prestar atención a `PendingTaskListContractTest`, que fija los valores admitidos de los filtros y **tiene que reflejar los dos valores nuevos**

---

## Dependencias

```text
Fase 1 (T001)
  └─ Fase 2 (T002-T010)  ← bloquea a las tres historias
       ├─ Fase 3 US1 (T011-T020)  🎯 MVP, entregable sola
       ├─ Fase 4 US2 (T021-T031)  necesita el fragmento de T015
       └─ Fase 5 US3 (T032-T040)  necesita el fragmento de T015
            └─ Fase 6 (T041-T046)
```

**Por qué la fase 2 bloquea**: las tres historias pasan por el mismo `listar`. El filtro, la visibilidad y el orden tienen que existir antes de que nadie los use, o cada historia se inventará su propia consulta.

**US2 y US3 dependen de T015**, no entre sí: ambas añaden un enlace al mismo fragmento. Hechas en paralelo, ese archivo es el único punto de conflicto.

### Se pueden hacer a la vez

- T002, T003, T004 (pruebas de la fase 2, archivos distintos)
- T011, T012, T013, T014 (pruebas de US1)
- T021 a T025 (pruebas de US2)
- T032 a T035 (pruebas de US3)
- T016 y T017 (los dos controladores de ficha, archivos distintos)
- T041 y T042 (acabado)

---

## Estrategia

**MVP**: fases 1 a 3. Con eso, las dos fichas ya responden a lo que piden las secciones 28 y 30 del insumo, y es desplegable.

**Después**: US2 aporta el ahorro de trabajo manual; US3 cierra el hueco de que algo vinculado quedara sin camino.

---

## Notas

- Sin migración. Si alguna tarea parece pedir una, es señal de que se desvió del plan
- El principio V prohíbe persistir derivados: ni recuentos, ni «hay más», ni estados de plazo
- La ficha **no** obtiene su propia consulta. Si aparece un método de repositorio nuevo para ella, se perdió lo que este plan intentaba evitar
- Confirmar tras cada tarea; commit por grupo lógico
