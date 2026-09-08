---

description: "Task list template for feature implementation"
---

# Tasks: Dashboard y sistema de alertas

**Input**: Design documents from `/specs/004-dashboard-alertas/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/pantallas.md](contracts/pantallas.md)

**Tests**: obligatorios. No es una elección de esta funcionalidad: la constitución exige
PostgreSQL real con Testcontainers, presupuestos medibles de consultas y tiempo (principio IV)
y comprobación de que el historial no se toca (principio VII).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo con las demás marcadas igual (archivos distintos, sin
  dependencias pendientes).
- **[US1]/[US2]/[US3]**: a qué historia de usuario pertenece.

## Path Conventions

Aplicación web de un solo proceso. Código en
`src/main/java/pe/org/beneficencia/legalcontrol/`, plantillas en
`src/main/resources/templates/`, pruebas en `src/test/java/pe/org/beneficencia/legalcontrol/`.

**Esta funcionalidad no lleva migración**: no crea ni altera ninguna tabla.

---

## Phase 1: Setup

- [X] T001 Crear el paquete `src/main/java/pe/org/beneficencia/legalcontrol/dashboard/` y el
      directorio de plantillas `src/main/resources/templates/dashboard/`
- [X] T002 Confirmar que no hace falta migración: comprobar que `pending_task`,
      `non_working_day`, `calendar_year` y `calendar_review` ya tienen todas las columnas que
      usa [data-model.md](data-model.md), y dejar constancia en el propio archivo si algo
      faltara

---

## Phase 2: Foundational (Blocking Prerequisites)

**Bloquea todas las historias.** Aquí vive la decisión 1 del plan —las fechas frontera— y la
corrección del fallo de calendario que la investigación destapó.

- [X] T003 Añadir `sumarDiasHabiles(LocalDate desde, int n)` a
      `src/main/java/pe/org/beneficencia/legalcontrol/calendar/DeadlineEvaluator.java`,
      devolviendo `Optional<LocalDate>` vacío si falta cobertura de calendario para algún año
      del intervalo, igual que hace `siguienteDiaHabil`
- [X] T004 Añadir `restarDiasHabiles(LocalDate hasta, int n)` al mismo archivo, con el mismo
      contrato pero recorriendo hacia atrás
- [X] T005 Añadir a
      `src/main/java/pe/org/beneficencia/legalcontrol/calendar/CalendarRepository.java` un
      snapshot que abarque también el año anterior (`paraAntiguedad(LocalDate hoy)`), porque
      `paraListado` solo mira hacia adelante y la antigüedad se cuenta hacia atrás
- [X] T006 Verificar el fallo y su corrección en
      `src/test/java/pe/org/beneficencia/legalcontrol/calendar/CoberturaAnualTest.java`: con
      **fechas absolutas** a ambos lados del cambio de año (recepción en diciembre, consulta
      en enero), la antigüedad debe dar un número y no el aviso de calendario sin cobertura.
      Con fechas relativas a «hoy» este fallo no aparece hasta enero
- [X] T007 Corregir el uso en
      `src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskController.java`
      (líneas del aviso de pendiente sin plazo, ~162 y ~323): usar el snapshot que mira hacia
      atrás. **Es un fallo ya desplegado en la 003**, no introducido aquí
- [X] T008 Comprobar en
      `src/test/java/pe/org/beneficencia/legalcontrol/integration/PendingTaskDeadlineIT.java`
      que la ficha de un pendiente recibido el año anterior muestra su antigüedad en lugar del
      aviso

**Checkpoint**: la aritmética de días hábiles resuelve fronteras y cruza años en ambos
sentidos. Sin esto ninguna tarjeta puede calcularse.

---

## Phase 3: User Story 1 - Ver de un vistazo qué exige atención hoy (Priority: P1) 🎯 MVP

**Goal**: seis tarjetas que digan cuánto hay de cada cosa, y que lleven a esos mismos
pendientes.

**Independent Test**: registrar pendientes en las seis situaciones, abrir el dashboard,
comprobar que cada tarjeta cuenta los suyos y que al pulsarla se llega exactamente a ellos.

### Tests for User Story 1

- [X] T009 [P] [US1] Comprobar las seis cuentas en
      `src/test/java/pe/org/beneficencia/legalcontrol/integration/DashboardIT.java`: un
      pendiente por cada situación y la cifra esperada en cada tarjeta
- [X] T010 [P] [US1] Comprobar en el mismo archivo que un pendiente **de otro responsable** no
      entra en ninguna cuenta
- [X] T011 [P] [US1] Comprobar que un pendiente cumplido sale de las tarjetas de urgencia y
      entra en «Cumplidos este mes»
- [X] T012 [P] [US1] Comprobar que sin cobertura de calendario «Próximos vencimientos» y «Sin
      plazo +15 días» avisan, y que las otras cuatro siguen dando su número

### Implementation for User Story 1

- [X] T013 [US1] Crear
      `src/main/java/pe/org/beneficencia/legalcontrol/dashboard/ResumenDelDia.java` con las
      seis cifras, distinguiendo «cero» de «no se puede saber»: en pantalla son cosas muy
      distintas
- [X] T014 [US1] Crear
      `src/main/java/pe/org/beneficencia/legalcontrol/dashboard/DashboardRepository.java` con
      las seis cuentas en **una sola consulta**, usando `COUNT(*) FILTER (WHERE ...)` y
      recibiendo las fronteras ya resueltas
- [X] T015 [US1] Usar `active = true AND completed_at IS NULL` en toda cuenta de «activos»,
      igual que el listado de pendientes (decisión 4 de [research.md](research.md)); contarlos
      solo por `active` haría discrepar la tarjeta de su listado
- [X] T016 [US1] Crear
      `src/main/java/pe/org/beneficencia/legalcontrol/dashboard/DashboardController.java` con
      `GET /`, que resuelve las fronteras **una sola vez** y filtra por el responsable de la
      sesión
- [X] T017 [US1] Crear `src/main/resources/templates/dashboard/index.html` con las seis
      tarjetas, cada una enlazando a su listado, y con texto para el caso de cero pendientes
- [X] T018 [US1] Añadir el filtro `alerta` a
      `src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskFilters.java`
      contra lista cerrada (`vencidos`, `hoy`, `proximos`, `sin-plazo-antiguos`, `activos`,
      `cumplidos-del-mes`), devolviendo 422 fuera de ella
- [X] T019 [US1] Aplicar ese filtro en
      `src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskRepository.java`,
      recibiendo las mismas fronteras que las tarjetas para que no puedan discrepar
- [X] T020 [US1] Comprobar en `DashboardIT.java` que el listado al que lleva cada tarjeta
      contiene **exactamente** los pendientes que esa tarjeta contaba (SC-004)

**Checkpoint**: el dashboard es utilizable navegando a `/` a mano. Ya entrega valor.

---

## Phase 4: User Story 2 - Recorrer las alertas en orden de urgencia (Priority: P1)

**Goal**: la lista de lo que requiere atención, cada uno con su tipo y en orden de urgencia.

**Independent Test**: crear un pendiente de cada tipo, abrir `/alertas`, comprobar etiquetas y
orden.

### Tests for User Story 2

- [X] T021 [P] [US2] Comprobar los cinco niveles y sus etiquetas en
      `src/test/java/pe/org/beneficencia/legalcontrol/integration/AlertLevelsIT.java`
- [X] T022 [P] [US2] Comprobar que un pendiente **vencido y además programado para hoy**
      aparece **una sola vez**, como vencido (FR-013, SC-005)
- [X] T023 [P] [US2] Comprobar que los cumplidos no aparecen, aunque su fecha haya pasado
- [X] T024 [P] [US2] Comprobar que el orden es el de los cinco niveles y que dos aperturas
      seguidas devuelven lo mismo

### Implementation for User Story 2

- [X] T025 [US2] Crear
      `src/main/java/pe/org/beneficencia/legalcontrol/dashboard/NivelDeAlerta.java` como
      enumeración con orden y nombre en español; no es catálogo administrable porque los fija
      el insumo
- [X] T026 [US2] Crear
      `src/main/java/pe/org/beneficencia/legalcontrol/dashboard/AlertRepository.java`
      asignando el nivel con **un solo `CASE`** evaluado en orden, no cinco consultas unidas:
      la unión daría duplicados y gastaría cinco viajes (decisión 3 de
      [research.md](research.md))
- [X] T027 [US2] Ordenar por nivel y, dentro de cada uno, por fecha y luego por identificador,
      para que el orden sea estable
- [X] T028 [US2] Crear
      `src/main/java/pe/org/beneficencia/legalcontrol/dashboard/AlertController.java` con
      `GET /alertas`, filtrando por el responsable de la sesión y paginando de 25 como el
      resto de listados
- [X] T029 [US2] Crear `src/main/resources/templates/dashboard/alerts.html` con la lista por
      niveles, cada fila enlazando a la ficha del pendiente
- [X] T030 [US2] Mostrar el aviso cuando falte cobertura de calendario, sin ocultar los
      niveles 1 a 3, que no dependen de él

**Checkpoint**: las dos pantallas de lectura funcionan de forma independiente.

---

## Phase 5: User Story 3 - Entrar directamente al dashboard (Priority: P2)

**Goal**: que la sesión aterrice en el dashboard y se pueda volver a él desde cualquier
pantalla.

**Independent Test**: iniciar sesión y comprobar el destino; volver al dashboard desde tres
pantallas distintas.

- [X] T031 [US3] Cambiar el destino en
      `src/main/java/pe/org/beneficencia/legalcontrol/access/SesionIniciada.java` de
      `/judiciales` a `/`
- [X] T032 [US3] Añadir el enlace al dashboard en la navegación principal, en
      `src/main/resources/templates/fragments/`
- [X] T033 [P] [US3] Actualizar `entrarConTeclado()` en
      `src/test/java/pe/org/beneficencia/legalcontrol/acceptance/AccessibilityAcceptanceTest.java`,
      que espera `waitForURL("**/judiciales**")`
- [X] T034 [P] [US3] Actualizar `entrar()` en
      `src/test/java/pe/org/beneficencia/legalcontrol/acceptance/RecorridoQuickstartTest.java`,
      que espera lo mismo
- [X] T035 [US3] Comprobar que quien pide `/` sin sesión va a iniciar sesión y, tras hacerlo,
      al dashboard

**Checkpoint**: la funcionalidad está completa de cara al usuario.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T036 [P] Verificar el presupuesto de consultas en
      `src/test/java/pe/org/beneficencia/legalcontrol/integration/DashboardQueryBudgetIT.java`
      con `ContadorDeConsultas`: `/` no pasa de **4** consultas y `/alertas` de **5**, con
      5.000 pendientes. Seis tarjetas no son seis consultas
- [X] T037 [P] Medir el coste de servidor en
      `src/test/java/pe/org/beneficencia/legalcontrol/acceptance/DashboardPerformanceTest.java`:
      p95 de **300 ms** para `/` y **400 ms** para `/alertas` con 5.000 pendientes
- [X] T038 [P] Comprobar en `DashboardQueryBudgetIT.java` que abrir ambas pantallas **no
      escribe ninguna fila** en `audit_event` (FR-019, SC-008)
- [X] T039 [P] Comprobar sobre `information_schema` que no se añadió ninguna columna derivada
      a `pending_task` ni ninguna tabla de alertas (principio V)
- [X] T040 Actualizar
      `src/test/java/pe/org/beneficencia/legalcontrol/acceptance/RutasSegunInsumoTest.java`:
      `/` y `/alertas` existen; `/calendario`, `/actividad-diaria` y `/configuracion` siguen
      reservadas
- [X] T041 [P] Ampliar
      `src/test/java/pe/org/beneficencia/legalcontrol/acceptance/InterfazEnEspanolTest.java`
      con las plantillas nuevas, incluidos nombres de tarjetas y tipos de alerta
- [X] T042 Ampliar
      `src/test/java/pe/org/beneficencia/legalcontrol/acceptance/AccessibilityAcceptanceTest.java`:
      ambas pantallas se recorren solo con teclado y siguen siendo utilizables si HTMX no
      llega a cargarse
- [X] T043 Recorrido de [quickstart.md](quickstart.md) automatizado con navegador en
      `src/test/java/pe/org/beneficencia/legalcontrol/acceptance/RevisionConNavegadorTest.java`:
      barrido de las 15 pantallas buscando claves sin traducir, errores de JavaScript,
      enlaces rotos y desbordes. Revision visual del panel y las alertas con capturas
- [ ] T044 Poner `main` al día y redesplegar con `./desplegar.sh`. **Sin migración que
      aplicar**: el script detectará que no hay nada pendiente y seguirá con los demás pasos

---

## Dependencies & Execution Order

### Phase Dependencies

```text
Fase 1 (Setup)
    ↓
Fase 2 (Foundational) ← bloquea todo: las fronteras y el cruce de años
    ↓
    ├─→ Fase 3 (US1: dashboard)  ─┐
    └─→ Fase 4 (US2: alertas)     ─┤ independientes entre sí
                                   ↓
                          Fase 5 (US3: entrada)
                                   ↓
                          Fase 6 (Polish)
```

### User Story Dependencies

- **US1 y US2 son independientes**: ambas dependen solo de la fase 2. Pueden implementarse a
  la vez o en cualquier orden.
- **US3 depende de US1**: no tiene sentido redirigir a una pantalla que aún no existe.

### Within Each User Story

Pruebas → tipos → repositorio → controlador → plantilla. El repositorio antes que el
controlador porque el segundo depende de la forma que devuelve el primero.

### Parallel Opportunities

- **Fase 2**: T003 y T004 son el mismo archivo, van seguidas; T005 es otro archivo y puede ir
  en paralelo.
- **Fase 3**: T009 a T012 en paralelo (mismo archivo de prueba, secciones distintas: coordinar
  o escribirlas seguidas).
- **Fase 4**: T021 a T024 en paralelo.
- **Fase 5**: T033 y T034 en paralelo, son archivos distintos.
- **Fase 6**: T036, T037, T038, T039 y T041 en paralelo.
- **US1 y US2 completas** pueden llevarlas dos personas a la vez tras la fase 2.

---

## Implementation Strategy

### MVP

**Fases 1 a 3.** El dashboard con sus seis tarjetas, alcanzable navegando a `/` a mano. Es lo
que responde «¿qué tengo que hacer hoy?», que es el motivo por el que el cliente pidió el
sistema.

### Incremental Delivery

1. **Fase 2** corrige de paso un fallo que ya está en producción (el cruce de año en la
   antigüedad). Tiene valor por sí sola aunque no se siguiera con el resto.
2. **Fase 3** entrega el dashboard.
3. **Fase 4** entrega las alertas: la tarjeta dice cuántos, la alerta dice cuáles.
4. **Fase 5** hace que el dashboard sea lo primero que se ve, que es cuando empieza a usarse
   de verdad.
5. **Fase 6** verifica que cumple los presupuestos y lo despliega.

### Riesgo abierto

El cliente **aún no ha entregado el calendario oficial de feriados**. No bloquea ninguna
tarea: el comportamiento sin cobertura está definido, probado y ahora corregido para el cruce
de años. Pero hasta que llegue, dos de las seis tarjetas mostrarán el aviso en producción en
lugar de una cifra.
