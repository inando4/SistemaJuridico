# Plan de implementación: Control de pendientes

**Rama Git actual**: `003-control-pendientes`, coincidente con la funcionalidad resuelta por
`.specify/feature.json`.

**Especificación**: [spec.md](spec.md) · 27 requisitos y 11 criterios de éxito

**Constitución vigente**: [4.0.1](../../.specify/memory/constitution.md)

## Summary

Se añade el registro de pendientes sobre las funcionalidades 001 y 002, desplegadas. El insumo
lo llama «la tabla más importante del sistema» (§8) y es exacto: los expedientes son el índice,
esto es el trabajo diario.

Lo que hace distinta a esta funcionalidad de las dos anteriores no es la entidad, sino las
**acciones con reglas y con vuelta atrás**: cumplir, deshacer un cumplimiento con motivo
obligatorio, declarar no cumplido y que el sistema calcule solo el siguiente día hábil.

Se añaden una entidad, tres catálogos, ocho pantallas y una migración. Todo lo demás —acceso,
calendario, evaluador de plazos, auditoría, permisos, paginación, roles de base de datos— se
reutiliza sin tocar.

**Y se salda una deuda anunciada**: los cinco catálogos del sistema pasan a compartir una base
común. Al planificar la 002 se dejó escrito que con tres repeticiones habría que extraer; con
esta funcionalidad aparecen tres más.

## Technical Context

Sin incógnitas: el stack está validado en producción desde la 001.

| Aspecto | Decisión |
| --- | --- |
| Lenguaje y plataforma | Java 21, Spring Boot 4.1.1 |
| Vistas | Thymeleaf con HTMX local; funciona sin JavaScript |
| Persistencia | `JdbcClient` con SQL explícito, sin JPA |
| Base de datos | PostgreSQL 17; Flyway desde **V9** |
| Despliegue | Render con Docker; Supabase en us-east-2, esquema `sistema_juridico` |
| Pruebas | JUnit 5, Testcontainers con PostgreSQL real, MockMvc, Playwright |
| Escala | Se dimensiona para 5.000 pendientes y cinco usuarios |

**Restricción no negociable**: las migraciones V1 a V8 están en producción y no se modifican.

## Constitution Check

| Principio | Cómo lo cumple |
| --- | --- |
| I. Idioma según destinatario | Interfaz y rutas en español; identificadores en inglés |
| II. Multiusuario y atribución | Responsable fijo al crear; jefatura interviene y queda registrado |
| III. Índice físico y protección | El documento de salida es un dato, no un archivo; roles separados intactos |
| IV. Ligereza | 6 consultas en listados, 8 en ficha; una lectura de calendario y una agregación por pantalla |
| V. Sin persistencia de derivados | Antigüedad, días restantes, tiempo de atención y reprogramaciones se calculan al consultar |
| VI. Plazos centralizados | El siguiente día hábil se añade al evaluador existente; no se duplica el calendario |
| VII. Trazabilidad inmutable | Se reutiliza `audit_event`; la reversión añade entrada y no borra la original |
| VIII. Un solo proceso | Sin servicios nuevos, sin colas |

**Resultado: sin violaciones.**

## Project Structure

### Documentation (this feature)

```text
specs/003-control-pendientes/
├── spec.md · plan.md · research.md · data-model.md · quickstart.md
├── contracts/web.md
└── checklists/requirements.md
```

### Source Code (repository root)

```text
src/main/java/pe/org/beneficencia/legalcontrol/
├── pendingtask/                      ← NUEVO
│   ├── PendingTask.java
│   ├── PendingTaskForm.java
│   ├── PendingTaskFilters.java
│   ├── PendingTaskRepository.java
│   ├── PendingTaskValidator.java
│   ├── PendingTaskService.java       ← las cuatro acciones con sus reglas
│   ├── PendingTaskAuthorization.java
│   └── PendingTaskController.java
├── catalog/                          ← NUEVO: base común de los cinco catálogos
│   ├── CatalogDefinition.java
│   ├── CatalogRepository.java
│   └── CatalogService.java
├── calendar/                         ← se AMPLÍA con siguienteDiaHabil
├── audit/ · access/ · shared/        ← sin cambios
├── judicialcase/ · proceduralstatus/ ← el catálogo migra a la base común
└── administrativeprocedure/ · administrativestatus/   ← idem

src/main/resources/
├── db/migration/V9__pending_task.sql   ← ÚNICA migración
└── templates/
    ├── pending-tasks/     ← list, today, completed, form, edit, detail, history
    └── catalogs/          ← una plantilla para los cinco catálogos
```

**Una sola migración** para la entidad, los tres catálogos, la tabla de referencia y la
ampliación de la restricción de auditoría, para que no exista un estado intermedio.

## Decisiones y su justificación

Desarrolladas en [research.md](research.md):

- **D1**: reutilizar `audit_event` en vez de crear `historial_pendientes`. Todas las columnas
  que pide la §13 tienen equivalente, y la inmutabilidad ya está garantizada por la base.
- **D2**: dos claves foráneas anulables más `CHECK` de exclusión mutua para el vínculo triple.
- **D3**: un método por acción, no una máquina de estados. Las reglas difieren en cada una.
- **D4**: `siguienteDiaHabil` se añade al evaluador existente y respeta la regla de cobertura.
- **D5**: ningún valor derivado se persiste; las reprogramaciones se cuentan en una agregación.
- **D6**: se extrae la base común de los cinco catálogos, **al final** y con las pruebas de las
  001 y 002 como red.
- **D7**: migraciones desde V9; V1–V8 intocables.
- **D8**: rutas del insumo para las tres pantallas que lo fijan.

## Riesgo principal

La refactorización de catálogos (D6) toca código de las funcionalidades 001 y 002, **ya
desplegadas**. Va deliberadamente en la fase de cierre, cuando los tres catálogos nuevos ya
funcionan, y se apoya en las pruebas existentes: si algo se rompe, lo dicen antes de producción.

Si al llegar allí el riesgo pareciera mayor que el beneficio, es la única parte del plan que se
puede omitir sin dejar la funcionalidad incompleta.

## Complexity Tracking

Sin entradas. Ninguna decisión se aparta de la constitución.
