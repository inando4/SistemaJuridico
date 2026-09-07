# Plan de implementación: Control de procedimientos administrativos

**Rama Git actual**: `002-procedimientos-administrativos`, coincidente con la funcionalidad
resuelta por `.specify/feature.json`.

**Especificación**: [spec.md](spec.md) · 24 requisitos y 10 criterios de éxito

**Constitución vigente**: [4.0.1](../../.specify/memory/constitution.md)

## Summary

Se añade el registro de procedimientos administrativos sobre la funcionalidad 001, ya
desplegada en producción. Es la segunda de las dos clases de expediente que tramita el área.

El trabajo es **estrecho a propósito**: dos tablas nuevas, un paquete paralelo al de
expedientes judiciales, cuatro pantallas y una ampliación de la restricción de auditoría. Todo
lo demás —acceso, sesión de jornada, calendario de días hábiles, evaluador de plazos,
auditoría inmutable, permisos, paginación, manejo de errores y separación de roles en base de
datos— **se reutiliza sin tocar**.

La decisión de diseño con más consecuencias es no abstraer lo común con los judiciales. Se
justifica en [research.md](research.md), D1.

## Technical Context

Sin incógnitas: la 001 fijó y validó el stack en producción.

| Aspecto | Decisión |
| --- | --- |
| Lenguaje y plataforma | Java 21, Spring Boot 4.1.1 |
| Vistas | Thymeleaf con HTMX servido localmente; funciona sin JavaScript |
| Persistencia | `JdbcClient` con SQL explícito, sin JPA |
| Base de datos | PostgreSQL 17; Flyway desde **V8** |
| Despliegue | Render con Docker; Supabase en us-east-2, esquema `sistema_juridico` |
| Pruebas | JUnit 5, Testcontainers con PostgreSQL real, MockMvc, Playwright |
| Escala | 197 procedimientos hoy; se dimensiona para 5.000 y cinco usuarios |

**Restricción heredada no negociable**: las migraciones V1 a V7 están aplicadas en producción
y no se modifican jamás. Flyway compara sumas de verificación y cualquier cambio dejaría la
base en estado fallido.

## Constitution Check

| Principio | Cómo lo cumple esta funcionalidad |
| --- | --- |
| I. Idioma según destinatario | Interfaz y mensajes en español; rutas visibles en español como las fija el insumo; identificadores en inglés |
| II. Multiusuario y atribución | Todos consultan todo; ABOGADO edita lo suyo, JEFA cualquiera y queda registrado como intervención |
| III. Índice físico y protección | Sin documentos ni adjuntos; se reutilizan los roles separados de base de datos y el esquema no expuesto |
| IV. Ligereza | Mismo presupuesto que la 001: 6 consultas en listado, 7 en ficha, una lectura de calendario por consulta |
| V. Sin persistencia de derivados | Días restantes, estado de vencimiento y advertencia de fechas se calculan y se descartan |
| VI. Plazos centralizados | Se llama al `DeadlineEvaluator` existente; no se reimplementa la regla ni el calendario |
| VII. Trazabilidad inmutable | Se amplía la restricción de `audit_event` sin tocar filas ni privilegios; la aplicación conserva solo SELECT e INSERT |
| VIII. Un solo proceso | Sin servicios nuevos, sin colas, sin procesos aparte |

**Resultado: sin violaciones.** Ninguna decisión de esta funcionalidad requiere justificación
en Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/002-procedimientos-administrativos/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── web.md
└── checklists/
    └── requirements.md
```

### Source Code (repository root)

Se añade un paquete paralelo. Nada de lo existente se mueve ni se renombra.

```text
src/main/java/pe/org/beneficencia/legalcontrol/
├── administrativeprocedure/          ← NUEVO
│   ├── AdministrativeProcedure.java
│   ├── AdministrativeProcedureForm.java
│   ├── ProcedureFilters.java
│   ├── AdministrativeProcedureRepository.java
│   ├── AdministrativeProcedureValidator.java
│   ├── AdministrativeProcedureService.java
│   ├── ProcedureAuthorization.java
│   └── AdministrativeProcedureController.java
├── administrativestatus/             ← NUEVO
│   ├── AdministrativeStatusRepository.java
│   ├── AdministrativeStatusService.java
│   └── AdministrativeStatusController.java
├── judicialcase/                     ← sin cambios
├── calendar/                         ← sin cambios, se reutiliza
├── audit/                            ← sin cambios, se reutiliza
├── access/                           ← sin cambios, se reutiliza
└── shared/                           ← sin cambios, se reutiliza

src/main/resources/
├── db/migration/
│   └── V8__administrative_procedure.sql   ← NUEVA, única migración
└── templates/
    ├── administrative-procedures/    ← NUEVO: list, form, edit, detail, history
    └── administrative-statuses/      ← NUEVO: list
```

**Una sola migración** para las dos tablas y la ampliación de la restricción, para que no
exista un estado intermedio donde la aplicación pueda auditar un tipo que la base rechaza.

## Decisiones y su justificación

Desarrolladas en [research.md](research.md):

- **D1**: duplicar estructura, compartir lógica. Se repiten los recorridos de campos, que son
  distintos en cada entidad; no se repite ninguna regla.
- **D2**: `DROP CONSTRAINT` + `ADD CONSTRAINT` sobre `audit_event`. No toca filas y no
  debilita la inmutabilidad.
- **D3**: tabla propia de referencia histórica de estados, porque la clave foránea no puede
  apuntar a dos catálogos.
- **D4**: mismo presupuesto de consultas que la 001, verificado contando transacciones reales.
- **D5**: numeración de migraciones desde V8; V1–V7 intocables.
- **D6**: rutas visibles en español, como las fija el insumo.

## Deuda de la 001 — corregida antes de empezar

El insumo fija `/judiciales` y `/judiciales/{id}` (§27 y §28), pero la 001 se había construido
con `/judicial-cases`. Se corrigió antes de planificar esta funcionalidad, junto con el resto
de rutas visibles, que estaban en inglés:

| Antes | Ahora |
| --- | --- |
| `/judicial-cases` | `/judiciales` |
| `/non-working-days` | `/dias-no-laborables` |
| `/procedural-statuses` | `/estados-procesales` |
| `/users` | `/usuarios` |
| `/access/redeem` | `/acceso/canjear` |
| `/account/password` | `/cuenta/contrasena` |

`/login` y `/logout` se conservan: son convención del marco de seguridad, se entienden
universalmente, y cambiarlas tocaría la configuración de autenticación sin ganancia para quien
las lee.

**`/dias-no-laborables` no se renombró a `/calendario`** aunque el insumo use esa palabra: la
§31 reserva `/calendario` para la vista de calendario con pendientes y audiencias, que es otra
pantalla de una funcionalidad futura. Ocuparla ahora la dejaría sin sitio.

`RutasSegunInsumoTest` fija esto: falla si aparece una ruta en inglés o si alguna pantalla
invade una ruta que el insumo reserva para otra funcionalidad.

## Complexity Tracking

Sin entradas. Ninguna decisión de diseño se aparta de la constitución.
