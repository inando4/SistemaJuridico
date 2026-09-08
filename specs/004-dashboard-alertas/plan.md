# Implementation Plan: Dashboard y sistema de alertas

**Branch**: `004-dashboard-alertas` | **Date**: 2026-09-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-dashboard-alertas/spec.md`

## Summary

Dos pantallas de lectura sobre datos que ya existen: un dashboard con seis tarjetas de
resumen en la raíz del sitio, y una lista de alertas ordenada en cinco niveles de urgencia.
Ninguna tabla nueva, ninguna migración.

La decisión que sostiene todo el diseño es **dónde se cuentan los días hábiles**. La
constitución exige una función única para ese cálculo (principio VI) y prohíbe que una
pantalla haga una consulta por fila (principio IV). Ambas se cumplen a la vez calculando en
Java las **fechas frontera** —cuál es el tercer día hábil desde hoy, y qué fecha queda quince
días hábiles atrás— y dejando que el SQL solo compare fechas. Así el dashboard se resuelve en
una sola consulta con seis agregaciones condicionales, sin que ninguna lógica de días hábiles
se duplique fuera de `DeadlineEvaluator`.

## Technical Context

**Language/Version**: Java 21 (`release=21`), compilado con JDK 25.

**Primary Dependencies**: Spring Boot 4.1.1, Thymeleaf, HTMX servido localmente. Sin
framework de JavaScript. Sin dependencias nuevas.

**Storage**: PostgreSQL 17 vía `JdbcClient` con SQL explícito. Sin JPA. **Sin migración
nueva**: esta funcionalidad no crea ni altera ninguna tabla.

**Testing**: JUnit 5 con Testcontainers y PostgreSQL real (H2 prohibido). Playwright para el
recorrido con navegador. `ContadorDeConsultas` para el presupuesto de consultas.

**Target Platform**: Render (contenedor Docker) con Supabase en us-east-2, esquema
`sistema_juridico`.

**Project Type**: Aplicación web de un solo proceso que sirve HTML.

**Performance Goals** *(presupuestos exigidos por el principio IV)*:

| Pantalla | Consultas | p95 servidor | Volumen |
|---|---|---|---|
| `/` (dashboard) | ≤ 4 | 300 ms | 5.000 pendientes |
| `/alertas` | ≤ 5 | 400 ms | 5.000 pendientes |

**Constraints**:

- Equipo de referencia: laptop modesta del área (la misma con la que el Excel resulta lento).
- Conexión: la del área, con la base en otra región; cada viaje de ida y vuelta cuesta.
- El coste de ambas pantallas **no puede crecer con el número de pendientes**: seis tarjetas
  no son seis consultas, y cinco niveles de alerta no son cinco consultas.
- Sin JavaScript la página debe seguir siendo legible y navegable.

**Scale/Scope**: 5 usuarios, dos pantallas nuevas, sin entidades nuevas.

## Constitution Check

*GATE: debe pasar antes de la fase 0 y volver a comprobarse tras la fase 1.*

| Principio | Cómo lo cumple este plan | Estado |
|---|---|---|
| **I. Idioma según destinatario** | Interfaz íntegra en español, incluidos nombres de tarjetas y tipos de alerta. Se amplía `InterfazEnEspanolTest`. | ✅ |
| **II. Multiusuario y atribución** | Las pantallas filtran por el responsable de la sesión. No escriben nada, así que no hay atribución que registrar. | ✅ |
| **III. Índice físico y protección de datos** | No introduce datos personales nuevos ni cambia el modelo. Las pruebas usan datos sintéticos. | ✅ |
| **IV. Ligereza** | Presupuestos definidos arriba y verificados con `ContadorDeConsultas` y una prueba de rendimiento con 5.000 filas. Una consulta para las seis tarjetas; una para las alertas. | ✅ |
| **V. Sin persistencia de derivados** | Ninguna cuenta se almacena: todas se calculan al abrir la pantalla. Es el principio que esta funcionalidad ejerce de forma más directa. | ✅ |
| **VI. Plazos hábiles centralizados** | Toda la aritmética de días hábiles vive en `DeadlineEvaluator`. El SQL recibe fechas ya resueltas y solo compara. Ver «Decisión 1» en [research.md](research.md). | ✅ |
| **VII. Trazabilidad inmutable** | Ambas pantallas son de solo lectura; FR-019 exige que no escriban historial y hay un criterio de aceptación que lo comprueba. | ✅ |
| **VIII. Un solo proceso** | Dos controladores más en la misma aplicación. Sin servicios externos, sin trabajos en segundo plano, sin caché. | ✅ |

**Sin desviaciones que justificar.** La tabla de complejidad queda vacía.

Un punto que el principio VI obliga a concretar —«cómo se comprueba la cobertura anual y cómo
se tratan períodos que cruzan años»— destapó un fallo **ya existente en producción**, no
introducido por esta funcionalidad. Está descrito en la decisión 2 de
[research.md](research.md) y se corrige aquí porque el dashboard lo necesita igualmente.

## Project Structure

### Documentation (this feature)

```text
specs/004-dashboard-alertas/
├── spec.md
├── plan.md              # este archivo
├── research.md          # las cuatro decisiones de diseño
├── data-model.md        # qué se lee y cómo se clasifica (sin tablas nuevas)
├── quickstart.md        # cómo validar que funciona
├── contracts/
│   └── pantallas.md     # rutas, parámetros y qué garantiza cada una
└── checklists/
    └── requirements.md
```

### Source Code (repository root)

```text
src/main/java/pe/org/beneficencia/legalcontrol/
├── dashboard/                       # NUEVO
│   ├── DashboardController.java     # GET /
│   ├── DashboardRepository.java     # las seis cuentas en una consulta
│   ├── ResumenDelDia.java           # las seis cifras, o el aviso de calendario
│   ├── AlertController.java         # GET /alertas
│   ├── AlertRepository.java         # los cinco niveles en una consulta
│   └── NivelDeAlerta.java           # el enum de los cinco niveles
├── calendar/
│   ├── DeadlineEvaluator.java       # + sumarDiasHabiles / restarDiasHabiles
│   └── CalendarRepository.java      # + snapshot que incluye el año anterior
├── pendingtask/
│   └── PendingTaskFilters.java      # + filtro por nivel de alerta
└── access/
    └── SesionIniciada.java          # redirige a / en vez de /judiciales

src/main/resources/templates/
├── dashboard/
│   ├── index.html                   # las seis tarjetas
│   └── alerts.html                  # la lista por niveles
└── fragments/                       # + enlace al dashboard en la navegación

src/test/java/pe/org/beneficencia/legalcontrol/
├── integration/
│   ├── DashboardIT.java             # las seis cuentas y su exactitud
│   ├── AlertLevelsIT.java           # los cinco niveles, sin duplicados
│   └── DashboardQueryBudgetIT.java  # el presupuesto de consultas
├── calendar/
│   └── CoberturaAnualTest.java      # cruce de años hacia atrás y adelante
└── acceptance/
    ├── DashboardPerformanceTest.java
    └── RutasSegunInsumoTest.java     # / y /alertas dejan de estar reservadas
```

**Structure Decision**: paquete `dashboard` propio, junto a los demás por funcionalidad
(`judicialcase`, `administrativeprocedure`, `pendingtask`, `calendar`), que es como está
organizado el proyecto. Las alertas viven ahí y no en `pendingtask` porque son una lectura
transversal: clasifican pendientes por urgencia, no gestionan su ciclo de vida.

## Complexity Tracking

Sin desviaciones de la constitución. Tabla vacía a propósito.
