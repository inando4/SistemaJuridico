---

description: "Tareas de implementación — Funcionalidad 003"
---

# Tareas: Control de pendientes

**Entrada**: documentos de diseño en `specs/003-control-pendientes/`

**Prerrequisitos**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/web.md](contracts/web.md)

**Pruebas**: obligatorias. PostgreSQL real por Testcontainers; **H2 prohibido**. Las `*IT` las
ejecuta Failsafe en `verify`; las `*Test`, Surefire.

## Contexto: lo que NO se construye

Las funcionalidades 001 y 002 están desplegadas. Se reutilizan **sin tocar** y no aparecen como
tareas: acceso y sesión de jornada · calendario de días no laborables y su revisión ·
`DeadlineEvaluator` · `AuditRecorder` y `AuditQueryRepository` · `Paging` · `ErrorHandling` ·
`HtmxSupport` · `ClockConfig` · roles separados · `PostgresIntegrationTest`, `SesionDePrueba`,
`DatosSinteticos` y `ContadorDeConsultas`.

**Las migraciones V1 a V8 no se modifican jamás.** Esta funcionalidad empieza en V9.

---

## Fase 1: Fundacional (prerrequisito bloqueante)

**Objetivo**: el esquema admite pendientes y sus catálogos, y la evidencia sigue siendo
inmutable tras ampliarla.

- [X] T001 Crear `src/main/resources/db/migration/V9__pending_task.sql` con `pending_task`, los tres catálogos `pending_task_type`, `priority` y `pending_task_status`, la tabla `pending_task_history_reference`, sus índices, la restricción CHECK de vínculo excluyente y la sustitución del CHECK de `entity_type` en `audit_event`; todo en una sola migración para que no exista un estado intermedio
- [X] T002 Verificar en `src/test/java/pe/org/beneficencia/legalcontrol/integration/PendingTaskSchemaIT.java` que se crean las cinco tablas, que la restricción de vínculo excluyente impide informar los dos expedientes a la vez, que las entradas de auditoría previas sobreviven, y que el rol de aplicación sigue sin poder modificar `audit_event` ni la tabla de referencia
- [X] T003 [P] Ampliar `src/test/java/pe/org/beneficencia/legalcontrol/integration/SesionDePrueba.java` para vaciar las tablas nuevas en orden de dependencias
- [X] T004 [P] Ampliar `src/test/java/pe/org/beneficencia/legalcontrol/integration/DatosSinteticos.java` con siembra de pendientes y sus catálogos, datos inventados y semilla fija
- [X] T005 Añadir `siguienteDiaHabil(fecha, calendario)` a `src/main/java/pe/org/beneficencia/legalcontrol/calendar/DeadlineEvaluator.java`, devolviendo vacío cuando el año atravesado no tenga cobertura confirmada en lugar de elegir una fecha
- [X] T006 [P] Verificar `siguienteDiaHabil` en `src/test/java/pe/org/beneficencia/legalcontrol/unit/SiguienteDiaHabilTest.java`: viernes a lunes, salto de feriado, cadena de varios feriados seguidos, cruce de año, y ausencia de resultado sin cobertura

**Punto de control**: `./mvnw verify` en verde; el esquema admite las entidades nuevas, el
vínculo excluyente lo impide la base, y la garantía de inmutabilidad sigue demostrada.

---

## Fase 2: Historia 1 — Registrar un pendiente y verlo en su lista (P1) 🎯 MVP

**Objetivo**: registrar lo que hay que hacer y encontrarlo.

**Prueba independiente**: registrar indicando solo el título y verlo en el listado.

### Pruebas

- [ ] T007 [P] [US1] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/web/PendingTaskFormContractTest.java`: alta mínima con solo título, alta completa que recupera los datos intactos, y que una fecha imposible o un texto excedido no dejan registro parcial ni pierden lo escrito
- [ ] T008 [P] [US1] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/PendingTaskLinkIT.java`: vínculo a expediente judicial, a administrativo, sin vínculo, y rechazo de ambos a la vez tanto por el servicio como por la restricción de la base saltándose la aplicación
- [ ] T009 [P] [US1] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/web/PendingTaskListContractTest.java`: filtros combinados incluido el de tipo de vínculo, orden contra lista cerrada con 422, comodines escapados, estado vacío con salida y página fuera de rango recuperable

### Implementación

- [ ] T010 [P] [US1] Crear el modelo en `src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTask.java`
- [ ] T011 [P] [US1] Crear el objeto de formulario en `src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskForm.java`, sin responsable ni identificadores técnicos
- [ ] T012 [P] [US1] Crear los filtros en `src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskFilters.java` con listas cerradas y conservación al paginar
- [ ] T013 [US1] Implementar el repositorio en `src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskRepository.java`, resolviendo el listado en una sola consulta con joins al responsable, a los tres catálogos y a ambos expedientes
- [ ] T014 [US1] Implementar la validación en `src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskValidator.java`, devolviendo todos los errores a la vez, rechazando el vínculo doble y sin truncar ningún texto
- [ ] T015 [US1] Implementar el alta en `src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskService.java`, fijando el responsable al usuario que registra y escribiendo la evidencia en la misma transacción
- [ ] T016 [US1] Implementar `GET /pendientes`, `GET /pendientes/nuevo`, `POST /pendientes` y `GET /pendientes/{id}` en `src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskController.java`
- [ ] T017 [P] [US1] Crear las vistas `src/main/resources/templates/pending-tasks/list.html` y `form.html` con las columnas de la sección 25 del insumo
- [ ] T018 [P] [US1] Crear `src/main/resources/templates/pending-tasks/detail.html`, mostrando los campos ausentes como ausentes y el documento de salida generado
- [ ] T019 [P] [US1] Añadir a `src/main/resources/messages.properties` los textos de las pantallas nuevas

**Punto de control**: se registra, se busca y se consulta un pendiente, con y sin vínculo.

---

## Fase 3: Historia 2 — Marcar cumplido y poder deshacerlo (P1)

**Objetivo**: la acción más frecuente del día, y su vuelta atrás.

**Prueba independiente**: cumplir, comprobar que sale de la lista activa, revertir con motivo
y comprobar que vuelve con las dos entradas en el historial.

### Pruebas

- [ ] T020 [P] [US2] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/CompleteAndRevertIT.java`: cumplir fija el instante y no toca la fecha programada; revertir la limpia y devuelve el estado; revertir conserva la fecha programada anterior y reaparece vencido si ya pasó
- [ ] T021 [P] [US2] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/web/RevertReasonContractTest.java` verificando que revertir sin motivo se rechaza, que con motivo se acepta, y que ninguna otra acción lo exige
- [ ] T022 [P] [US2] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/CompletionHistoryIT.java`: tras revertir quedan **dos** entradas, la del cumplimiento no se borra ni se edita, y revertir algo no cumplido se rechaza

### Implementación

- [ ] T023 [P] [US2] Implementar la autorización en `src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskAuthorization.java`, revalidada dentro de la transacción de escritura
- [ ] T024 [US2] Implementar `marcarCumplido` y `revertirCumplimiento` en el servicio, con el motivo obligatorio solo en la reversión y la evidencia en la misma transacción
- [ ] T025 [US2] Implementar `POST /pendientes/{id}/cumplir` y `POST /pendientes/{id}/revertir`, exigiendo versión y CSRF
- [ ] T026 [P] [US2] Añadir las acciones a la ficha y al listado en las plantillas, con el formulario de motivo en la reversión

**Punto de control**: se cumple, se revierte con motivo, y el historial conserva ambas entradas.

---

## Fase 4: Historia 3 — Declarar no cumplido y reprogramar (P1)

**Objetivo**: que lo que no salió pase solo al siguiente día hábil.

**Prueba independiente**: declarar no cumplido un pendiente de viernes y comprobar que pasa al
lunes, o más allá si hay feriado.

### Pruebas

- [ ] T027 [P] [US3] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/NotCompletedIT.java`: viernes a lunes, salto de feriado, estado a reprogramado y pendiente que sigue activo
- [ ] T028 [P] [US3] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/RescheduleWithoutCalendarIT.java` verificando que sin cobertura confirmada el sistema avisa y **no cambia la fecha**
- [ ] T029 [P] [US3] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/ManualRescheduleIT.java`: el historial guarda fecha anterior, nueva e instante, y el motivo es opcional

### Implementación

- [ ] T030 [US3] Implementar `declararNoCumplido` y `reprogramar` en el servicio, usando el `siguienteDiaHabil` del evaluador y respetando la regla de cobertura
- [ ] T031 [US3] Implementar `POST /pendientes/{id}/no-cumplido` y `POST /pendientes/{id}/reprogramar`
- [ ] T032 [US3] Implementar `GET /pendientes/{id}/historial` y la vista `src/main/resources/templates/pending-tasks/history.html`, componiendo la descripción legible «Reprogramado del X al Y» al mostrar, sin almacenarla
- [ ] T033 [P] [US3] Añadir las acciones al listado y la ficha, con el selector de fecha en la reprogramación manual

**Punto de control**: «no cumplí» reprograma al siguiente día hábil real, y sin calendario avisa
en vez de inventar una fecha.

---

## Fase 5: Historia 4 — Vigilar los pendientes sin plazo (P1)

**Objetivo**: que nada se quede esperando en silencio.

**Prueba independiente**: registrar uno sin fecha límite con recepción antigua y ver el aviso.

### Pruebas

- [ ] T034 [P] [US4] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/unit/AntiguedadSinPlazoTest.java` con los bordes exactos: quince días hábiles no avisa, dieciséis sí, y cumplido nunca avisa
- [ ] T035 [P] [US4] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/NoDerivedPendingColumnsIT.java` recorriendo `information_schema` para garantizar que ninguna columna guarda antigüedad, días restantes, tiempo de atención ni número de reprogramaciones

### Implementación

- [ ] T036 [US4] Implementar el cálculo de antigüedad en días hábiles desde la fecha de recepción, reutilizando el calendario compartido y avisando cuando falte cobertura o falte la fecha de recepción
- [ ] T037 [US4] Mostrar el aviso de pendiente sin plazo en listado y ficha, en texto además de color

**Punto de control**: el aviso aparece a partir del decimosexto día hábil y desaparece al
cumplir.

---

## Fase 6: Historia 5 — Trabajar el día de hoy y revisar lo hecho (P2)

**Objetivo**: las dos vistas del día a día.

**Prueba independiente**: con pendientes de hoy, ayer y mañana, comprobar qué muestra cada una.

### Pruebas

- [ ] T038 [P] [US5] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/web/TodayScreenContractTest.java`: incluye los de hoy y los vencidos que siguen activos, excluye los cumplidos
- [ ] T039 [P] [US5] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/CompletedTasksIT.java` verificando tiempo de atención y número de reprogramaciones calculados, y que un revertido desaparece de la pantalla

### Implementación

- [ ] T040 [US5] Implementar `GET /pendientes/hoy` y su vista `src/main/resources/templates/pending-tasks/today.html`
- [ ] T041 [US5] Implementar `GET /cumplidos` y su vista `src/main/resources/templates/pending-tasks/completed.html`, obteniendo el número de reprogramaciones con **una** agregación para toda la página, no una consulta por fila

**Punto de control**: las dos pantallas muestran lo que corresponde y ninguna hace una consulta
por fila.

---

## Fase 7: Historia 6 — Administrar los tres catálogos (P2)

**Objetivo**: que la jefatura mantenga tipos, prioridades y estados.

**Prueba independiente**: con los catálogos vacíos, crear valores, asignarlos, deshabilitar uno
e intentar borrar uno en uso.

### Pruebas

- [ ] T042 [P] [US6] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/PendingCatalogsIT.java`: los tres arrancan vacíos, rechazan nombre repetido, permiten borrar solo lo nunca usado y bloquean por uso actual y por aparición en historial
- [ ] T043 [P] [US6] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/web/PendingCatalogPermissionTest.java` verificando que un ABOGADO consulta pero no modifica, con peticiones directas
- [ ] T044 [P] [US6] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/FiveCatalogsIndependentIT.java` comprobando que el mismo nombre puede existir en los cinco catálogos del sistema sin conflicto y que ninguno ve las filas de otro

### Implementación

- [ ] T045 [US6] Implementar los tres catálogos siguiendo el patrón existente, con comprobación de uso actual e histórico contra `pending_task_history_reference`
- [ ] T046 [US6] Implementar `GET` y las escrituras de `/tipos-de-pendiente`, `/prioridades` y `/estados-de-pendiente`, exclusivas de JEFA
- [ ] T047 [P] [US6] Crear la vista compartida `src/main/resources/templates/catalogs/list.html`, parametrizada por catálogo, explicando en el vacío qué valores enumera el insumo
- [ ] T048 [US6] Ofrecer solo los valores habilitados en los formularios, conservando el que ya tuviera un pendiente existente

**Punto de control**: los tres catálogos se administran desde la aplicación y un valor usado no
puede desaparecer.

---

## Fase 8: Unificación de los cinco catálogos

**Objetivo**: saldar la deuda anunciada en la funcionalidad 002. Los cinco catálogos del
sistema pasan a compartir una base común.

**Riesgo**: toca código de las funcionalidades 001 y 002, ya desplegadas. Va aquí, al final,
con los tres catálogos nuevos funcionando y las pruebas existentes como red. Si al llegar el
riesgo pareciera mayor que el beneficio, es la única fase omitible sin dejar incompleta la
funcionalidad.

- [ ] T049 Crear `src/main/java/pe/org/beneficencia/legalcontrol/catalog/CatalogDefinition.java` describiendo tabla, tabla que lo referencia y tabla de referencia histórica de cada catálogo
- [ ] T050 Crear `src/main/java/pe/org/beneficencia/legalcontrol/catalog/CatalogRepository.java` y `CatalogService.java` con las operaciones comunes: crear con nombre único normalizado, cambiar disponibilidad y borrar solo si nunca se usó
- [ ] T051 Migrar los cinco catálogos a la base común, dejando en cada paquete solo su definición y su controlador
- [ ] T052 Verificar que **todas** las pruebas de catálogo de las funcionalidades 001, 002 y 003 siguen en verde sin modificarlas: son la prueba de que la unificación no cambió el comportamiento

**Punto de control**: `./mvnw verify` en verde sin haber tocado ninguna prueba existente.

---

## Fase 9: Cierre y puertas de aceptación

- [ ] T053 [P] Verificar en `src/test/java/pe/org/beneficencia/legalcontrol/integration/PendingTaskQueryBudgetIT.java` que los listados no superan 6 consultas ni la ficha 8, contando con `ContadorDeConsultas`, y que el conteo de reprogramaciones no escala con las filas
- [ ] T054 [P] Medir en `src/test/java/pe/org/beneficencia/legalcontrol/acceptance/PendingTaskPerformanceTest.java` el coste de servidor con 5.000 pendientes en las tres pantallas
- [ ] T055 [P] Ampliar `src/test/java/pe/org/beneficencia/legalcontrol/acceptance/InterfazEnEspanolTest.java` y `RutasSegunInsumoTest.java` para cubrir las plantillas y rutas nuevas
- [ ] T056 Ampliar el recorrido con navegador en `src/test/java/pe/org/beneficencia/legalcontrol/acceptance/AccessibilityAcceptanceTest.java` para cumplir y revertir un pendiente por teclado y sin JavaScript
- [ ] T057 [P] Verificar en `src/test/java/pe/org/beneficencia/legalcontrol/integration/ConcurrentActionsIT.java` que dos acciones simultáneas sobre el mismo pendiente no dejan estado inconsistente: prospera una y la otra recibe conflicto
- [ ] T058 Aplicar la migración V9 contra Supabase y redesplegar con `./desplegar.sh`, tras poner `main` al día
- [ ] T059 Ejecutar el recorrido manual de [quickstart.md](quickstart.md) contra el despliegue y registrar la evidencia de SC-001 a SC-011

**Punto de control**: `./mvnw verify` en verde, presupuestos dentro de límite, y la
funcionalidad verificada sobre el entorno real.

---

## Dependencias

```text
Fase 1 (fundacional, incluye siguienteDiaHabil)
   └─> Fase 2 (US1)  ──┬─> Fase 3 (US2)  cumplir y revertir
                       ├─> Fase 4 (US3)  no cumplido y reprogramar
                       ├─> Fase 5 (US4)  sin plazo
                       └─> Fase 7 (US6)  catálogos
                              Fase 3 y 4 ──> Fase 6 (US5)  las vistas necesitan
                                                            cumplidos y reprogramados
                                                   └─> Fase 8 ──> Fase 9
```

La US6 solo depende de la US1: un pendiente se registra sin tipo ni prioridad asignados.

La Fase 8 depende de la 7 porque unifica los cinco catálogos, y los tres nuevos deben existir.

## Oportunidades de paralelismo

- **Fase 1**: T003, T004 y T006 en paralelo.
- **Fase 2**: las tres pruebas T007–T009; después T010, T011 y T012.
- **Fases 3, 4, 5 y 7**: sus bloques de pruebas son paralelos entre sí.
- **Fase 9**: T053, T054, T055 y T057 en paralelo; T058 y T059 secuenciales al final.

## Estrategia de entrega

El **MVP es la Fase 2**: registrar y consultar pendientes ya sustituye la hoja del Excel.

Pero el valor real llega con las **fases 3 y 4**: cumplir, revertir y reprogramar es el flujo
diario que describe la §44 del insumo. Si hubiera que parar antes de terminar, ese es el punto
donde el área notaría la diferencia.

Cada fase cierra en un punto de control demostrable.
