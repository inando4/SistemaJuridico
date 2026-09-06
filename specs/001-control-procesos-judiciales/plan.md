# Plan de implementación: Acceso y control de procesos judiciales con plazos

**Rama Git actual**: `main` | **Fecha**: 2026-09-06 | **Spec**: [spec.md](spec.md)

**Entrada**: `specs/001-control-procesos-judiciales/spec.md`, 30 requisitos, 12 criterios de
éxito y 3 aclaraciones aceptadas. Constitución vigente: 4.0.1.
**Estado**: Fases 0 y 1 completadas; diseño listo para descomposición en tareas.
**Identificador de funcionalidad**: `001-control-procesos-judiciales`. El script devuelve
ese identificador como BRANCH al resolver `feature.json`; `git branch --show-current`
confirma `main`. No se creó ni cambió una rama durante este comando.

## Resumen

Construir una aplicación web ligera para cinco integrantes del área jurídica. Java y
Spring Boot sirven páginas Thymeleaf y fragmentos HTMX desde un único JAR; PostgreSQL
almacena procesos físicos indexados, cuentas, calendario y evidencia de modificaciones.
Acceso compartido de lectura, escritura propia para ABOGADO y global para JEFA.

La implementación incorpora sesiones de jornada —4 h de inactividad y 12 h absolutas—,
altas y recuperación por
código entregado en mano, desactivación/reactivación, protección de la última JEFA, estados procesales
administrables y un evaluador único de días hábiles. Fechas y estados temporales se calculan
al consultar. No documentos, importación de Excel, pendientes, asignación ni API separada.

## Contexto técnico

**Lenguaje/versión**: Java 21; identificadores ingleses; texto de producto y documentación en español.

**Dependencias principales**: Spring Boot 4.1.1 BOM; MVC, Thymeleaf, Security,
JDBC `JdbcClient`, Validation, Flyway/PostgreSQL, Argon2/Bouncy Castle; HTMX 2.0.10 local.
Maven Wrapper 3.9.11. Versiones verificadas y alternativas en [research.md](research.md).

**Almacenamiento**: PostgreSQL 17, alineado con la versión mayor documentada de Supabase.
Confirmar el parche del proyecto con `SHOW server_version` antes de fijar la imagen de pruebas.
Migraciones versionadas; roles DB runtime/migración
separados. SQL explícito, sin JPA. Ningún almacén documental ni caché de derivados de negocio.

**Pruebas**: JUnit Jupiter, Spring Boot Test/MockMvc, Testcontainers PostgreSQL, AssertJ,
Playwright Java para navegador real. Sin buzón de correo: no hay envíos que probar.
Dependencias de test
compatibles se fijan mediante BOM; Playwright Java 1.58.0 como herramienta de prueba.

**Plataforma objetivo**: Render aloja el JAR; Supabase aloja PostgreSQL, ambos en la misma
región para minimizar latencia. Linux, servidor embebido y HTTPS. **Sin SMTP ni ninguna
integración externa de negocio**: los códigos de acceso se muestran en pantalla a JEFA y se
entregan presencialmente.
Navegadores Chromium/Firefox estables; no instalar software cliente ni depender de CDN.

**Tipo de proyecto**: Aplicación web monolítica de un módulo Maven, organizada por funcionalidad.

**Objetivos de rendimiento**: p95 de listado/ficha/formulario ≤1 s; guardado ≤2 s, cinco
usuarios y 5.000 procesos, laptop 2 núcleos/4 GB, enlace 2 Mbps/150 ms. Una petición por
interacción HTMX; recursos y metodología en [contrato operativo](contracts/operations.md).

**Restricciones**: Java/Spring Boot/PostgreSQL/Thymeleaf/HTMX, un JAR; sin framework JS,
API REST separada, servicio Redis ni almacén de sesiones en base de datos.
Toda la configuración en YAML; sin `.properties` de configuración.
Perfil `local` contra PostgreSQL en Docker sobre localhost; perfil `prod` contra Supabase.
Arrancar sin perfil activo falla con mensaje claro y nunca conecta a producción. Autorización transaccional y auditoría atómica,
solo mutaciones. No contar hoy ni desplazar fecha límite por zona horaria.

**Escala/alcance**: Siete historias de usuario. Modelo inicial admite historial paginado;
no se diseña escalado horizontal. Respaldos diarios verificados, RPO 24 h/RTO 4 h iniciales.

**Estado del repositorio**: Al comenzar solo había documentos y scripts de Spec Kit.
Se ejecutó Graphify `--code-only` (13 scripts, sin aplicación Java ni vistas); la consulta
de aplicación no encontró nodos coincidentes. No se reutiliza un backend inexistente.

## Comprobación de la constitución

Puerta previa a investigación y posterior a diseño, ambas evaluadas contra 4.0.1:

| Principio | Evidencia de diseño | Antes | Después |
| --- | --- | --- | --- |
| I. Idioma | Documentos españoles, tablas/campos/clases ingleses; etiquetas ABOGADO/JEFA | Cumple | Cumple |
| II. Multiusuario | Permisos por actor/propietario, dos roles, autor y responsable separados | Cumple | Cumple |
| III. Índice y datos | Sin archivos; TLS y credenciales fuera del repositorio; sin panel ni API de datos públicos; restauración obligatoria | Cumple | Cumple |
| IV. Ligereza | HTML/HTMX local, presupuestos de solicitudes/tamaño/SQL y medición física | Cumple | Cumple |
| V. Lógica activa | DTO de plazo y totales calculados por consulta; sin columnas derivadas | Cumple | Cumple |
| VI. Días hábiles | Evaluador único, snapshot del calendario, revisión anual y aviso de ausencia | Cumple | Cumple |
| VII. Trazabilidad | Cambios y auditoría juntos; evidencia inmutable; GET/no-op con cero entradas | Cumple | Cumple |
| VIII. Arquitectura | Un JAR Java/Spring Boot, PostgreSQL, Thymeleaf y HTMX | Cumple | Cumple |

La entrega parcial que difiere asignación y vista de equipo fue autorizada explícitamente
en la spec; no se implementan esas capacidades en 001. Las revisiones técnicas de filas,
metadatos de sesión y controles de acceso no son indicadores de negocio. No se persistirán
conteos anuales ni etiquetas temporales. El respaldo es operación de datos, no carga de
archivos del producto. La corrección documental elimina referencias obsoletas a constitución
2.0.0 sin cambiar las decisiones funcionales aprobadas.

## Estructura del proyecto

### Documentación de esta funcionalidad

```text
specs/001-control-procesos-judiciales/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── web.md
│   └── operations.md
└── checklists/requirements.md
```

`tasks.md` corresponde al siguiente comando y no se genera en planificación.

### Código fuente previsto en la raíz

```text
pom.xml
mvnw
.mvn/wrapper/
src/main/java/pe/org/beneficencia/legalcontrol/
├── LegalControlApplication.java
├── config/              # MVC, seguridad, reloj y JDBC
├── access/              # cuentas, sesiones, tokens, límites y bootstrap
├── judicialcase/        # formularios, permisos, repositorios y vistas de procesos
├── proceduralstatus/    # catálogo de estados
├── calendar/            # días, revisiones y DeadlineEvaluator
├── audit/               # evidencia inmutable y consulta paginada
└── shared/              # errores, paginación y adaptación HTML/HTMX
src/main/resources/
├── application.yml
├── application-local.yml
├── application-prod.yml
├── db/migration/
├── templates/{layout,access,users,judicial-cases,procedural-statuses,calendar,error}/
├── static/{css,js,vendor}/
└── messages_es.properties
src/test/java/pe/org/beneficencia/legalcontrol/
├── unit/
├── integration/
├── web/
└── acceptance/
src/test/resources/
└── fixtures/
```

**Decisión de estructura**: Un módulo Maven con controlador, servicio, repositorio y
objetos de entrada en cada funcionalidad. Evitar capas genéricas de CRUD que oculten los
permisos. No crear esta estructura hasta implementación. Los tests no usan datos personales reales.

## Fase 0 — Investigación completada

[research.md](research.md) resuelve versiones, sesiones, revocación, tokens, última JEFA,
entrega de códigos, unicidad, evidencia, calendario, HTMX y operación. Se consultaron fuentes oficiales
y se consolidaron dos investigaciones paralelas sobre acceso y persistencia.

Decisiones que evitan trabajo posterior:

- No basta la expiración por inactividad: guardar inicio de autenticación y cortar a 12 h
  absolutas, además de 4 h sin actividad.
- La revocación lógica de acceso comparte transacción con la cuenta y se comprueba por
  `auth_version` en cada petición; no depende de borrar sesión alguna.
- Un único bloqueo global de cuentas protege a la última JEFA; no basta un COUNT previo.
- Cobertura anual es evidencia de revisión frente a versión del año; una cantidad no prueba
  revisión. Cero bloquea, 1–4 advierte y exige reconocimiento, 5+ aún requiere declaración.
- El catálogo puede borrar estados nunca usados aunque tengan auditoría propia; referencias
  del historial de procesos sí impiden borrar.
- Restablecer un respaldo invalida todos los códigos y sesiones recuperados antes de abrir red.

## Fase 1 — Diseño completado

- [Modelo de datos](data-model.md): entidades, campos, índices, validaciones, evidencia,
  transiciones, orden de bloqueos y límites de consulta.
- [Contrato web](contracts/web.md): rutas HTML, formularios, filtros, permisos, HTTP,
  fragmentos y recuperación frente a errores sin API JSON separada.
- [Contrato operativo](contracts/operations.md): configuración, bootstrap,
  presupuestos, observabilidad, copias y restauración.
- [Guía de validación](quickstart.md): comandos previstos y recorridos reproducibles
  para demostrar aceptación después de implementar.

### Orden de implementación para descomponer en tareas

1. Crear módulo Maven, perfiles, esquema, migraciones y privilegios; fijar reloj y tratamiento
   de errores. Agregar pruebas de PostgreSQL real y bootstrap de la primera JEFA.
2. Implementar acceso, sesiones y revocación; códigos de activación y restablecimiento y cambio de contraseña propia; desactivación,
   reactivación y concurrencia de última JEFA. Establecer evidencia de cuenta sin secretos.
3. Crear catálogo y procesos con validación, unicidad global, versiones y permisos; ficha,
   filtros y listado compartido; snapshots y prohibición de mutar evidencia.
4. Implementar días no laborables, confirmación anual y evaluador único; integrar
   interpretaciones de fechas en ficha/listado y todos los casos límite de spec.
5. Completar adaptación HTMX, accesibilidad y recorridos end-to-end; medir presupuestos
   con datos sintéticos y red lenta; ensayar restauración antes de aceptación operativa.

No se genera ni ejecuta implementación en este comando.

### Matriz de trazabilidad y verificación prevista

| Requisitos | Diseño | Verificación requerida |
| --- | --- | --- |
| FR-001–002 | access + sesión en memoria | login/logout, 4 h inactividad y 12 h absolutas con reloj controlado, CSRF, revocación y cierre por reinicio |
| FR-003–006 | judicial_case + unique | alta manual mínima/completa, rango, duplicado concurrente |
| FR-007–010 | web/SQL/permisos | filtros combinados, ambos roles, edición propia/ajena y peticiones directas |
| FR-011–013 | audit + version | cero historial por GET/no-op, rollback y conflicto de edición |
| FR-014–016 | DeadlineEvaluator | 12 ejemplos, propiedades contra enumerador, medianoche y zonas |
| FR-017–020 | calendar_year/review | años 0/1/4/5 entradas, mover año, revisión concurrente, invalidez |
| FR-021 | HTML y budgets | español, teclado, foco, red fallida, tamaños y p95 |
| FR-022 | operations | privacidad DB, restore aislado e integridad de evidencia |
| FR-023 | active vs status | cuatro combinaciones; cambio independiente e historial |
| FR-024–026, FR-025b | access/códigos | código mostrado una sola vez, canje único y concurrente, límites de intento y de generación, cambio de contraseña propia sin código |
| FR-027–028 | catálogo/referencias | sin uso borrable, uso histórico protegido, snapshots legibles |
| FR-029–030 | guard + cuentas | desactivar/reactivar, misma cuenta, nueva contraseña y última JEFA |

### Puertas de aceptación

Unitarias exactas para fechas, dominio y expiraciones; integración real para restricciones,
transacciones y concurrencia; MockMvc/Playwright para contratos y sesiones. No sustituir
PostgreSQL por H2. Las mediciones y restauración complementan los tests automáticos.
La evidencia de cada SC-001–012 debe poder vincularse a un recorrido de quickstart.

El plan no afirma rendimiento ni pruebas aprobadas sobre software inexistente. No quedan
aclaraciones bloqueantes; secretos/dominio/proveedor y operador nominal se provisionan
según contrato antes de producción. Conservar aprobación del calendario real por JEFA.

## Seguimiento de complejidad

No hay incumplimientos constitucionales que justificar. PostgreSQL aloja también sesiones
y controles técnicos; no se añaden Redis, broker, SPA, almacenamiento documental ni servicios
independientes. El JS propio se limita a adaptación y accesibilidad; sin lógica de negocio.
