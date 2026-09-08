# Plan de implementación: Trabajo en equipo — vista de carga y asignación

**Rama**: `005-equipo-asignacion` | **Fecha**: 2026-09-08 | **Spec**: [spec.md](spec.md)

**Entrada**: especificación de `specs/005-equipo-asignacion/spec.md`

## Resumen

Dos capacidades sobre datos que ya existen: **cambiar de responsable** un expediente (con todos sus pendientes) o un pendiente suelto, y una **vista de carga por persona** que da criterio para hacerlo.

El enfoque técnico se apoya en tres hechos comprobados del código actual:

1. **No hace falta migración.** `audit_event.action` es texto libre y el `CHECK` de `entity_type` que dejó la V9 ya admite `JUDICIAL_CASE`, `ADMINISTRATIVE_PROCEDURE` y `PENDING_TASK`. La acción nueva `REASSIGN` entra sin tocar el esquema.
2. **La vista de equipo es una sola consulta** con `count(*) FILTER`, agrupando por responsable — la misma forma que `DashboardRepository` ya mide en 13 ms sobre 5.000 filas. El número de consultas no depende del tamaño del equipo.
3. **La reasignación son tres sentencias en una transacción**: leer los pares (pendiente, responsable actual), actualizar en bloque, escribir el historial en bloque. O(1) viajes con independencia de cuántos pendientes cuelguen.

La frontera de la semana se calcula en Java y se pasa a SQL como fechas ya resueltas, igual que la 004 resolvió las suyas: así el cálculo de días hábiles sigue viviendo en una única función (principio VI) y SQL solo compara fechas.

## Contexto técnico

**Lenguaje/versión**: Java 21 (`release=21`), JDK 25 en local

**Dependencias principales**: Spring Boot 4.1.1, Thymeleaf, HTMX servido localmente. Sin framework de JavaScript.

**Almacenamiento**: PostgreSQL 17 (Supabase en producción, esquema `sistema_juridico`), acceso con `JdbcClient` y SQL explícito. Sin JPA.

**Pruebas**: JUnit 5 + Testcontainers con PostgreSQL real (H2 prohibido por la constitución). Failsafe ejecuta `*IT.java`; Surefire, `*Test.java`. Playwright/Chromium para las de navegador.

**Plataforma objetivo**: Render (Docker), un único JAR autocontenido.

**Tipo de proyecto**: aplicación web de un solo proceso, vistas servidas desde el backend.

**Objetivos de rendimiento**: vista de equipo por debajo de **1,5 s** y con **número fijo de consultas** con independencia del tamaño del equipo (CE-005, CE-006). Reasignación con número fijo de sentencias con independencia de cuántos pendientes se traspasen.

**Restricciones**: equipo lento de referencia y conexión lenta; 5 usuarios y 5.000 pendientes como volumen de medida, el mismo que usó la 004 para poder comparar.

**Escala/alcance**: 5 personas, ~5.000 pendientes, 1 pantalla nueva y 3 fichas modificadas.

**Migración de base de datos**: **ninguna**. Ver `research.md`, decisión 1.

## Comprobación de la constitución

*PUERTA: debe pasar antes de la fase 0 y volver a comprobarse tras la fase 1.*

| Principio | Cómo lo cumple este plan | Verificación |
|---|---|---|
| **I. Idioma según destinatario** | Artefactos y comentarios en español; tablas, columnas y clases en inglés (`ReassignmentService`, `TeamWorkloadRepository`); mensajes de error en español | Revisión de nombres en `contracts/pantallas.md` |
| **II. Multiusuario y atribución** | La vista es consultable por cualquier rol (RF-021); reasignar exige jefa y se comprueba en el servidor, no ocultando el botón (RF-003); el historial ya separa `actor_id` de `owner_id` | `EquipoPermisosIT`, `ReasignacionPermisosIT` |
| **III. Índice físico y datos** | No se añade almacenamiento documental ni superficie nueva de datos | Sin cambios de esquema |
| **IV. Ligereza como aceptación** | Presupuestos fijados abajo y medidos como puerta, no como aspiración | `EquipoQueryBudgetIT`, `ReasignacionQueryBudgetIT` |
| **V. Sin derivados persistidos** | La carga se cuenta al consultar; no hay columna ni tabla de agregados | `NoDerivedTeamColumnsIT` |
| **VI. Plazos con cálculo centralizado** | La antigüedad sin plazo usa `DeadlineEvaluator.restarDiasHabiles`; no se reimplementa | `EquipoSinCalendarioIT` |
| **VII. Trazabilidad inmutable** | Una entrada por registro movido, con responsable anterior real y nuevo; atómica con el cambio; sin entrada si nada cambió | `ReasignacionHistorialIT`, `ReasignacionAtomicaIT` |
| **VIII. Un solo proceso** | Thymeleaf + HTMX en el mismo proceso; sin API separada | Revisión de código |

**Presupuestos medibles** (principio IV; se miden con 5 usuarios y 5.000 pendientes):

| Pantalla / operación | Consultas | Tiempo p95 |
|---|---|---|
| `GET /equipo` | **≤ 4**, e idéntico con 5 que con 15 personas | ≤ 400 ms |
| Reasignar expediente | **≤ 6 sentencias**, e idéntico con 5 que con 50 pendientes | ≤ 500 ms |
| Reasignar pendiente suelto | ≤ 4 sentencias | ≤ 300 ms |

**Resultado de la puerta**: pasa. No hay violaciones que justificar, así que la sección de complejidad queda vacía.

## Estructura del proyecto

### Documentación (esta feature)

```text
specs/005-equipo-asignacion/
├── plan.md              # Este archivo
├── research.md          # Fase 0: decisiones y alternativas descartadas
├── data-model.md        # Fase 1: entidades y consultas
├── quickstart.md        # Fase 1: recorrido de validación
├── contracts/
│   └── pantallas.md     # Fase 1: rutas, parámetros y textos
├── checklists/
│   └── requirements.md  # Calidad de la especificación
└── tasks.md             # Fase 2 (/speckit-tasks, no lo crea este comando)
```

### Código fuente

```text
src/main/java/pe/org/beneficencia/legalcontrol/
├── team/                              # NUEVO
│   ├── TeamController.java            # GET /equipo
│   ├── TeamWorkloadRepository.java    # una consulta con count(*) FILTER
│   └── CargaDeAbogado.java            # registro de solo lectura, sin tabla
├── assignment/                        # NUEVO
│   ├── ReassignmentController.java    # POST de reasignación (3 destinos)
│   ├── ReassignmentService.java       # transacción única, historial incluido
│   └── ReassignmentRepository.java    # snapshot + update en bloque
├── judicialcase/
│   ├── JudicialCaseController.java    # + formulario de reasignación en la ficha
│   ├── JudicialCaseForm.java          # + ownerId opcional en el alta (RF-012)
│   └── JudicialCaseService.java       # + responsable elegido al crear
├── administrativeprocedure/           # los tres cambios equivalentes
├── pendingtask/
│   ├── PendingTaskController.java     # + reasignación individual si no hay vínculo
│   └── PendingTaskRepository.java     # + filtros que usan las tarjetas del equipo
└── access/
    └── UserRepository.java            # + destinatarios activos para el desplegable

src/main/resources/templates/
├── team/list.html                     # NUEVO: la vista de equipo
├── fragments/
│   ├── navegacion.html                # + enlace a /equipo
│   └── reasignacion.html              # NUEVO: formulario reutilizado por las 3 fichas
├── judicial-cases/{detail,form}.html
├── administrative-procedures/{detail,form}.html
└── pending-tasks/detail.html

src/test/java/pe/org/beneficencia/legalcontrol/
├── integration/                       # los IT nombrados en la tabla de arriba
├── unit/SemanaDeTrabajoTest.java      # fronteras de la semana, con fechas absolutas
└── acceptance/RecorridoEquipoTest.java # recorrido de quickstart.md con navegador
```

**Decisión de estructura**: se añaden dos paquetes nuevos (`team` y `assignment`) en lugar de repartir el código entre `judicialcase`, `administrativeprocedure` y `pendingtask`. La reasignación es **una sola operación con tres puntos de entrada**: si vive en cada paquete se escribe tres veces, y la tercera copia es la que se olvida —exactamente el fallo que la 004 tuvo con el quinto sitio del inventario—. `assignment` centraliza la transacción, la comprobación de permisos y el historial; los controladores existentes solo la invocan.

## Seguimiento de complejidad

Sin violaciones de la constitución. Tabla vacía a propósito.

## Nota fuera de alcance

El `REVOKE` pendiente sobre `flyway_schema_history` (hoy concede UPDATE y DELETE al rol de la aplicación sin motivo) **no entra aquí**: esta feature no necesita migración, y crear una V10 cuyo único contenido es un arreglo de permisos la ataría a un cambio funcional con el que no tiene relación. Queda anotado para la primera feature que sí necesite migración propia.
