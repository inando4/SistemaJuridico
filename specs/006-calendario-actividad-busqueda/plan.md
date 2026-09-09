# Plan de implementación: Calendario, actividad diaria y buscador global

**Rama**: `006-calendario-actividad-busqueda` | **Fecha**: 2026-09-08 | **Spec**: [spec.md](spec.md)

## Resumen

Tres pantallas de consulta y **una** entidad nueva.

- **Buscador global** (`/buscar`): tres consultas, una por tipo de registro, cada una paginada por su cuenta. Amplía los campos de texto que hoy no se buscan —materia y observaciones en judiciales, observaciones en administrativos y en pendientes— y **los mismos campos se amplían en los tres listados existentes**, para que la misma palabra no dé resultados distintos según dónde se escriba.
- **Actividad diaria** (`/actividad-diaria`): mitad derivada —los pendientes cumplidos ese día, que no se guardan— y mitad almacenada —la actividad manual, única tabla nueva y primera migración desde la V9—.
- **Calendario** (`/calendario`): una consulta por rango de fechas que devuelve los eventos de las tres tablas, y tres presentaciones (día, semana, mes) sobre el mismo resultado.

El eje técnico de la feature es que **el coste no dependa del tamaño de lo mostrado**: el mes cuesta lo mismo que el día, y trescientos resultados lo mismo que tres.

## Contexto técnico

**Lenguaje**: Java 21 (`release=21`), Spring Boot 4.1.1

**Dependencias principales**: Thymeleaf, HTMX servido localmente, `JdbcClient` con SQL explícito. Sin JPA, sin marco de JavaScript.

**Almacenamiento**: PostgreSQL 17 (Supabase, esquema `sistema_juridico`). Migración **V10**, la primera desde la V9.

**Pruebas**: Testcontainers con PostgreSQL real (H2 prohibido, principio VIII); Surefire para `*Test.java`, Failsafe para `*IT.java` en `verify`; Playwright (Chromium headless) para el recorrido con navegador.

**Plataforma**: contenedor Docker en Render, un solo proceso.

**Presupuestos de rendimiento**: ver la tabla de [quickstart.md](quickstart.md). Se expresan **primero como invariantes** —el número de consultas no cambia al crecer el resultado— y solo después como techo absoluto. En la 005 el techo escrito en el plan (6) resultó ser 7 al medirlo, y fue el documento el que se corrigió, no el código: los techos de aquí son provisionales hasta que los mida `*QueryBudgetIT`; las invariantes no.

**Escala**: 5 personas, volumen de prueba de 5.000 pendientes.

## Comprobación de la constitución

| Principio | Cómo lo cumple esta feature |
|---|---|
| **I. Idioma según destinatario** | Rutas, pantallas y mensajes en español (`/buscar`, `/actividad-diaria`, `/calendario`); esquema y código en inglés (`manual_activity`). |
| **II. Multiusuario y atribución** | Las tres pantallas leen **todo** el área. El filtro «solo lo mío» es comodidad, no permiso. La escritura de una actividad manual la limita RF-020 **en el servidor**, no ocultando el botón. |
| **III. Índice físico y protección de datos** | No se añade ningún dato personal ni ningún archivo. La actividad manual es texto que el usuario escribe sobre su propio trabajo. |
| **IV. Ligereza como requisito de aceptación** | CE-007 y CE-008 son las invariantes de la feature y tienen prueba propia (`AgendaQueryBudgetIT`, `BusquedaQueryBudgetIT`). Sin ellas, un calendario mensual es treinta y una consultas que nadie nota hasta que duelen. |
| **V. Sin persistencia de derivados** | Los pendientes cumplidos del día **no se copian** a ninguna tabla, y no existe ningún resumen diario guardado. Los eventos del calendario tampoco: se leen de los registros que ya tienen esas fechas. Lo único que se escribe es la actividad manual, que **no es derivada de nada**. |
| **VI. Plazos hábiles centralizados** | Esta feature **no calcula ningún día hábil**. Todas sus fechas son fechas guardadas o aritmética de calendario. Solo el sombreado de días no laborables lee `CalendarRepository`, y su ausencia se avisa (RF-027). |
| **VII. Trazabilidad inmutable** | El alta, la modificación y la retirada de una actividad manual pasan por `AuditRecorder`. La V10 amplía el `CHECK` de `audit_event.entity_type` con `MANUAL_ACTIVITY`, igual que hizo la V9. Retirar no borra. |
| **VIII. Un solo proceso** | Ningún servicio nuevo, ninguna dependencia externa nueva. Se descartó `unaccent` en la especificación, y esa decisión se sostiene aquí. |

**Resultado: pasa.** Sin violaciones que justificar.

La única tensión real es la del principio V dentro de la sección 33, y está resuelta por diseño: la mitad automática se calcula al consultar y la manual se persiste porque no tiene otro origen del que derivarse. Ver [research.md](research.md), decisión 3.

## Estructura del proyecto

### Documentación (esta feature)

```text
specs/006-calendario-actividad-busqueda/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/pantallas.md
├── checklists/requirements.md
└── tasks.md          # lo crea /speckit-tasks
```

### Código fuente

```text
src/main/resources/db/migration/
└── V10__manual_activity.sql            # tabla, CHECK de auditoría ampliado, grants, REVOKE pendiente

src/main/java/pe/org/beneficencia/legalcontrol/
├── search/                             # NUEVO — buscador global
│   ├── package-info.java
│   ├── GlobalSearchController.java     # GET /buscar
│   ├── GlobalSearchRepository.java     # tres consultas, una por tipo
│   ├── ResultadoDeBusqueda.java        # referencia mínima a un registro
│   └── GrupoDeResultados.java          # los N mostrados + si hay más
├── activity/                           # NUEVO — actividad diaria y manual
│   ├── package-info.java
│   ├── DailyActivityController.java    # GET /actividad-diaria, POST de alta/edición/retirada
│   ├── ManualActivityRepository.java
│   ├── ManualActivityService.java
│   ├── ManualActivityForm.java
│   ├── ManualActivityValidator.java
│   ├── ManualActivity.java
│   └── ActividadDelDia.java            # la unión; NUNCA se persiste
├── agenda/                             # NUEVO — el calendario de eventos
│   ├── package-info.java
│   ├── AgendaController.java           # GET /calendario
│   ├── AgendaRepository.java           # UNA consulta por rango
│   ├── EventoDeAgenda.java
│   └── RejillaDelMes.java              # reparto en semanas; sin SQL
├── calendar/                           # EXISTE — días no laborables. No se toca salvo lectura
├── judicialcase/JudicialCaseRepository.java        # RF-012: ampliar q
├── administrativeprocedure/…Repository.java        # RF-012: ampliar q
├── pendingtask/PendingTaskRepository.java          # RF-012: ampliar q + consulta de cumplidos del día
└── catalog/CatalogDefinition.java, CatalogRepository.java   # segundo uso del catálogo (research, decisión 1)

src/main/resources/templates/
├── search/results.html
├── activity/day.html, activity/form.html, activity/history.html
├── agenda/calendar.html
└── fragments/navegacion.html           # tres enlaces nuevos
```

**Decisión de estructura**: tres paquetes nuevos, uno por pantalla, siguiendo el criterio de la 005 (`assignment`, `team`).

El calendario va en **`agenda`, no en `calendar`**, a propósito. El paquete `calendar` ya existe y significa otra cosa: el calendario **de días no laborables**, con `CalendarController` sirviendo `/dias-no-laborables`. Meter ahí un segundo controlador para `/calendario` dejaría dos clases de nombre casi idéntico haciendo cosas sin relación —una decide qué días cuentan para un plazo, la otra dibuja una rejilla— y sería una confusión permanente. La ruta que ve el usuario sigue siendo `/calendario`, que es lo que pide el insumo.

## Seguimiento de complejidad

Sin violaciones de la constitución que justificar.

## Nota fuera de alcance

La V10 aprovecha el viaje para el `REVOKE UPDATE, DELETE, TRUNCATE ON flyway_schema_history FROM sistema_juridico_app`, anotado en la investigación de la 005 como pendiente de la primera migración que hiciera falta. Se comprobó que es seguro: `application.yml` deja Flyway en `enabled: false` y le da una credencial propia (`DB_MIGRATION_*`), de modo que el rol de la aplicación no ejecuta migraciones y quitarle esos permisos no puede romper el arranque.

Los **recordatorios** de la sección 31 siguen fuera, por lo dicho en la especificación: el insumo los sitúa en la FASE 3 y no los define en ninguna parte.
