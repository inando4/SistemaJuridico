---

description: "Tareas de implementación de la feature 005"
---

# Tareas: Trabajo en equipo — vista de carga y asignación

**Entrada**: documentos de diseño de `specs/005-equipo-asignacion/`

**Prerrequisitos**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/pantallas.md](contracts/pantallas.md), [quickstart.md](quickstart.md)

**Pruebas**: **obligatorias**. La constitución (principio IV) convierte los presupuestos medibles en puerta de aceptación, y el principio VII exige evidencia comprobable de la trazabilidad. Failsafe ejecuta `*IT.java`; Surefire, `*Test.java`. PostgreSQL real con Testcontainers: H2 está prohibido.

**Organización**: por historia de usuario, para que cada una se implemente y se pruebe sola.

## Formato: `[ID] [P?] [Historia] Descripción`

- **[P]**: puede ir en paralelo (archivos distintos, sin dependencias pendientes)
- **[US1] [US2] [US3]**: a qué historia pertenece

## Rutas

Proyecto único. `src/main/java/pe/org/beneficencia/legalcontrol/` abreviado como `…/legalcontrol/`, y `src/test/java/pe/org/beneficencia/legalcontrol/` como `…/test/`.

---

## Fase 1: Preparación

**Propósito**: dejar el terreno listo. **No hay migración**: `audit_event.action` es texto libre y el `CHECK` de la V9 ya admite los tres tipos (`research.md`, decisión 1).

- [X] T001 Crear los paquetes `…/legalcontrol/team/` y `…/legalcontrol/assignment/` con su `package-info.java` explicando por qué la reasignación vive centralizada y no repartida entre los tres paquetes de dominio
- [X] T002 [P] Añadir a `…/test/integration/DatosSinteticos.java` un ayudante `sembrarEquipo(jdbc, encoder, n)` que cree n cuentas activas con nombres sintéticos, y `sembrarCargaDesigual(jdbc, responsables, hoy)` que reparta pendientes vencidos, de la semana y sin plazo de forma desigual
- [X] T003 [P] Añadir a `…/test/integration/DatosSinteticos.java` un ayudante `sembrarExpedienteConPendientes(jdbc, responsable, activos, cumplidos)` que devuelva el id del expediente y los de sus pendientes

---

## Fase 2: Base (bloquea todas las historias)

**Propósito**: lo que las tres historias necesitan. **Hasta aquí no hay nada visible.**

- [X] T004 Crear `…/legalcontrol/assignment/DestinosDeAsignacion.java`: consulta las cuentas **activas** (`status = 'ACTIVE'`, **sin filtrar por rol** — `research.md`, decisión 6) excluyendo opcionalmente al responsable actual, con nombre ordenado
- [X] T005 [P] Crear `…/test/unit/SemanaDeTrabajoTest.java` con **fechas absolutas, no relativas a hoy**: lunes y domingo de la semana de una fecha dada, incluyendo el caso de una semana que cruza el cambio de año y el de un domingo (el fin de semana pertenece a su propia semana, no a la siguiente)
- [X] T006 Crear `…/legalcontrol/team/SemanaDeTrabajo.java` con `lunesDe(LocalDate)` y `domingoDe(LocalDate)` usando `with(DayOfWeek.MONDAY)`. **Aritmética de calendario, no días hábiles**: por eso los recuentos de la semana no se degradan sin calendario (`research.md`, decisión 3). Hacer pasar T005

---

## Fase 3: Historia 1 — Reasignar un expediente completo (P1) 🎯 MVP

**Meta**: la jefa cambia de responsable un expediente y todo su trabajo viaja con él.

**Prueba independiente**: reasignar un expediente con pendientes activos y cumplidos y comprobar que todos cambiaron de dueño y que el nuevo puede operar sobre ellos. **Entrega valor sin la vista de equipo.**

### Pruebas primero

- [X] T007 [P] [US1] Crear `…/test/integration/ReasignacionIT.java`: reasignar un expediente judicial con 3 activos y 2 cumplidos deja los 6 registros a nombre del destino; el mismo caso con un procedimiento administrativo (escenarios 1 y 4)
- [X] T008 [P] [US1] Crear `…/test/integration/ReasignacionPermisosIT.java`: un abogado recibe rechazo del servidor al enviar el `POST` directamente, incluso sobre un expediente suyo; la jefa sí puede (escenario 5, RF-003)
- [X] T009 [P] [US1] Crear `…/test/integration/ReasignacionHistorialIT.java`: el expediente registra `A → B` con la jefa como autora; **cada pendiente movido tiene su propia entrada** (RF-007); el pendiente que era de una tercera persona C conserva **C** como responsable anterior, no A (escenarios 6, 7 y 12 — `research.md`, decisión 5)
- [X] T010 [P] [US1] Crear `…/test/integration/ReasignacionAtomicaIT.java`: forzando un fallo a mitad de la transacción, ni el expediente ni **ninguno** de sus pendientes cambia de responsable (escenario 8, RF-005)
- [X] T011 [P] [US1] Crear `…/test/integration/ReasignacionSinCambiosIT.java`: reasignar al responsable que ya consta no escribe historial; pero un expediente que ya es de B con pendientes de A **sí** produce cambios efectivos sobre esos pendientes (RF-011 — `research.md`, decisión 8)
- [X] T012 [P] [US1] Crear `…/test/integration/ReasignacionDestinoInvalidoIT.java`: rechazo al reasignar a una cuenta inactiva (RF-010); la jefa sí es destino válido
- [X] T013 [P] [US1] Crear `…/test/integration/PermisosTrasReasignarIT.java`: el nuevo responsable revierte un cumplido que marcó el anterior (RF-008); el anterior conserva la lectura y pierde la escritura (RF-009). Escenarios 2 y 3
- [X] T014 [P] [US1] Crear `…/test/integration/ReasignacionVersionIT.java`: reasignar sube `version` de cada registro movido, así que quien estuviera editando uno recibe `ConflictoDeEdicion` en vez de sobrescribir (`research.md`, decisión 7)

### Implementación

- [X] T015 [US1] Crear `…/legalcontrol/assignment/ReassignmentRepository.java` con los tres pasos de `data-model.md`: `SELECT id, owner_id … WHERE <vínculo> AND owner_id <> :nuevo` (la foto previa, porque PostgreSQL 17 no tiene `RETURNING OLD.*`), el `UPDATE` en bloque de los pendientes y el `UPDATE` del expediente con comprobación de versión
- [X] T016 [US1] Añadir a `…/legalcontrol/audit/AuditRecorder.java` un método de inserción **en bloque** que escriba una fila por registro movido en una sola sentencia, para que el número de escrituras no dependa de cuántos pendientes cuelguen
- [X] T017 [US1] Crear `…/legalcontrol/assignment/ReassignmentService.java`: `@Transactional`, exige jefa **en el servidor**, valida el destino activo, ejecuta los tres pasos y escribe `action = 'REASSIGN'` con `owner_id` = responsable **anterior real** de cada registro. Hacer pasar T007 a T014
- [X] T018 [US1] Crear `…/legalcontrol/assignment/AvisoDeTraspaso.java`: cuenta los pendientes que se traspasarán y **nombra a los responsables actuales distintos del saliente** (RF-004b). Los tres textos están en `contracts/pantallas.md`
- [X] T019 [P] [US1] Crear `src/main/resources/templates/fragments/reasignacion.html` con el fragmento `formulario(destino, actual, version, aviso)`, el desplegable de cuentas activas y el mensaje `No hay otra cuenta activa a la que reasignar.` cuando no quede ninguna
- [X] T020 [US1] Crear `…/legalcontrol/assignment/ReassignmentController.java` con `POST /judiciales/{id}/responsable` y `POST /administrativos/{id}/responsable`, y los mensajes exactos de `contracts/pantallas.md`
- [X] T021 [P] [US1] Insertar el fragmento de reasignación en `templates/judicial-cases/detail.html`, visible solo para la jefa, con el aviso previo de T018
- [X] T022 [P] [US1] Insertar el mismo fragmento en `templates/administrative-procedures/detail.html`. **Tarea aparte a propósito**: RF-001 y RF-002 están separados porque implementar uno y olvidar el otro es el fallo que la 004 tuvo con el quinto sitio de su inventario
- [X] T023 [US1] Añadir a `…/legalcontrol/audit/AuditQueryRepository.java` la lectura de `REASSIGN`, resolviendo los nombres de responsable anterior y nuevo desde el JSON de la evidencia
- [X] T024 [P] [US1] Mostrar el cambio de responsable en `templates/judicial-cases/history.html`, `administrative-procedures/history.html` y `pending-tasks/history.html`. **Sin esto la evidencia se guarda y no se ve**, que es el fallo que la 003 dejó en producción con las reprogramaciones
- [X] T025 [US1] Crear `…/test/integration/ReasignacionQueryBudgetIT.java`: con `ContadorDeConsultas`, comprobar **≤ 6 sentencias** y que **el número no cambia** al pasar de 5 a 50 pendientes. Es la comprobación que distingue una operación en bloque de un N+1 que aún no duele

**Punto de control**: la reasignación funciona de punta a punta y es desplegable sola.

---

## Fase 4: Historia 2 — Vista de carga del equipo (P2)

**Meta**: ver de un vistazo cómo está repartido el trabajo, para decidir a quién asignar.

**Prueba independiente**: con carga desigual entre cinco personas, la vista muestra los recuentos correctos y ordena de mayor a menor.

### Pruebas primero

- [ ] T026 [P] [US2] Crear `…/test/integration/EquipoIT.java`: una fila por cuenta activa con sus cuatro recuentos; **quien no tiene pendientes sale con ceros** (RF-018 — comprueba `LEFT JOIN` y `count(t.id)`, no `count(*)`); las cuentas inactivas no salen (RF-019)
- [ ] T027 [P] [US2] Crear `…/test/integration/EquipoOrdenIT.java`: se ordena por `vencidos + esta semana` descendente; el desempate por nombre e id hace que **dos aperturas seguidas den el mismo orden**
- [ ] T028 [P] [US2] Crear `…/test/integration/EquipoJefaIT.java`: **la jefa aparece en la lista** con su carga. Filtrar por `role = 'LAWYER'` la borraría, y es el filtro que se reintroduce sin pensar (RF-019a)
- [ ] T029 [P] [US2] Crear `…/test/integration/EquipoSinCalendarioIT.java`: sin el año confirmado, «Sin plazo, antiguos» muestra `Faltan días no laborables por revisar` y **«Vencidos» y «Vence esta semana» siguen mostrando su número**. Espejo de `AlertLevelsIT.sinCalendarioSalenLosQueNoDependenDeEl`
- [ ] T030 [P] [US2] Crear `…/test/integration/EquipoEnlacesIT.java`: cada recuento lleva a un listado con **exactamente** las mismas filas que decía el número (RF-020). Es el desajuste tarjeta/listado que la 004 tuvo que corregir
- [ ] T031 [P] [US2] Crear `…/test/integration/EquipoPermisosIT.java`: un abogado puede abrir `/equipo` (RF-021, la lectura es compartida)
- [ ] T032 [P] [US2] Crear `…/test/integration/NoDerivedTeamColumnsIT.java`: el esquema no tiene ninguna columna ni tabla de agregados de carga (principio V, CE-009)

### Implementación

- [ ] T033 [US2] Añadir el foco `semana` a `…/legalcontrol/pendingtask/PendingTaskRepository.java`, junto a los `vencidos`, `hoy`, `proximos` y `sin-plazo-antiguos` que ya existen, comparando `deadline` o `scheduled_for` contra el lunes y el domingo
- [ ] T034 [US2] Crear `…/legalcontrol/team/CargaDeAbogado.java` como registro de solo lectura, con `sinPlazoAntiguos` de tipo `Integer` para que **`null` signifique «no se puede calcular» y no cero** (`data-model.md`)
- [ ] T035 [US2] Crear `…/legalcontrol/team/TeamWorkloadRepository.java` con **la consulta única** de `data-model.md`: `LEFT JOIN`, `count(t.id) FILTER`, `WHERE u.status = 'ACTIVE'` sin filtro de rol, `CAST(:hace15 AS date)` y orden estable. Hacer pasar T026 a T028
- [ ] T036 [US2] Crear `…/legalcontrol/team/TeamController.java` para `GET /equipo`: resuelve el lunes y el domingo con `SemanaDeTrabajo`, la frontera de antigüedad con `DeadlineEvaluator.restarDiasHabiles` sobre `CalendarRepository.paraAntiguedad(hoy)` —**no `paraListado`**, que fue el fallo de la 003— y convierte el cero por frontera ausente en `null`
- [ ] T037 [US2] Crear `src/main/resources/templates/team/list.html` con la tabla de `contracts/pantallas.md`, el rango `Semana del … al …` bajo el título y **la suma que ordena visible en su columna** (RF-017a)
- [ ] T038 [US2] Añadir en `templates/team/list.html` los enlaces de cada recuento con `responsable` y `visibilidad` explícitos. **Sin esos dos parámetros el listado muestra lo de todo el mundo** y el número no cuadra. Hacer pasar T030
- [ ] T039 [P] [US2] Añadir el enlace `Equipo` a `templates/fragments/navegacion.html` entre «Cumplidos» y «Judiciales», y pasar `actual = 'equipo'` desde `TeamController`
- [ ] T040 [US2] Enlazar desde la vista de equipo hacia la reasignación del expediente (RF-022)
- [ ] T041 [US2] Crear `…/test/integration/EquipoQueryBudgetIT.java`: **≤ 4 consultas**, y **el mismo número con 15 personas que con 5**. La invariancia es la comprobación que importa
- [ ] T042 [US2] Crear `…/test/integration/EquipoPerformanceTest.java`: p95 ≤ 400 ms con 5 cuentas y 5.000 pendientes, el mismo volumen que midió la 004 para poder comparar

**Punto de control**: la jefa ve la carga y llega desde ahí a reasignar.

---

## Fase 5: Historia 3 — Elegir responsable al dar de alta (P3)

**Meta**: registrar un expediente ya a nombre de quien lo va a llevar.

**Prueba independiente**: la jefa da de alta un expediente eligiendo responsable y queda a nombre de esa persona.

- [ ] T043 [P] [US3] Crear `…/test/integration/AltaConResponsableIT.java`: la jefa elige responsable y el expediente queda a nombre de esa persona; un abogado que **envía `ownerId` de todas formas** queda como responsable él, porque el servidor ignora el campo (RF-012, RF-013, escenarios 1 y 2)
- [ ] T044 [P] [US3] Crear `…/test/integration/AltaHistorialIT.java`: el historial del alta con responsable elegido tiene `before_values` **nulo**. Es lo que distingue asignar de reasignar (RF-014, principio VII, escenario 3)
- [ ] T045 [US3] Añadir `ownerId` opcional a `…/legalcontrol/judicialcase/JudicialCaseForm.java` y a `AdministrativeProcedureForm.java`
- [ ] T046 [US3] En `JudicialCaseService` y `AdministrativeProcedureService`, usar el `ownerId` **solo si el actor es jefa**; en cualquier otro caso, la identidad del actor. Hacer pasar T043
- [ ] T047 [P] [US3] Añadir el desplegable de responsable a `templates/judicial-cases/form.html` y `administrative-procedures/form.html`, visible solo para la jefa, con la propia cuenta por omisión

**Punto de control**: las tres historias completas.

---

## Fase 6: Reasignación de pendientes sueltos (RF-025 a RF-028)

**Propósito**: cerrar el hueco que motivó la consulta al cliente. Sin esto, un abogado que deja el área deja pendientes sueltos inmovilizados. Pertenece a la Historia 1 por prioridad, pero se separa porque se puede entregar aparte.

- [ ] T048 [P] Crear `…/test/integration/ReasignacionPendienteSueltoIT.java`: un pendiente sin vínculo se reasigna y su historial lo registra (escenario 9); uno **vinculado** se rechaza con el mensaje de `contracts/pantallas.md` (escenario 10, RF-027)
- [ ] T049 Añadir a `ReassignmentService` la reasignación individual, rechazando los pendientes vinculados y aplicando las mismas guardas que los expedientes (RF-028)
- [ ] T050 Añadir `POST /pendientes/{id}/responsable` a `ReassignmentController`
- [ ] T051 En `templates/pending-tasks/detail.html`, mostrar el formulario solo si el pendiente **no** está vinculado; si lo está, un enlace a su expediente, para que la jefa llegue a la operación correcta en vez de encontrar un botón ausente sin explicación
- [ ] T052 Crear `…/test/integration/TraspasoCompletoIT.java`: un abogado con expedientes judiciales, administrativos y pendientes sueltos puede quedar **sin ningún registro** sin vía de traspaso (CE-010). Es la comprobación que cierra el caso que motivó la pregunta al cliente

---

## Fase 7: Acabado y comprobaciones transversales

- [ ] T053 [P] Añadir `/equipo` a la lista de pantallas de `…/test/acceptance/RevisionConNavegadorTest.java`, para que la guardia ortográfica y la de mojibake la cubran
- [ ] T054 [P] Añadir los textos nuevos a `src/main/resources/messages.properties` **con sus tildes y eñes**, verificando que las claves no cambian
- [ ] T055 [P] Revisar la accesibilidad de la vista y del formulario en `…/test/acceptance/AccessibilityAcceptanceTest.java`: encabezados de tabla asociados, orden de foco y el `role="alert"` del aviso de traspaso
- [ ] T056 Crear `…/test/acceptance/RecorridoEquipoTest.java` que recorra con navegador los doce pasos de `quickstart.md`
- [ ] T057 Ejecutar `./mvnw -o verify` completo y comprobar que las 220 pruebas anteriores siguen pasando
- [ ] T058 Revisar con Playwright la vista y las tres fichas buscando errores de consola, enlaces rotos y textos sin tilde

---

## Dependencias

```
Fase 1 (T001-T003)
   └─> Fase 2 (T004-T006)   ← bloquea todo
         ├─> Fase 3  US1 (T007-T025)  🎯 MVP, desplegable sola
         │      ├─> Fase 6 (T048-T052)  reutiliza ReassignmentService
         │      └─> Fase 4  US2 (T026-T042)  T040 necesita T020
         └─> Fase 5  US3 (T043-T047)   independiente de US2
   Fase 7 (T053-T058) ← al final
```

- **US1 no depende de nada** salvo la base. Es el MVP.
- **US2 depende de US1 solo para T040** (el enlace a reasignar). Todo lo demás es independiente: se puede construir la vista antes y enlazarla después.
- **US3 es independiente de US2.**
- **La fase 6** necesita `ReassignmentService` de T017.

## Paralelismo

**Fase 3, las ocho pruebas de golpe**: T007 a T014 tocan archivos distintos y ninguna depende de otra.

**Fase 4, las siete pruebas de golpe**: T026 a T032.

**Entre fases**: con US1 terminada, la fase 4 y la fase 5 pueden avanzar a la vez — no comparten archivos salvo el controlador de reasignación, que ya estará hecho.

**Cuidado con T021 y T022**: marcadas [P] porque son plantillas distintas, pero **hay que hacer las dos**. Están separadas justamente para que la segunda no se pierda.

## Estrategia de implementación

**MVP = fase 1 + fase 2 + fase 3** (T001–T025). Con eso la jefa ya puede reasignar expedientes completos, que es la capacidad que hoy no existe de ninguna forma, y es desplegable sin nada más.

**Segundo incremento**: fase 6 (T048–T052). Barata, reutiliza lo anterior y cierra el hueco de los pendientes sueltos.

**Tercero**: fase 4 (T026–T042), la vista de equipo, que da criterio a lo anterior.

**Cuarto**: fase 5 (T043–T047), comodidad en el alta.

En cada incremento, las pruebas van **antes** que la implementación dentro de su fase: los presupuestos del principio IV son puerta de aceptación, y una pantalla que los incumple no está terminada.

## Recuento

| Fase | Tareas | Historia |
|---|---|---|
| 1. Preparación | 3 | — |
| 2. Base | 3 | — |
| 3. Reasignar expediente | 19 | US1 (MVP) |
| 4. Vista de equipo | 17 | US2 |
| 5. Alta con responsable | 5 | US3 |
| 6. Pendientes sueltos | 5 | US1 (ampliación) |
| 7. Acabado | 6 | — |
| **Total** | **58** | |
