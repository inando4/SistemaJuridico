---

description: "Tareas de implementación — Funcionalidad 002"
---

# Tareas: Control de procedimientos administrativos

**Entrada**: documentos de diseño en `specs/002-procedimientos-administrativos/`

**Prerrequisitos**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/web.md](contracts/web.md)

**Pruebas**: obligatorias. La constitución 4.0.1 exige PostgreSQL real por Testcontainers;
**H2 está prohibido**. Las `*IT` las ejecuta Failsafe en `verify`; las `*Test`, Surefire.

## Contexto: lo que NO se construye

La funcionalidad 001 está desplegada en producción. Estas piezas se reutilizan **sin tocarlas**
y no aparecen como tareas:

acceso identificado y sesión de jornada · calendario de días no laborables y su revisión anual ·
`DeadlineEvaluator` y `DeadlineView` · `AuditRecorder` y `AuditQueryRepository` · `Paging` ·
`ErrorHandling` · `HtmxSupport` · `ClockConfig` · roles separados de base de datos ·
`PostgresIntegrationTest`, `SesionDePrueba` y `DatosSinteticos`.

**Las migraciones V1 a V7 no se modifican jamás.** Están aplicadas en producción y Flyway
compara sumas de verificación.

---

## Fase 1: Fundacional (prerrequisito bloqueante)

**Objetivo**: el esquema admite procedimientos administrativos y la evidencia sigue siendo
inmutable después de ampliarla.

- [X] T001 Crear la migración `src/main/resources/db/migration/V8__administrative_procedure.sql` con las tablas `administrative_procedure`, `administrative_status` y `procedure_history_status_reference`, sus índices y la sustitución del CHECK de `entity_type` en `audit_event`; todo en una sola migración para que no exista un estado intermedio donde la aplicación pueda auditar un tipo que la base rechace
- [X] T002 Verificar en `src/test/java/pe/org/beneficencia/legalcontrol/integration/AdministrativeSchemaIT.java` que la migración crea las tres tablas, que las entradas de auditoría previas siguen existiendo tras sustituir la restricción, y que el rol de aplicación sigue sin poder ejecutar UPDATE ni DELETE sobre `audit_event`, intentándolo con una conexión de ese rol y no leyendo bits de permiso
- [X] T003 [P] Ampliar `src/test/java/pe/org/beneficencia/legalcontrol/integration/SesionDePrueba.java` para vaciar también las tablas nuevas, respetando el orden de dependencias
- [X] T004 [P] Ampliar `src/test/java/pe/org/beneficencia/legalcontrol/integration/DatosSinteticos.java` con la siembra de procedimientos administrativos y su catálogo, con datos inventados y semilla fija

**Punto de control**: `./mvnw verify` en verde; el esquema admite las entidades nuevas y la
garantía de inmutabilidad de la 001 sigue demostrada.

---

## Fase 2: Historia 1 — Registrar y consultar procedimientos (P1) 🎯 MVP

**Objetivo**: registrar un procedimiento con los datos del Excel y localizarlo después.

**Prueba independiente**: con una cuenta activa, registrar indicando solo el número de
expediente, encontrarlo por búsqueda y abrir su ficha.

### Pruebas

- [X] T005 [P] [US1] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/web/AdministrativeProcedureFormContractTest.java`: alta mínima con solo número, alta completa que recupera los datos intactos, número conservado con ceros y guiones, y que una fecha imposible o un texto excedido no dejan registro parcial ni pierden lo escrito
- [X] T006 [P] [US1] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/ProcedureNumberUniquenessIT.java`: rechazo por caja y espacios distintos, contra registros de otra persona, contra ocultos, y con ocho altas simultáneas del mismo número de las que solo prospera una
- [X] T007 [P] [US1] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/IndependentNumberingIT.java` verificando que un mismo número puede existir a la vez como expediente judicial y como procedimiento administrativo, porque sus series son independientes
- [X] T008 [P] [US1] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/web/AdministrativeProcedureListContractTest.java`: filtros combinados incluido área solicitante, orden contra lista cerrada con 422 ante un valor inventado, comodines escapados, estado vacío con salida, y página fuera de rango recuperable

### Implementación

- [X] T009 [P] [US1] Crear el modelo en `src/main/java/pe/org/beneficencia/legalcontrol/administrativeprocedure/AdministrativeProcedure.java`
- [X] T010 [P] [US1] Crear el objeto de formulario en `src/main/java/pe/org/beneficencia/legalcontrol/administrativeprocedure/AdministrativeProcedureForm.java`, sin responsable ni identificadores técnicos
- [X] T011 [P] [US1] Crear los filtros en `src/main/java/pe/org/beneficencia/legalcontrol/administrativeprocedure/ProcedureFilters.java` con listas cerradas de orden y visibilidad, y conservación al paginar
- [X] T012 [US1] Implementar el repositorio en `src/main/java/pe/org/beneficencia/legalcontrol/administrativeprocedure/AdministrativeProcedureRepository.java` resolviendo el listado en una sola consulta con joins al responsable y al catálogo, con `%` y `_` escapados
- [X] T013 [US1] Implementar la validación en `src/main/java/pe/org/beneficencia/legalcontrol/administrativeprocedure/AdministrativeProcedureValidator.java` devolviendo todos los errores a la vez y sin truncar ningún texto en silencio
- [X] T014 [US1] Implementar la advertencia de FR-007 en el validador: si la fecha límite es anterior a la de recepción se avisa pero se guarda, sin corregir ninguna de las dos
- [X] T015 [US1] Implementar el servicio de alta en `src/main/java/pe/org/beneficencia/legalcontrol/administrativeprocedure/AdministrativeProcedureService.java`, fijando el responsable al usuario que registra y escribiendo la evidencia en la misma transacción
- [X] T016 [US1] Implementar `GET /administrativos`, `GET /administrativos/nuevo`, `POST /administrativos` y `GET /administrativos/{id}` en `src/main/java/pe/org/beneficencia/legalcontrol/administrativeprocedure/AdministrativeProcedureController.java`
- [X] T017 [P] [US1] Crear las vistas de listado y alta en `src/main/resources/templates/administrative-procedures/list.html` y `form.html` con las columnas de la sección 29 del insumo
- [X] T018 [P] [US1] Crear la vista de ficha en `src/main/resources/templates/administrative-procedures/detail.html` mostrando los campos ausentes como ausentes, y dejando previsto sin construir el hueco de «Pendientes relacionados» de la sección 30
- [X] T019 [P] [US1] Añadir a `src/main/resources/messages.properties` los textos de las pantallas nuevas

**Punto de control**: se registra, se busca y se consulta un procedimiento. La historia es
demostrable por sí sola.

---

## Fase 3: Historia 2 — Interpretar el plazo de respuesta (P1)

**Objetivo**: ver los días hábiles restantes con la misma regla y el mismo calendario que los
expedientes judiciales.

**Prueba independiente**: con calendario cargado y revisado, registrar procedimientos con
fecha límite pasada, de hoy y futura, y comprobar los tres estados.

### Pruebas

- [X] T020 [P] [US2] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/SharedCalendarIT.java` verificando que añadir un día no laborable cambia el conteo de los procedimientos administrativos y el de los expedientes judiciales a la vez; si solo cambiara uno, habría dos calendarios donde debe haber uno
- [X] T021 [P] [US2] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/ProcedureDeadlineConsistencyIT.java` comprobando que listado y ficha muestran el mismo estado de plazo, y que sin cobertura confirmada ambos avisan en vez de mostrar un número
- [X] T022 [P] [US2] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/NoDerivedProcedureColumnsIT.java` recorriendo `information_schema` para garantizar que ninguna columna nueva guarda días restantes, estado de vencimiento ni la advertencia de fechas

### Implementación

- [X] T023 [US2] Conectar el `DeadlineEvaluator` existente al controlador, leyendo el calendario una sola vez por consulta y compartiendo esa lectura entre todas las filas de la página
- [X] T024 [US2] Mostrar el plazo en listado y ficha con texto además de color, incluidos el aviso de fecha límite en día no hábil y el de años sin cobertura con los años nombrados
- [X] T025 [US2] Mostrar la advertencia de fechas incoherentes en la ficha, calculada al consultar y nunca persistida

**Punto de control**: los plazos se interpretan igual que en los judiciales, con el mismo
calendario, y sin ninguna columna derivada en la base.

---

## Fase 4: Historia 3 — Editar con permisos y consultar el historial (P1)

**Objetivo**: mantener los procedimientos al día con atribución de cada cambio.

**Prueba independiente**: con dos cuentas de ABOGADO y una de JEFA, comprobar la matriz de
permisos con peticiones directas y revisar el historial resultante.

### Pruebas

- [X] T026 [P] [US3] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/web/ProcedurePermissionContractTest.java` comprobando la matriz completa mediante peticiones compuestas a mano, no pulsando botones, y verificando que el registro queda intacto tras cada rechazo
- [X] T027 [P] [US3] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/ProcedureConcurrentEditIT.java`: una versión obsoleta no pisa el cambio de la otra persona, un formulario sin versión se rechaza, y un guardado sin cambios no genera historial
- [X] T028 [P] [US3] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/ProcedureHistoryIT.java` verificando que se guardan los valores anterior y nuevo, que autor y responsable se distinguen cuando son personas distintas, y que consultar no escribe historial

### Implementación

- [X] T029 [P] [US3] Implementar la autorización en `src/main/java/pe/org/beneficencia/legalcontrol/administrativeprocedure/ProcedureAuthorization.java`, revalidada dentro de la transacción de escritura y no solo al pintar los botones
- [X] T030 [US3] Ampliar el repositorio con el bloqueo de fila, la actualización condicionada a la versión y el cambio de visibilidad que no toca el estado
- [X] T031 [US3] Ampliar el servicio con la edición: revalidar permiso tras bloquear, comparar versión, escribir evidencia con el responsable de ese momento y referenciar los estados implicados
- [X] T032 [US3] Implementar `GET /administrativos/{id}/editar`, `POST /administrativos/{id}`, `POST /administrativos/{id}/visibilidad` y `GET /administrativos/{id}/historial`
- [X] T033 [P] [US3] Crear las vistas `src/main/resources/templates/administrative-procedures/edit.html` y `history.html`, con la versión oculta en el formulario y la intervención de jefatura señalada en el historial

**Punto de control**: la matriz de permisos se cumple ante peticiones directas, dos personas
no se pisan al editar, y el historial atribuye cada cambio.

---

## Fase 5: Historia 4 — Administrar el catálogo de estados (P2)

**Objetivo**: que la jefatura mantenga la lista de estados sin depender del programador.

**Prueba independiente**: con el catálogo vacío, crear los estados, asignarlos, deshabilitar
uno e intentar borrar uno en uso.

### Pruebas

- [X] T034 [P] [US4] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/AdministrativeStatusIT.java`: catálogo vacío al inicio, rechazo de nombre repetido por caja o espacios, borrado permitido solo sin uso, y bloqueo del borrado tanto por uso actual como por aparición en el historial
- [X] T035 [P] [US4] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/web/AdministrativeStatusVisibilityContractTest.java` verificando que un ABOGADO consulta pero no modifica, y que un estado deshabilitado deja de ofrecerse sin alterar los procedimientos que ya lo usan
- [X] T036 [P] [US4] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/SeparateCatalogsIT.java` comprobando que crear «Archivado» en ambos catálogos no produce conflicto y que ninguno ve las filas del otro

### Implementación

- [X] T037 [P] [US4] Implementar el repositorio en `src/main/java/pe/org/beneficencia/legalcontrol/administrativestatus/AdministrativeStatusRepository.java` con la comprobación de uso actual e histórico
- [X] T038 [US4] Implementar el servicio en `src/main/java/pe/org/beneficencia/legalcontrol/administrativestatus/AdministrativeStatusService.java` distinguiendo deshabilitar de borrar, y escribiendo la evidencia
- [X] T039 [US4] Implementar `GET /estados-administrativos` y las tres escrituras exclusivas de JEFA en `src/main/java/pe/org/beneficencia/legalcontrol/administrativestatus/AdministrativeStatusController.java`
- [X] T040 [P] [US4] Crear la vista `src/main/resources/templates/administrative-statuses/list.html` explicando en el catálogo vacío qué estados suele usar el área
- [X] T041 [US4] Ofrecer solo los estados habilitados en los formularios de alta y edición, conservando el que ya tuviera un procedimiento existente

**Punto de control**: el catálogo se administra desde la aplicación, y un estado que se usó
alguna vez no puede desaparecer.

---

## Fase 6: Cierre y puertas de aceptación

- [X] T042 [P] Verificar en `src/test/java/pe/org/beneficencia/legalcontrol/integration/ProcedureQueryBudgetIT.java` que el listado no supera 6 consultas de dominio ni la ficha 7, contando transacciones reales y comprobando que el coste no crece con el número de filas
- [X] T043 [P] Medir en `src/test/java/pe/org/beneficencia/legalcontrol/acceptance/ProcedurePerformanceTest.java` el coste de servidor con 5.000 procedimientos, comprobando que la página lejana cuesta lo mismo que la primera
- [X] T044 [P] Ampliar `src/test/java/pe/org/beneficencia/legalcontrol/acceptance/InterfazEnEspanolTest.java` para que cubra las plantillas nuevas: sin etiquetas en inglés, avisos con texto y cada campo con su etiqueta asociada
- [X] T045 [P] Ampliar `src/test/java/pe/org/beneficencia/legalcontrol/acceptance/RutasSegunInsumoTest.java` comprobando que existen `/administrativos` y `/administrativos/{id}` tal como los fija el insumo y que ninguna ruta nueva invade las reservadas
- [X] T046 Ampliar el recorrido con navegador en `src/test/java/pe/org/beneficencia/legalcontrol/acceptance/AccessibilityAcceptanceTest.java` para el alta de un procedimiento por teclado y sin JavaScript
- [ ] T047 (pendiente: requiere aplicar V8 contra Supabase y redesplegar) Aplicar la migración V8 contra Supabase con la credencial de migración y redesplegar en Render, en ese orden, siguiendo [quickstart.md](quickstart.md)
- [ ] T048 (pendiente: depende de T047) Ejecutar el recorrido manual de [quickstart.md](quickstart.md) contra el despliegue y registrar la evidencia de los criterios SC-001 a SC-010

**Punto de control**: `./mvnw verify` en verde, presupuestos dentro de límite, y la
funcionalidad verificada sobre el entorno real.

---

## Dependencias

```text
Fase 1 (fundacional)
   └─> Fase 2 (US1) ──> Fase 3 (US2)   los plazos necesitan procedimientos que interpretar
        │                └─> Fase 4 (US3)
        └─────────────────────> Fase 5 (US4)   el catálogo se puede construir en paralelo
                                                a US2 y US3 una vez existe US1
                                   └─> Fase 6 (cierre)
```

La US4 solo depende de la US1: un procedimiento se registra sin estado asignado, así que el
catálogo no bloquea nada. Se puede construir en paralelo a las historias 2 y 3.

## Oportunidades de paralelismo

- **Fase 1**: T003 y T004 son independientes entre sí.
- **Fase 2**: las cuatro pruebas (T005–T008) se escriben en paralelo; después T009, T010 y T011
  también, por tocar archivos distintos.
- **Fase 3**: T020, T021 y T022 en paralelo.
- **Fase 4**: T026, T027 y T028 en paralelo.
- **Fase 5**: T034, T035 y T036 en paralelo.
- **Fase 6**: T042 a T045 en paralelo; T046 depende de las vistas, y T047 y T048 son
  secuenciales y van al final.

## Estrategia de entrega

El **MVP es la Fase 2**: con ella el área ya puede registrar y consultar procedimientos
administrativos, que es lo que hoy hace en el Excel. Las fases 3 a 5 añaden interpretación de
plazos, edición con historial y catálogo administrable.

Cada fase termina en un punto de control demostrable, así que se puede parar en cualquiera de
ellos y entregar lo hecho.
