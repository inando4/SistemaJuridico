---

description: "Tareas de la 006 — calendario, actividad diaria y buscador global"
---

# Tareas: Calendario, actividad diaria y buscador global

**Entrada**: documentos de diseño de `/specs/006-calendario-actividad-busqueda/`

**Requisitos previos**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/pantallas.md](contracts/pantallas.md), [quickstart.md](quickstart.md)

**Pruebas**: **incluidas**. No es opcional en este proyecto: la constitución hace de los presupuestos de consultas y tiempo un criterio de aceptación (principio IV) y exige PostgreSQL real vía Testcontainers (principio VIII).

**Organización**: por historia de usuario, para que cada una se pueda terminar y probar sola.

## Formato: `[ID] [P?] [Historia] Descripción`

- **[P]**: se puede hacer en paralelo (archivo distinto, sin depender de nada incompleto)
- **[US1/US2/US3]**: a qué historia pertenece
- Cada tarea lleva su ruta de archivo

## Convenciones de ruta

- Código: `src/main/java/pe/org/beneficencia/legalcontrol/`
- Plantillas: `src/main/resources/templates/`
- Migraciones: `src/main/resources/db/migration/`
- Pruebas: `src/test/java/pe/org/beneficencia/legalcontrol/{unit,web,integration,acceptance}/`

---

## Fase 1: Preparación

- [ ] T001 Comprobar que la rama `006-calendario-actividad-busqueda` está al día con `main` y que `./mvnw verify` pasa en verde **antes** de tocar nada, para que cualquier fallo posterior sea atribuible
- [ ] T002 Levantar el entorno local con `./probar-local.sh --sembrar` y dejar activas la jefa y dos abogados, según los requisitos previos de [quickstart.md](quickstart.md)

---

## Fase 2: Base común (bloquea a US2 y US3)

**Propósito**: unificar el cálculo de la frontera del día antes de que tres pantallas nuevas lo repitan mal.

- [ ] T003 Sustituir los dos `ZoneId.of("America/Lima")` escritos a mano por `ClockConfig.ZONA` en `src/main/java/.../pendingtask/PendingTaskRepository.java:234` y `src/main/java/.../pendingtask/PendingTaskController.java:361`
- [ ] T004 Añadir a `src/test/java/.../unit/` una prueba que compruebe que ningún archivo bajo `src/main/java` contiene el literal `ZoneId.of("America/Lima")` fuera de `ClockConfig`, al estilo de `RutasSegunInsumoTest` (recorre las fuentes). Esta feature añade varias fronteras de día y el literal suelto es lo que las descoloca

**Punto de control**: `./mvnw verify` sigue en verde. US1 no depende de esta fase y puede empezar en paralelo.

---

## Fase 3: Historia 1 — Buscador global (P1) 🎯 MVP

**Objetivo**: encontrar cualquier registro desde una sola caja, incluyendo los campos que hoy no se buscan.

**Prueba independiente**: cargar registros con un término distintivo **solo** en materia y **solo** en observaciones, buscar, y ver los tres grupos. No necesita migración ni ninguna otra historia.

### Ampliar los campos de búsqueda (RF-012)

- [ ] T005 [P] [US1] Extraer la condición `ILIKE` a un fragmento reutilizable y añadir `subject` y `notes` en `src/main/java/.../judicialcase/JudicialCaseRepository.java`, conservando el `ESCAPE '\'` y el método `escapar(...)`
- [ ] T006 [P] [US1] Añadir `notes` a la condición de `src/main/java/.../administrativeprocedure/AdministrativeProcedureRepository.java:66`, misma forma
- [ ] T007 [P] [US1] Añadir `notes` a la condición de `src/main/java/.../pendingtask/PendingTaskRepository.java:90`, misma forma
- [ ] T008 [US1] Actualizar `src/test/java/.../web/JudicialCaseListContractTest.java`, `AdministrativeProcedureListContractTest.java` y `PendingTaskListContractTest.java` con un caso por campo nuevo: un registro que coincida **solo** por materia y otro **solo** por observaciones (T005–T007)
- [ ] T009 [US1] Comprobar que `src/test/java/.../integration/ProcedureQueryBudgetIT.java` y `PendingTaskQueryBudgetIT.java` siguen dentro de su presupuesto tras ampliar la condición, y corregir el número medido si cambió — el documento, no el código

### Pruebas del buscador

- [ ] T010 [P] [US1] `src/test/java/.../web/BusquedaContractTest.java`: `q` ausente no consulta; `q` de 2 caracteres devuelve el aviso de mínimo **sin consultar**; `q` válido devuelve los tres grupos; `100%` se trata como literal
- [ ] T011 [P] [US1] `src/test/java/.../integration/BusquedaGlobalIT.java`: un término presente **solo** en materia, **solo** en observaciones de cada tipo, y un expediente **archivado** que coincide. Comprobar que el buscador lo devuelve señalado y que `/judiciales?q=…` (visibilidad por omisión) **no** lo devuelve — es lo que CE-003 afirma y lo que no
- [ ] T012 [P] [US1] `src/test/java/.../integration/BusquedaQueryBudgetIT.java`: **la invariante** — las mismas consultas con 300 coincidencias que con 3, usando `ContadorDeConsultas`. El techo absoluto se anota con el número medido

### Implementación

- [ ] T013 [P] [US1] `src/main/java/.../search/package-info.java` con la razón de ser del paquete y el enlace a la decisión 7 de research.md
- [ ] T014 [P] [US1] `src/main/java/.../search/ResultadoDeBusqueda.java` y `GrupoDeResultados.java` — la referencia mínima a un registro y el par `(resultados, hayMas)`
- [ ] T015 [US1] `src/main/java/.../search/GlobalSearchRepository.java`: tres consultas independientes, cada una con `Paging.limitConSondeo()`, reutilizando el fragmento `ILIKE` de T005–T007. **Sin `count(*)`** — rompería CE-008
- [ ] T016 [US1] `src/main/java/.../search/GlobalSearchController.java` con `GET /buscar` y los parámetros `q`, `pageJ`, `pageA`, `pageP` de [contracts/pantallas.md](contracts/pantallas.md). Los nombres son los del contrato: en la 005, inventarlos en español dejó los enlaces sin filtrar
- [ ] T017 [US1] `src/main/resources/templates/search/results.html`: tres grupos con encabezado, «hay más» por grupo, enlace a la ficha, marca visible en los archivados, y mensaje explícito cuando no hay nada
- [ ] T018 [US1] Añadir el enlace **Buscar** a `src/main/resources/templates/fragments/navegacion.html`
- [ ] T019 [US1] Comprobar `src/test/java/.../acceptance/InterfazEnEspanolTest.java` y `RutasSegunInsumoTest.java`: `/buscar` es ruta nueva no fijada por el insumo (§34 no da ninguna) y debe pasar el filtro de rutas en inglés

**Punto de control**: el buscador funciona entero y entrega valor sin US2 ni US3. Es el MVP.

---

## Fase 4: Historia 2 — Actividad diaria (P2)

**Objetivo**: ver lo cumplido en el día sin registrarlo, y completar a mano lo que nunca fue pendiente.

**Prueba independiente**: cumplir dos pendientes y añadir una actividad manual; la pantalla muestra las tres y solo las tres.

### Migración

- [ ] T020 [US2] `src/main/resources/db/migration/V10__manual_activity.sql`: tabla `manual_activity` según [data-model.md](data-model.md), con los `CHECK` de longitud y `manual_activity_tipo_excluyente`, y los dos índices
- [ ] T021 [US2] En `src/main/resources/db/migration/V10__manual_activity.sql`, **ampliar `audit_event_entidad_valida` con `MANUAL_ACTIVITY`** con `DROP CONSTRAINT` + `ADD CONSTRAINT`, igual que `V9__pending_task.sql:110`. Sin esto **toda** escritura de auditoría de la actividad manual falla
- [ ] T022 [US2] En `src/main/resources/db/migration/V10__manual_activity.sql`, `GRANT SELECT, INSERT, UPDATE ON manual_activity TO sistema_juridico_app` — **sin `DELETE`**: retirar es `active = false`
- [ ] T023 [US2] En `src/main/resources/db/migration/V10__manual_activity.sql`, `REVOKE UPDATE, DELETE, TRUNCATE ON flyway_schema_history FROM sistema_juridico_app`, la deuda que la 005 dejó anotada (research.md, decisión 13)
- [ ] T024 [US2] `src/test/java/.../integration/ManualActivitySchemaIT.java`: la tabla existe con sus restricciones; insertar con las dos columnas de tipo llenas **falla**; el rol de la aplicación no puede borrar de `manual_activity` ni escribir en `flyway_schema_history`

### El segundo uso del catálogo (research.md, decisión 1)

- [ ] T025 [US2] Cambiar `src/main/java/.../catalog/CatalogDefinition.java` para que los usos sean una **lista** de pares tabla/columna; `TIPOS_DE_PENDIENTE` declara `pending_task` y `manual_activity`, los otros cuatro catálogos uno solo
- [ ] T026 [US2] Adaptar `enUsoActual` en `src/main/java/.../catalog/CatalogRepository.java:84-89` para recorrer la lista. Es el **único** sitio que lee esos accesores (comprobado)
- [ ] T027 [US2] `src/test/java/.../integration/CatalogoEnUsoPorActividadIT.java`: un tipo usado **solo** por una actividad manual no se puede borrar, y el mensaje dice **que está en uso**, no que aparezca en el historial

### Pruebas de la actividad diaria

- [ ] T028 [P] [US2] `src/test/java/.../integration/ActividadDiariaIT.java`: un pendiente cumplido a las **23:50 hora de Lima** aparece en **ese** día, no en el siguiente. Es el caso que falla si alguien escribe `CAST(completed_at AS date)`
- [ ] T029 [P] [US2] En `src/test/java/.../integration/ActividadDiariaIT.java`: un cumplido revertido deja de aparecer; una actividad manual con fecha anterior aparece al consultar ese día — CE-005 tal como está redactado
- [ ] T030 [P] [US2] `src/test/java/.../integration/ActividadManualIT.java`: las tres formas de tipo (sin tipo, del catálogo, escrito a mano) se guardan y se listan; el tipo escrito **no** aparece en `/tipos-de-pendiente` (RF-015b)
- [ ] T031 [P] [US2] En `src/test/java/.../integration/ActividadManualIT.java`: alta, edición y retirada escriben en `audit_event` con `entity_type = 'MANUAL_ACTIVITY'` conservando `before_values`; retirar **no** borra la fila (RF-021, RF-022)
- [ ] T032 [P] [US2] `src/test/java/.../web/ActividadDiariaContractTest.java`: fecha futura rechazada; `POST` de otra persona rechazado por el servidor; los `POST` llevan CSRF
- [ ] T033 [P] [US2] `src/test/java/.../integration/ActividadQueryBudgetIT.java`: **la invariante** — las mismas consultas con 40 actividades que con 1

### Implementación

- [ ] T034 [P] [US2] `src/main/java/.../activity/package-info.java`, explicando por qué la mitad automática no se persiste y la manual sí (research.md, decisión 3)
- [ ] T035 [P] [US2] `src/main/java/.../activity/ManualActivity.java` y `ActividadDelDia.java` — el registro persistido y la unión que **nunca** se guarda
- [ ] T036 [US2] `src/main/java/.../activity/ManualActivityRepository.java`: alta, edición, retirada por `active`, y el listado del día con **`LEFT JOIN`** al catálogo — con `JOIN` desaparecerían justo las de tipo libre y las sin tipo
- [ ] T037 [US2] Añadir a `src/main/java/.../pendingtask/PendingTaskRepository.java` la consulta de cumplidos del día por **dos marcas de tiempo** (`>= :inicio AND < :inicioDelSiguiente`), nunca por `CAST` (research.md, decisión 4)
- [ ] T038 [P] [US2] `src/main/java/.../activity/ManualActivityForm.java` y `ManualActivityValidator.java`: descripción obligatoria, fecha no futura, y `typeId`/`otherType` mutuamente excluyentes
- [ ] T039 [US2] `src/main/java/.../activity/ManualActivityService.java`, `@Transactional`, con la comprobación de autor-o-jefa **en el servicio** (RF-020) y la llamada a `AuditRecorder.referenciarCatalogosDePendiente(evento, tipoId)` cuando el tipo venga del catálogo
- [ ] T040 [US2] `src/main/java/.../activity/DailyActivityController.java` con `GET /actividad-diaria` y los `POST` de alta, edición y retirada, según el contrato
- [ ] T041 [US2] `src/main/resources/templates/activity/day.html`: cumplidos y manuales **distinguidos** (RF-017), navegación de fechas, mensaje de día vacío, y el formulario de alta **solo** cuando se mira la actividad propia
- [ ] T042 [P] [US2] `src/main/resources/templates/activity/form.html` con el desplegable «(sin tipo) / catálogo / Otro» y el campo de texto que aparece al elegir «Otro»
- [ ] T043 [P] [US2] `src/main/resources/templates/activity/history.html` con el caso de `MANUAL_ACTIVITY`, siguiendo los historiales existentes
- [ ] T044 [US2] Añadir el enlace **Actividad diaria** a `fragments/navegacion.html`
- [ ] T045 [US2] Actualizar `src/test/java/.../acceptance/RutasSegunInsumoTest.java`: `/actividad-diaria` deja de estar reservada y pasa a **exigirse**; `/configuracion` sigue reservada

**Punto de control**: la actividad diaria funciona entera. La V10 está en producción y US3 puede empezar.

---

## Fase 5: Historia 3 — Calendario (P3)

**Objetivo**: ver el día, la semana o el mes con todo lo que ocurre en ellos.

**Prueba independiente**: con eventos de los cinco orígenes repartidos por un mes, las tres vistas los muestran cada uno en su día.

### Pruebas

- [ ] T046 [P] [US3] `src/test/java/.../integration/AgendaIT.java`: los cinco orígenes aparecen (programado, vencimiento de pendiente, vencimiento judicial, actuación judicial, vencimiento administrativo), cada uno en su día y con su tipo
- [ ] T047 [P] [US3] En `src/test/java/.../integration/AgendaIT.java`: un pendiente con programación **y** vencimiento dentro del rango produce **dos** eventos en dos días — no es duplicado
- [ ] T048 [P] [US3] En `src/test/java/.../integration/AgendaIT.java`: **navegar a un mes de un año anterior** con su año confirmado muestra el sombreado y **no** avisa. Es el caso que falla con `paraListado(hoy)` (research.md, decisión 10)
- [ ] T049 [P] [US3] En `src/test/java/.../integration/AgendaIT.java`: con el año sin confirmar, la rejilla y **todos** los eventos siguen; solo se pierde el sombreado y se avisa (RF-027)
- [ ] T050 [P] [US3] `src/test/java/.../unit/RejillaDelMesTest.java`: un mes que empieza en domingo y otro que acaba en lunes se completan con días vecinos **distinguidos**; una rejilla de diciembre a enero abarca dos años
- [ ] T051 [P] [US3] `src/test/java/.../integration/AgendaQueryBudgetIT.java`: **la invariante** — el **mismo** número de consultas en vista día, semana y mes. Y el **tiempo p95 de un mes cargado a propósito**, porque el calendario no pagina y la invariante no vigila eso (research.md, decisión 11)

### Implementación

- [ ] T052 [P] [US3] `src/main/java/.../agenda/package-info.java`, explicando por qué el paquete es `agenda` y no `calendar` (plan.md, decisión de estructura)
- [ ] T053 [P] [US3] `src/main/java/.../agenda/EventoDeAgenda.java` con su enum de tipo
- [ ] T054 [US3] `src/main/java/.../agenda/AgendaRepository.java`: **una** consulta con las cinco ramas `UNION ALL` sobre `:desde`/`:hasta`, con orden estable por `(dia, tipo, titulo, entidad_id)`
- [ ] T055 [P] [US3] `src/main/java/.../agenda/RejillaDelMes.java`: rango de la rejilla y reparto en semanas. **Sin SQL** — es aritmética sobre la lista
- [ ] T056 [US3] `src/main/java/.../agenda/AgendaController.java` con `GET /calendario` y los parámetros `vista`, `ancla`, `ownerId`. El sombreado se pide con `instantanea(desde.getYear(), hasta.getYear())`, **nunca** con `paraListado(hoy)`
- [ ] T057 [US3] `src/main/resources/templates/agenda/calendar.html`: las tres vistas, días vecinos distinguidos, sombreado de no laborables, nombre del tipo en cada evento de pendiente, enlace a la ficha, indicador de «hay N más» en días llenos, y navegación anterior/siguiente/hoy conservando `vista` y `ownerId`
- [ ] T058 [US3] Añadir el enlace **Calendario** a `fragments/navegacion.html`
- [ ] T059 [US3] Actualizar `src/test/java/.../acceptance/RutasSegunInsumoTest.java`: `/calendario` deja de estar prohibida para los controladores y pasa a **exigirse** en `AgendaController`, sin que `CalendarController` (`/dias-no-laborables`) la ocupe

**Punto de control**: las tres pantallas funcionan.

---

## Fase 6: Acabado y comprobaciones transversales

- [ ] T060 `src/test/java/.../acceptance/RecorridoAgendaTest.java`: recorrido con Playwright de los 15 pasos de [quickstart.md](quickstart.md). En la 003 y en la 005, el mensaje que no se renderiza solo lo encontró el navegador
- [ ] T061 Añadir las tres pantallas nuevas a `src/test/java/.../acceptance/AccessibilityAcceptanceTest.java`: orden de foco, etiquetas de formulario y navegación por teclado en la rejilla del calendario. Si hace falta escribir en un campo con `autofocus`, esperar con `waitForFunction` sobre `document.activeElement.id` antes de teclear
- [ ] T062 Añadir las tres rutas a `src/test/java/.../acceptance/InterfazEnEspanolTest.java`
- [ ] T063 [P] Medir los tres presupuestos y **corregir en [plan.md](plan.md), [quickstart.md](quickstart.md) y [contracts/pantallas.md](contracts/pantallas.md) los techos provisionales con el número real**. Si el número medido supera la estimación y ninguna consulta es redundante, se corrige el documento, no el código — como en la 005 (6 escritos, 7 medidos)
- [ ] T064 [P] Actualizar `DESPLIEGUE.md` con la V10: es la primera migración desde la V9 y hay que ejecutarla con la credencial de migración, **que no va en las variables de entorno de Render**
- [ ] T065 Ejecutar `./mvnw verify` completo y comprobar que las **518 pruebas anteriores** siguen en verde junto a las nuevas
- [ ] T066 Recorrer [quickstart.md](quickstart.md) a mano en local, con atención a los pasos 2, 5, 7 y 13 — los cuatro que comprueban fallos que este plan predice pero que ninguna prueba unitaria vería

---

## Dependencias

```
Fase 1 (T001-T002)
   │
   ├─────────────────────────────► US1 (T005-T019)  ◄── no depende de la Fase 2
   │                                    │
Fase 2 (T003-T004)                      │
   │                                    │
   ├──► US2 (T020-T045) ────────────────┤
   │       │                            │
   │       └── T020-T024 (V10) bloquean T034-T045
   │                                    │
   └──► US3 (T046-T059) ────────────────┤
                                        │
                                  Fase 6 (T060-T066)
```

- **US1 no depende de nada más que la Fase 1.** Es el MVP y puede ir primero y solo.
- **US2 depende de la Fase 2** (frontera del día) y su propia migración bloquea su implementación.
- **US3 depende de la Fase 2** (frontera del día para «hoy») pero **no de US2**: no toca `manual_activity`.
- **US2 y US3 son independientes entre sí** y podrían hacerse en cualquier orden.

## Paralelismo

Dentro de US1: T005, T006 y T007 son tres repositorios distintos. T010, T011 y T012 son tres archivos de prueba distintos.

Dentro de US2: T028–T033 son pruebas en archivos distintos. T042 y T043 son plantillas distintas.

Dentro de US3: T046–T051 son pruebas independientes; T052, T053 y T055 no se tocan entre sí.

Entre historias: **US1 puede ir entera en paralelo a la Fase 2**, porque no calcula ninguna frontera de día.

## Estrategia de entrega

1. **MVP = US1.** El buscador solo ya resuelve una tarea diaria completa, no escribe nada y no necesita migración. Se puede desplegar sin US2 ni US3.
2. **Después US2**, que es donde está el riesgo: la única migración y la única escritura.
3. **US3 al final**, que es la de más trabajo de interfaz y menos capacidad nueva.

Cada punto de control es un estado desplegable.
