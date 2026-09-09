---

description: "Tareas de la 009 — cancelar registros y los filtros que faltan"
---

# Tareas: Cancelar registros y los filtros que faltan

**Entrada**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/pantallas.md](contracts/pantallas.md)

**Pruebas**: incluidas. El principio IV hace del presupuesto medido una condición de aceptación.

**Sin migración**: la marca de visibilidad existe en las tres tablas y `audit_event.action` es texto libre.

## Formato: `[ID] [P?] [Historia] Descripción`

---

## Fase 1: Preparación

- [X] T001 Comprobar que la rama `009-cancelar-y-filtros` parte de `main` con `./mvnw verify` en verde, para que cualquier rojo posterior sea atribuible a esta funcionalidad

---

## Fase 2: Fundacional (bloquea a US2 y US3)

**Por qué bloquea**: US2 necesita saber volver al listado y US3 necesita las opciones de los desplegables. Sin estas dos piezas, cada historia se inventaría la suya. **US1 no depende de esta fase** y puede empezar en paralelo.

### Pruebas

- [X] T002 [P] `src/test/java/.../web/VueltaAlListadoContractTest.java`: `POST` con `filtros=?sort=title&page=2` redirige a `/pendientes?sort=title&page=2`; **sin** el parámetro redirige a `/pendientes` a secas; una cadena que no encaja con el patrón permitido **se ignora** y devuelve al listado sin filtros, nunca un error (D2, contrato §POST cancelar 8-9)
- [X] T003 [P] En la misma prueba, el caso de seguridad: `filtros=https://otro-sitio.example` **no** saca al usuario de la aplicación. El destino se escribe fijo en el código, así que la cadena no puede desviarlo
- [X] T004 [P] `src/test/java/.../integration/OpcionesDeFiltroIT.java`: pedir las opciones de las tres pantallas cuesta **dos consultas cada una** —una para todos los catálogos, una para las cuentas—, no una por desplegable (D3). Medido con `ContadorDeConsultas`
- [X] T005 [P] En la misma prueba: `habilitadosDeVarios` devuelve **lo mismo** que llamar a `habilitados` catálogo por catálogo. Es lo que impide que el `UNION` y el criterio de `habilitados()` se separen
- [X] T006 [P] En la misma prueba: las opciones de responsable incluyen **una cuenta desactivada** que tiene pendientes, marcada como tal. Con `DestinosDeAsignacion.activos` esta prueba fallaría, que es justo por lo que no se reutiliza (D4, RF-021)

### Implementación

- [X] T007 `src/main/java/.../catalog/CatalogRepository.java`: `habilitadosDeVarios(List<CatalogDefinition>)` que compone con `UNION ALL` **el mismo fragmento** que usa `habilitados` —extraerlo a una constante o método privado y que ambos lo usen—, con una columna que diga de qué catálogo es cada fila. Una definición del criterio, dos formas de pedirla
- [X] T008 `src/main/java/.../shared/OpcionesDeFiltro.java`: componente que devuelve los catálogos de una pantalla y **todas** las cuentas. La consulta de cuentas va sobre `app_user` **sin filtrar por estado** y marca las desactivadas en el texto; no usa `DestinosDeAsignacion.activos`, que excluye una cuenta y sólo trae activas (D4)
- [X] T009 `src/main/java/.../pendingtask/PendingTaskFilters.java`: nada que añadir —ya acepta los quince componentes—. **Comprobar y dejar constancia** de que `comoQuery` incluye `ownerId`, `typeId`, `priorityId`, `statusId` y `overdue`, porque de eso depende que los filtros nuevos sobrevivan a paginar
- [X] T010 `src/main/java/.../pendingtask/PendingTaskController.java`: añadir al modelo `queryActual = filtros.comoQuery(page)`. **Es la pieza que falta**: sólo existen `queryAnterior` y `querySiguiente`, y desde una fila hace falta la página actual o cada acción devolvería a la página 0 en silencio (D2)
- [X] T011 `src/main/java/.../shared/VueltaAlListado.java`: valida la cadena contra `^\?[A-Za-z0-9=&_%.\-]*$` o vacía y la concatena a una base **que recibe del código, nunca del usuario**. Hoy `/pendientes//algo` no es explotable; la validación está para que siga sin serlo cuando alguien cambie la base

**Punto de control**: `./mvnw verify` en verde. Nada ha cambiado para el usuario todavía.

---

## Fase 3: Historia 1 — Cancelar y recuperar (P1) 🎯 MVP

**Objetivo**: que un registro creado por error se pueda quitar de en medio.

**Prueba independiente**: cancelar un pendiente, comprobar que sale de la lista de trabajo, encontrarlo entre los ocultos y devolverlo.

### Pruebas

- [X] T012 [P] [US1] `src/test/java/.../web/CancelarPendienteContractTest.java`: `POST /pendientes/{id}/cancelar` lo saca del listado de trabajo; aparece con `visibility=inactive`; `POST /pendientes/{id}/devolver` lo trae de vuelta (RF-001, RF-002)
- [X] T013 [P] [US1] En la misma prueba: cancelar deja `CANCEL` en el historial y devolver deja `RESTORE`, ambos con actor y fecha y **con `reason` nulo** —RF-005 no pide motivo— (RF-003)
- [X] T014 [P] [US1] En la misma prueba, los rechazos: un abogado **no** puede cancelar el pendiente de otro (403); la jefa **sí**; una `version` desfasada da conflicto y **no** aplica el cambio; cancelar algo ya cancelado no hace nada **y no escribe una segunda entrada** (RF-004, RF-009)
- [X] T015 [P] [US1] `src/test/java/.../integration/CancelarNoSePropagaIT.java`: cancelar un pendiente **no** toca su expediente; ocultar un expediente con doce pendientes activos los deja **activos y en la lista de trabajo de quien los tenga**. Una cascada retiraría trabajo de otras personas sin que nadie lo decidiera (RF-008, D7)
- [X] T016 [P] [US1] `src/test/java/.../integration/CanceladoFueraDeTodasPartesIT.java`: un pendiente cancelado no aparece en el listado, ni en el bloque de su expediente, ni en `/alertas`, ni en `/calendario`, ni en `/pendientes/hoy` (RF-006). Son cinco pantallas distintas y cada una filtra por su cuenta
- [X] T017 [P] [US1] `src/test/java/.../web/VisibilidadExpedienteContractTest.java`: la ficha judicial y la administrativa **ofrecen** ocultar y volver a mostrar; quien no puede editar no ve el control; el estado «Archivado» y la visibilidad **no se afectan** (RF-007, contrato §visibilidad 3)

### Implementación

- [X] T018 [US1] `src/main/java/.../pendingtask/PendingTaskRepository.java`: método para cambiar la marca de visibilidad con versión, del mismo corte que los que ya existen para cumplir y reprogramar
- [X] T019 [US1] `src/main/java/.../pendingtask/PendingTaskActionService.java`: `cancelar` y `devolver`, reutilizando el ayudante `bloquear(id, version, actor)` que ya revalida el permiso **después** de bloquear —una revocación concurrente debe impedir el guardado— y auditando `CANCEL` y `RESTORE` con `{active: …}`. Es la forma de `ManualActivityService.retirar` de la 006 (D6)
- [X] T020 [US1] `src/main/java/.../pendingtask/PendingTaskController.java`: `POST /pendientes/{id}/cancelar` y `/devolver`, con `version` y el parámetro de vuelta. La ruta es **`cancelar`** porque así la nombra §25; las de expediente se quedan en `/visibilidad` y no se renombran (D1)
- [X] T021 [US1] `src/main/resources/templates/pending-tasks/detail.html`: el botón de cancelar —o el de devolver, según el estado— junto a las acciones que ya hay, sólo para quien puede actuar
- [X] T022 [US1] `src/main/resources/templates/judicial-cases/detail.html`: el control de ocultar y volver a mostrar, que apunta a `POST /judiciales/{id}/visibilidad`, **una ruta que el servidor ya sirve desde la 001 y que ninguna pantalla usaba**. Con su `version` en un campo oculto
- [X] T023 [US1] `src/main/resources/templates/administrative-procedures/detail.html`: lo mismo con `/administrativos/{id}/visibilidad`
- [X] T024 [US1] Quitar el componente `active` de `JudicialCaseForm` y `AdministrativeProcedureForm`: **es campo muerto** —nunca se pinta ni lo lee `actualizar`—, y ahora que hay una acción de verdad para la visibilidad, dejarlo ahí invita a creer que el formulario la cambia

**Punto de control**: un registro creado por error se puede quitar y recuperar, en las tres entidades. Entregable por sí solo.

---

## Fase 4: Historia 2 — Acciones rápidas en la fila (P2)

**Objetivo**: actuar sobre un pendiente sin abrirlo y sin perder el sitio.

**Prueba independiente**: desde la página 2 de un listado filtrado, marcar uno como cumplido y seguir en la página 2 con los mismos filtros.

### Pruebas

- [X] T025 [P] [US2] `src/test/java/.../web/AccionesEnLaFilaContractTest.java`: cada fila ofrece abrir, editar, cumplir y cancelar (RF-010); **no** ofrece «No cumplido» ni «Reprogramar», que piden datos y viven en la ficha (RF-011)
- [X] T026 [P] [US2] En la misma prueba: cumplir desde la fila con `?sort=title&page=1` devuelve **a esa misma página con ese mismo orden** (RF-012). Es el fallo silencioso de esta historia: sin `queryActual` volvería a la página 0 sin dar error
- [X] T027 [P] [US2] En la misma prueba, lo que se pinta: a un abogado **no** se le dibujan los botones sobre el pendiente de otro, y a la jefa **sí** (RF-013); un pendiente ya cumplido no ofrece cumplirse otra vez y uno cancelado no ofrece cancelarse (RF-014)
- [X] T028 [P] [US2] En la misma prueba, la comprobación que importa: aunque no se dibuje el botón, **el servidor sigue rechazando** un `POST` directo de quien no puede. La fila decide qué se pinta, no quién puede (D5)
- [X] T029 [P] [US2] `src/test/java/.../integration/UltimaFilaDeLaPaginaIT.java`: cumplir la única fila activa de la última página devuelve **a esa página, vacía**, con su salida visible. No es un error: el listado muestra lo que queda por hacer (R5)
- [X] T030 [P] [US2] `src/test/java/.../integration/PendingTaskQueryBudgetIT.java`: el listado con acciones cuesta **lo mismo con 2 filas que con 25**. Es lo que delataría una comprobación de permiso consultada por fila (CE-006)

### Implementación

- [X] T031 [US2] `src/main/resources/templates/pending-tasks/list.html`: una columna de acciones por fila. Cada formulario lleva su `version` y el `queryActual` en campos ocultos. **`th:if` (300) y `th:with` (400) nunca en el mismo elemento** — la condición de permiso va en un elemento propio; han sido cuatro tropiezos en este proyecto y todos compilaban limpios (R9)
- [X] T032 [US2] `src/main/java/.../pendingtask/PendingTaskController.java`: que `cumplir` acepte el parámetro de vuelta y redirija al listado cuando llegue, conservando el comportamiento actual —volver a la ficha— cuando no llegue. La acción desde la ficha no debe cambiar
- [X] T033 [US2] Poner en el modelo, por fila, si quien mira puede actuar. **Sin consulta nueva**: `puedeActuar` compara dos identificadores en memoria y `SELECCION` ya trae el responsable de cada fila

**Punto de control**: el repaso del final del día se hace desde la lista, sin abrir nada.

---

## Fase 5: Historia 3 — Los filtros de la sección 27 (P3)

**Objetivo**: componer un filtro sin escribir la dirección a mano.

**Prueba independiente**: en `/judiciales`, filtrar por una responsable y sólo los vencidos, con los desplegables.

### Pruebas

- [ ] T034 [P] [US3] `src/test/java/.../web/FiltrosEnPantallaContractTest.java`: `/judiciales` ofrece controles para responsable, estado procesal, materia y vencidos, además de los que ya tenía — los **ocho** de §27 (RF-016)
- [ ] T035 [P] [US3] En la misma prueba: `/administrativos` ofrece responsable, estado y vencidos (RF-017); `/pendientes` ofrece responsable, tipo, prioridad, estado y vencidos (RF-018)
- [ ] T036 [P] [US3] En la misma prueba: dos filtros a la vez se cumplen los dos; sobreviven a paginar y a ordenar; «Quitar filtros» los retira todos (RF-019, RF-020)
- [ ] T037 [P] [US3] `src/test/java/.../integration/FiltrosConCatalogoDeshabilitadoIT.java`: un catálogo deshabilitado **desaparece del desplegable** pero **no** desaparece de los registros que ya lo tenían; una cuenta desactivada **sigue en el desplegable** y filtrar por ella encuentra su trabajo (RF-021)
- [ ] T038 [P] [US3] `src/test/java/.../integration/QueryBudgetIT.java` y `ProcedureQueryBudgetIT.java`: `/judiciales` y `/administrativos` no pasan de **6 consultas**; `/pendientes` no pasa de **8**. Las cifras de partida medidas son 4, 4 y 6 (D3)
- [ ] T039 [P] [US3] `src/test/java/.../web/JudicialCaseListContractTest.java`: añadir `columnasDelInsumo`, **la prueba que le falta** y que tienen las otras dos listas. Es la que habría cazado antes que §27 pedía ocho filtros y la pantalla ofrecía tres

### Implementación

- [ ] T040 [US3] `src/main/java/.../judicialcase/JudicialCaseController.java`: pasar al modelo las opciones de `OpcionesDeFiltro`. **Sólo en el listado**, no en la ficha, que no filtra nada
- [ ] T041 [US3] `src/main/java/.../administrativeprocedure/AdministrativeProcedureController.java`: ídem
- [ ] T042 [US3] `src/main/java/.../pendingtask/PendingTaskController.java`: ídem en el listado
- [ ] T043 [P] [US3] `src/main/resources/templates/judicial-cases/list.html`: los cuatro controles nuevos. «Materia» es texto libre, no catálogo: va como campo de texto, no como desplegable
- [ ] T044 [P] [US3] `src/main/resources/templates/administrative-procedures/list.html`: los tres controles nuevos
- [ ] T045 [P] [US3] `src/main/resources/templates/pending-tasks/list.html`: los cinco controles nuevos, junto a los ocultos del expediente que ya puso la 008

**Punto de control**: los ocho filtros de §27 se aplican desde la pantalla.

---

## Fase 6: Acabado

- [ ] T046 [P] `src/test/java/.../acceptance/AccessibilityAcceptanceTest.java`: los botones de las filas y los desplegables nuevos se alcanzan con el teclado, y cada control tiene su etiqueta
- [ ] T047 [P] `src/test/java/.../acceptance/InterfazEnEspanolTest.java`: los textos nuevos en español con tildes (principio I)
- [ ] T048 `src/test/java/.../acceptance/RecorridoCancelarYFiltrosTest.java` con Playwright: el recorrido de [quickstart.md](quickstart.md) de punta a punta, con los pasos **4, 8 y 11** —los tres fallos silenciosos— como afirmaciones explícitas. Localizadores por texto de botón o acotados a un `tr`, nunca `form[action$=…]`: en la 006 eso violó el modo estricto dos veces
- [ ] T049 Anotar en [plan.md](plan.md) las cifras **medidas** de consultas, sustituyendo los techos, como en las funcionalidades 004, 006, 007 y 008
- [ ] T050 `./mvnw verify` completo. Prestar atención a `RutasSegunInsumoTest`: las rutas nuevas tienen que pasar el filtro de palabras inglesas —`cancelar` y `devolver` son españolas— y `FIJADAS_POR_EL_INSUMO` sigue con nueve, porque enumera pantallas y no acciones

---

## Dependencias

```text
Fase 1 (T001)
  ├─ Fase 3 US1 (T012-T024)  🎯 MVP — no necesita la fase 2
  └─ Fase 2 (T002-T011)
       ├─ Fase 4 US2 (T025-T033)  necesita queryActual y VueltaAlListado
       └─ Fase 5 US3 (T034-T045)  necesita OpcionesDeFiltro
            └─ Fase 6 (T046-T050)
```

**US1 no depende de la fase 2**, al contrario que en la 008: cancelar desde la ficha no necesita ni volver al listado ni desplegables. Se puede empezar por ella el primer día.

**US2 depende de US1** para el botón de cancelar de la fila —el resto de sus acciones ya existen— y comparte `list.html` con US3, que es el único punto de conflicto entre las dos.

### Se pueden hacer a la vez

- T002 a T006 (pruebas de la fase 2)
- T012 a T017 (pruebas de US1)
- T025 a T030 (pruebas de US2)
- T034 a T039 (pruebas de US3)
- T022 y T023 (las dos fichas de expediente, archivos distintos)
- T043, T044, T045 (las tres plantillas de listado)
- T046 y T047 (acabado)

---

## Estrategia

**MVP**: fases 1 y 3. Con eso desaparece el único problema sin salida —un registro creado por error—, y es desplegable sin tocar los listados.

**Después**: US2 ahorra el trabajo repetido del día a día; US3 cierra §27.

---

## Notas

- Sin migración. Si alguna tarea parece pedir una, se desvió del plan
- El principio VII prohíbe borrar: **todo se retira y se recupera**, y todo deja rastro
- El destino del redirect se escribe en el código. Si aparece un parámetro con una ruta completa, se perdió lo que D2 evitaba
- Los desplegables de filtro se leen **una vez por pantalla**. Si aparece una consulta por catálogo, se perdió D3
- Confirmar tras cada tarea; commit por grupo lógico
