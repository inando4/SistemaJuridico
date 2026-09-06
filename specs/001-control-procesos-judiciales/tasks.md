---

description: "Lista de tareas para la funcionalidad 001"
---

# Tareas: Acceso y control de procesos judiciales con plazos

**Entrada**: documentos de diseño en `/specs/001-control-procesos-judiciales/`

**Prerrequisitos**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/)

**Constitución vigente**: 4.0.1

**Sin correo**: el sistema no envía correo ni integra SMTP; los códigos de acceso se muestran una sola vez a JEFA y se entregan en mano.

**Pruebas**: SÍ se generan tareas de prueba. La spec define 12 criterios de éxito medibles y el plan establece puertas de aceptación explícitas (unitarias exactas de fechas, integración real contra PostgreSQL, MockMvc y Playwright). No sustituir PostgreSQL por H2.

**Organización**: por historia de usuario, para poder implementar y validar cada una por separado.

## Formato: `[ID] [P?] [Historia] Descripción`

- **[P]**: puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Historia]**: US1..US7 según [spec.md](spec.md)
- Todas las rutas son relativas a la raíz del repositorio

## Convenciones de rutas

Paquete base `pe.org.beneficencia.legalcontrol` en `src/main/java/pe/org/beneficencia/legalcontrol/`, organizado por funcionalidad: `config/`, `access/`, `judicialcase/`, `proceduralstatus/`, `calendar/`, `audit/`, `shared/`. Pruebas en `src/test/java/pe/org/beneficencia/legalcontrol/{unit,integration,web,acceptance}/`.

---

## Fase 1: Preparación (infraestructura compartida)

**Propósito**: dejar el proyecto compilable, arrancable en local y con las reglas de configuración que exige la constitución.

- [X] T001 Crear módulo Maven con `pom.xml` (Java 21, BOM de Spring Boot 4.1.1, MVC, Thymeleaf, Security, JDBC, Validation, Flyway, PostgreSQL, Argon2/Bouncy Castle; sin starter de Mail) y `mvnw` con Maven Wrapper 3.9.11, sin `latest` en ninguna versión
- [X] T002 Verificar que cada versión fijada en `pom.xml` resuelve realmente en el repositorio de artefactos antes de continuar, y corregir en `specs/001-control-procesos-judiciales/research.md` cualquier versión que no exista
- [X] T003 Crear `src/main/java/pe/org/beneficencia/legalcontrol/LegalControlApplication.java` con el arranque del contexto
- [X] T004 [P] Crear `src/main/resources/application.yml` con la configuración común, en YAML; no se admiten `.properties` de configuración
- [X] T005 [P] Crear `src/main/resources/application-local.yml` apuntando exclusivamente a PostgreSQL en `localhost`, de modo que un error de configuración no pueda alcanzar la base real
- [X] T006 [P] Crear `src/main/resources/application-prod.yml` sin credenciales ni URL literales: solo referencias a variables de entorno, exigiendo que todas estén presentes
- [X] T007 Implementar en `src/main/java/pe/org/beneficencia/legalcontrol/config/ProfileGuard.java` el fallo de arranque con mensaje claro en español cuando no hay perfil activo, para que nunca se conecte a producción por omisión, y verificar que `prod` no se active de forma implícita
- [X] T008 [P] Crear `docker-compose.yml` en la raíz con PostgreSQL 17 para el perfil `local`, fijando el tag de parche exacto e igualando la versión mayor de producción
- [X] T009 [P] Crear `.gitignore` excluyendo `*.bak*`, `target/`, archivos de entorno y cualquier archivo de secretos
- [X] T010 [P] Crear `src/main/resources/messages_es.properties` con el paquete de traducciones y configurar el `MessageSource` en `src/main/java/pe/org/beneficencia/legalcontrol/config/WebConfig.java`
- [X] T011 [P] Descargar HTMX 2.0.10 a `src/main/resources/static/vendor/htmx.min.js` y servirlo localmente; ninguna página puede depender de un CDN
- [X] T012 [P] Crear la hoja de estilo mínima en `src/main/resources/static/css/app.css`, sin framework de JavaScript ni carga gráfica innecesaria
- [X] T013 Configurar Flyway en `src/main/resources/application.yml` con credencial de migración separada (`DB_MIGRATION_*`), de forma que la credencial de runtime no pueda modificar el esquema
- [X] T014 [P] Crear la clase base de pruebas de integración en `src/test/java/pe/org/beneficencia/legalcontrol/integration/PostgresIntegrationTest.java` con Testcontainers y PostgreSQL 17 real; prohibido H2

**Punto de control**: el proyecto compila, arranca con `--spring.profiles.active=local` contra el PostgreSQL de Docker, y falla con mensaje claro si no se indica perfil.

---

## Fase 2: Fundacional (prerrequisitos bloqueantes)

**Propósito**: esquema, reloj, seguridad de sesión, auditoría y arranque de la primera cuenta. Ninguna historia puede empezar antes.

**⚠️ CRÍTICO**: nada de la Fase 3 en adelante puede comenzar hasta terminar esta fase.

- [X] T015 Crear la migración inicial `src/main/resources/db/migration/V1__schema_base.sql` con los esquemas y los privilegios separados de los roles de migración y de runtime, según [data-model.md](data-model.md)
- [X] T016 [P] Crear `src/main/resources/db/migration/V2__app_user.sql` con `app_user` (identidad, correo único, credencial, rol `LAWYER`/`HEAD`, condición, `auth_version`, `version`, tiempos)
- [X] T017 [P] Crear `src/main/resources/db/migration/V3__access.sql` con `access_guard`, `access_token` y `auth_attempt`
- [X] T018 [P] Crear `src/main/resources/db/migration/V4__judicial_case.sql` con `judicial_case`, su unicidad normalizada de número de expediente, `version` y los índices de los filtros del listado (responsable, estado procesal, fecha límite, condición activa)
- [X] T019 [P] Crear `src/main/resources/db/migration/V5__procedural_status.sql` con `procedural_status` y su unicidad de nombre
- [X] T020 [P] Crear `src/main/resources/db/migration/V6__calendar.sql` con `calendar_year`, `non_working_day` (fecha única) y `calendar_review`
- [X] T021 [P] Crear `src/main/resources/db/migration/V7__audit.sql` con `audit_event` y `case_history_status_reference`, sin permisos de UPDATE ni DELETE para el rol de runtime
- [X] T022 Verificar en `src/test/java/pe/org/beneficencia/legalcontrol/integration/SchemaMigrationIT.java` que las migraciones se aplican sobre PostgreSQL real y que el rol de runtime no puede alterar el esquema ni modificar `audit_event`
- [X] T023 Implementar el reloj inyectable en `src/main/java/pe/org/beneficencia/legalcontrol/config/ClockConfig.java` fijado a la hora local de Arequipa, para que las pruebas controlen «hoy» sin depender del reloj del sistema
- [X] T024 Configurar la seguridad en `src/main/java/pe/org/beneficencia/legalcontrol/config/SecurityConfig.java`: formulario de acceso, CSRF activo, cookie `HttpOnly`/`Secure`/`SameSite=Lax`/`Path=/` sin `Domain`, y sesión **en memoria del contenedor**; sin Spring Session ni almacén en base de datos
- [X] T025 Configurar `server.servlet.session.timeout=4h` en `src/main/resources/application.yml` e implementar el corte absoluto de 12 h en `src/main/java/pe/org/beneficencia/legalcontrol/access/AbsoluteSessionFilter.java`, comprobando `authenticated_at` en cada petición sin que la actividad renueve el límite
- [X] T026 Implementar la comprobación de `auth_version` por petición en `src/main/java/pe/org/beneficencia/legalcontrol/access/AuthVersionFilter.java`, de modo que la revocación surta efecto sin borrar sesión alguna
- [X] T027 Implementar el registro de auditoría inmutable en `src/main/java/pe/org/beneficencia/legalcontrol/audit/AuditRecorder.java`, escribiendo únicamente en modificaciones efectivas, en la misma transacción que el cambio, con autor, responsable de entonces, valores anterior y nuevo, y momento
- [X] T028 Verificar en `src/test/java/pe/org/beneficencia/legalcontrol/integration/AuditImmutabilityIT.java` que una consulta no genera ninguna entrada, que un guardado sin cambios tampoco, y que un fallo al auditar revierte también la modificación
- [X] T029 [P] Implementar el manejo de errores y las páginas de error en `src/main/java/pe/org/beneficencia/legalcontrol/shared/ErrorHandling.java` y `src/main/resources/templates/error/`, con mensajes en español y sin filtrar detalles internos
- [X] T030 [P] Implementar la paginación compartida en `src/main/java/pe/org/beneficencia/legalcontrol/shared/Paging.java` con páginas de 25 y página fuera de rango recuperable
- [X] T031 [P] Implementar la adaptación HTML/HTMX en `src/main/java/pe/org/beneficencia/legalcontrol/shared/HtmxSupport.java`, devolviendo fragmento o página completa según la petición, con una sola petición por interacción
- [X] T032 [P] Crear la plantilla base en `src/main/resources/templates/layout/base.html` con identidad y rol visibles, navegación mínima y carga local de HTMX
- [X] T033 Implementar el comando de arranque en `src/main/java/pe/org/beneficencia/legalcontrol/access/BootstrapCommand.java`: modo CLI del mismo JAR, sin servidor web, que crea la primera cuenta JEFA pendiente y **imprime su código una sola vez** por la salida del comando, única excepción a la regla, y solo con la tabla de usuarios vacía
- [X] T034 Verificar en `src/test/java/pe/org/beneficencia/legalcontrol/integration/BootstrapIT.java` que el arranque solo opera con la tabla vacía, que permite regenerar el código mientras no exista cuenta activa, y que el código no queda en logs ni en la base en claro

**Punto de control**: esquema aplicado, sesión y auditoría operativas, primera cuenta JEFA creable. Las historias pueden comenzar.

---

## Fase 3: Historia 1 — Acceder de forma identificada (P1) 🎯 MVP

**Objetivo**: que un integrante entre con su cuenta y opere con las facultades de su rol.

**Prueba independiente**: con cuentas de ambos roles preparadas, probar ingreso, rechazo, caducidad y cierre de sesión, sin necesitar procesos existentes.

### Pruebas

- [ ] T035 [P] [US1] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/web/LoginContractTest.java` con MockMvc: credenciales correctas, incorrectas y cuenta inactiva, comprobando que el rechazo es genérico y no revela si la cuenta existe
- [ ] T036 [P] [US1] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/AccessLifecycleIT.java` con reloj controlado: 4 h de inactividad, 12 h absolutas aun con actividad continua, cierre de sesión, y pérdida de sesión al reiniciar el contexto
- [ ] T037 [P] [US1] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/web/CsrfContractTest.java` verificando que toda operación de escritura exige CSRF

### Implementación

- [ ] T038 [P] [US1] Implementar el repositorio de cuentas en `src/main/java/pe/org/beneficencia/legalcontrol/access/AppUserRepository.java` con `JdbcClient` y SQL explícito, sin JPA
- [ ] T039 [US1] Implementar el servicio de autenticación en `src/main/java/pe/org/beneficencia/legalcontrol/access/AuthenticationService.java` con verificación Argon2 y respuesta genérica ante credencial inválida o cuenta inactiva
- [ ] T040 [US1] Implementar la política de contraseñas de FR-026 en `src/main/java/pe/org/beneficencia/legalcontrol/access/PasswordPolicy.java`: entre 15 y 128 caracteres, admitiendo espacios y pegado, con confirmación coincidente, aplicada en el canje de código y en el cambio propio; mensajes de error en español que expliquen el incumplimiento sin revelar la contraseña
- [ ] T041 [P] [US1] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/unit/PasswordPolicyTest.java` con los límites exactos: 14, 15, 128 y 129 caracteres, espacios internos y de borde, y confirmación que no coincide
- [ ] T042 [US1] Implementar el registro de intentos y los límites de abuso en `src/main/java/pe/org/beneficencia/legalcontrol/access/AuthAttemptService.java` para los tres tipos de evento de FR-026: 10 ingresos fallidos por cuenta y origen en 15 minutos, 10 canjes de código fallidos en 15 minutos, y 5 generaciones de código por hora y cuenta. Contar en consulta sin contadores guardados, anonimizar con `APP_RATE_LIMIT_KEY`, serializar la comprobación y el registro por clave, y no registrar contraseñas ni códigos
- [ ] T043 [US1] Implementar el controlador de acceso en `src/main/java/pe/org/beneficencia/legalcontrol/access/AccessController.java` con `GET /login`, `POST /login` y `POST /logout` según [contracts/web.md](contracts/web.md)
- [ ] T044 [P] [US1] Crear las vistas en `src/main/resources/templates/access/login.html` con mensajes en español, navegación por teclado y foco correcto
- [ ] T045 [US1] Guardar `authenticated_at` y `auth_version` en la sesión al ingresar, e invalidar todas las sesiones al cambiar contraseña o desactivar la cuenta, en `src/main/java/pe/org/beneficencia/legalcontrol/access/SessionLifecycle.java`

**Punto de control**: se entra, se sale, la sesión caduca por ambos límites y la revocación es inmediata.

---

## Fase 4: Historia 2 — Registrar y consultar procesos de toda el área (P1)

**Objetivo**: dar de alta un expediente físico y localizar los procesos de cualquier integrante.

**Prueba independiente**: con una cuenta activa, crear un proceso con datos mínimos y otro completo, y consultar registros de otros responsables.

### Pruebas

- [ ] T046 [P] [US2] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/web/JudicialCaseFormContractTest.java`: alta mínima, alta completa, número vacío, fecha imposible y monto no numérico, comprobando que se conserva el formulario y no se crea registro parcial
- [ ] T047 [P] [US2] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/CaseNumberUniquenessIT.java` con altas concurrentes del mismo número normalizado, incluyendo registros ocultos y de otros responsables
- [ ] T048 [P] [US2] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/web/JudicialCaseListContractTest.java`: filtros combinados, orden en ambos sentidos, fechas ausentes al final, estado vacío y filtro no válido con 422

### Implementación

- [ ] T049 [P] [US2] Crear el modelo de proceso en `src/main/java/pe/org/beneficencia/legalcontrol/judicialcase/JudicialCase.java` con los campos de FR-004 y FR-005
- [ ] T050 [P] [US2] Crear el objeto de entrada del formulario en `src/main/java/pe/org/beneficencia/legalcontrol/judicialcase/JudicialCaseForm.java`, sin admitir `ownerId`, ids técnicos ni tiempos como entradas
- [ ] T051 [US2] Implementar el repositorio en `src/main/java/pe/org/beneficencia/legalcontrol/judicialcase/JudicialCaseRepository.java` con SQL explícito, filtros combinables y como máximo 6 consultas SELECT de dominio por listado
- [ ] T052 [US2] Implementar la validación en `src/main/java/pe/org/beneficencia/legalcontrol/judicialcase/JudicialCaseValidator.java`: número obligatorio conservando letras, separadores y ceros iniciales; fechas reales; correlativo entero; monto a `BigDecimal` con dos decimales admitiendo coma o punto; rechazo sin truncar textos fuera de presupuesto
- [ ] T053 [US2] Implementar el servicio de alta en `src/main/java/pe/org/beneficencia/legalcontrol/judicialcase/JudicialCaseService.java`, fijando el responsable en el servidor como el usuario creador y rechazando explícitamente cualquier intento de asignación
- [ ] T054 [US2] Implementar `GET /judicial-cases`, `GET /judicial-cases/new`, `POST /judicial-cases` y `GET /judicial-cases/{id}` en `src/main/java/pe/org/beneficencia/legalcontrol/judicialcase/JudicialCaseController.java`
- [ ] T055 [P] [US2] Crear las vistas de listado y alta en `src/main/resources/templates/judicial-cases/list.html` y `form.html`, con moneda PEN visible y fecha de referencia mostrada
- [ ] T056 [P] [US2] Crear la vista de ficha en `src/main/resources/templates/judicial-cases/detail.html`, mostrando todos los datos opcionales con ausencia explícita cuando proceda
- [ ] T057 [US2] Implementar la conservación de filtros al paginar y al volver al listado, resolviéndolos en una sola petición al enviar

**Punto de control**: se registran y se consultan expedientes de todo el área, con filtros y orden.

---

## Fase 5: Historia 3 — Editar con permisos y consultar el historial (P1)

**Objetivo**: que cada abogado actualice lo suyo, que la jefa corrija cualquier proceso, y que quede evidencia de qué cambió y quién lo hizo.

**Prueba independiente**: preparar procesos de dos abogados y de la jefa, ejecutar la matriz de permisos y revisar el historial del expediente afectado.

### Pruebas

- [ ] T058 [P] [US3] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/web/CasePermissionContractTest.java` con la matriz completa: propio y ajeno para ABOGADO, ajeno para JEFA, incluyendo peticiones directas que omiten los controles visibles
- [ ] T059 [P] [US3] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/CaseHistoryIT.java`: cambios de fecha y estado con valores anterior y nuevo, autor distinto de responsable, consulta sin generar entradas, e imposibilidad de editar o borrar historial
- [ ] T060 [P] [US3] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/ConcurrentEditIT.java` verificando que una edición concurrente no sobrescribe en silencio y devuelve 409 con la versión vigente

### Implementación

- [ ] T061 [US3] Implementar la autorización por propietario y rol en `src/main/java/pe/org/beneficencia/legalcontrol/judicialcase/CaseAuthorization.java`, revalidada dentro de la transacción de escritura y no solo en la vista
- [ ] T062 [US3] Implementar el control de versión optimista en `src/main/java/pe/org/beneficencia/legalcontrol/judicialcase/JudicialCaseService.java`, devolviendo 409 ante conflicto o duplicado
- [ ] T063 [US3] Implementar `GET /judicial-cases/{id}/edit`, `POST /judicial-cases/{id}` y `POST /judicial-cases/{id}/visibility` en `JudicialCaseController.java`, sin permitir que la visibilidad altere el estado procesal
- [ ] T064 [P] [US3] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/web/VisibilityStatusMatrixTest.java` verificando las cuatro combinaciones de SC-012: visible/oculto por en trámite/concluido. Un concluido puede seguir visible y un proceso en trámite puede estar oculto; cambiar el estado procesal no oculta, y ocultar no cambia el estado
- [ ] T065 [US3] Conectar cada modificación con `AuditRecorder` en la misma transacción, registrando el responsable de entonces cuando el autor sea la jefa sobre un proceso ajeno
- [ ] T066 [US3] Implementar `GET /judicial-cases/{id}/history` con historia paginada de solo lectura en `JudicialCaseController.java` y `src/main/java/pe/org/beneficencia/legalcontrol/audit/AuditQueryRepository.java`
- [ ] T067 [P] [US3] Crear las vistas de edición y de historial en `src/main/resources/templates/judicial-cases/edit.html` y `history.html`, en orden cronológico y distinguiendo autor de responsable

**Punto de control**: los permisos se cumplen incluso ante peticiones directas y el historial es completo e inmutable.

---

## Fase 6: Historia 4 — Interpretar el plazo del expediente (P1)

**Objetivo**: mostrar si vence hoy, si ya venció o cuántos días hábiles quedan, sin contar fechas a mano.

**Prueba independiente**: preparar expedientes con distintas fechas y un calendario anual verificado, y consultar listado y ficha con fecha de referencia controlada.

### Pruebas

- [ ] T068 [P] [US4] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/unit/DeadlineEvaluatorTest.java` con los 12 ejemplos de la spec: hoy, pasado, futuro, ausencia, fin de semana, día no laborable, cruce de año y cobertura faltante
- [ ] T069 [P] [US4] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/unit/DeadlinePropertyTest.java` contrastando el evaluador contra un enumerador día a día, incluyendo medianoche y zona horaria de Arequipa
- [ ] T070 [P] [US4] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/DeadlineConsistencyIT.java` comprobando que listado y ficha aplican exactamente la misma regla

### Implementación

- [ ] T071 [US4] Implementar el evaluador único en `src/main/java/pe/org/beneficencia/legalcontrol/calendar/DeadlineEvaluator.java`: excluir sábados, domingos y días no laborables registrados; excluir hoy; incluir la fecha límite cuando sea hábil; no desplazar un límite no hábil
- [ ] T072 [US4] Implementar el snapshot del calendario por consulta en `src/main/java/pe/org/beneficencia/legalcontrol/calendar/CalendarSnapshot.java`, de modo que una sola lectura sirva a todas las filas del listado
- [ ] T073 [US4] Implementar el objeto de interpretación en `src/main/java/pe/org/beneficencia/legalcontrol/calendar/DeadlineView.java` con «Vence hoy», «Vencido», «X días hábiles restantes» y «Sin fecha límite», calculados al consultar y nunca almacenados
- [ ] T074 [US4] Implementar la ausencia de cobertura en `DeadlineEvaluator.java`: sustituir el conteo por «Cálculo no disponible: revisar días no laborables» indicando los años faltantes, conservando «Vence hoy» y «Vencido» cuando sean determinables por comparación de fechas
- [ ] T075 [P] [US4] Integrar la interpretación en `src/main/resources/templates/judicial-cases/list.html` y `detail.html`, con la fecha de referencia visible y la relación temporal de las demás fechas
- [ ] T076 [US4] Verificar que ningún indicador temporal se persiste como columna, conforme al principio V, en `src/test/java/pe/org/beneficencia/legalcontrol/integration/NoDerivedColumnsIT.java`

**Punto de control**: los plazos se interpretan igual en listado y ficha, y la falta de calendario se advierte en lugar de mentir.

---

## Fase 7: Historia 6 — Gestionar acceso de usuarios (P1)

**Objetivo**: que la jefa dé de alta, desactive y reactive cuentas sin depender del programador, entregando códigos en mano.

**Prueba independiente**: ejecutar el ciclo completo de una cuenta sin ningún servicio externo ni buzón de correo, sin necesitar procesos existentes.

### Pruebas

- [ ] T077 [P] [US6] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/AccountLifecycleIT.java`: alta con código mostrado una sola vez, canje, desactivación con invalidación inmediata de sesiones, reactivación con contraseña nueva y códigos previos rechazados
- [ ] T078 [P] [US6] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/LastHeadGuardIT.java` verificando que la desactivación concurrente de la última JEFA activa se rechaza mediante bloqueo global, no mediante un COUNT previo
- [ ] T079 [P] [US6] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/web/UserPermissionContractTest.java` comprobando que ABOGADO obtiene 403 en todas las rutas de `/users`

### Implementación

- [ ] T080 [P] [US6] Implementar los códigos de acceso en `src/main/java/pe/org/beneficencia/legalcontrol/access/AccessCodeService.java`: generación con entropía suficiente y legible a mano, uso único, caducidad (24 h activación y reactivación, 1 h restablecimiento), invalidación del anterior al generar otro, persistencia solo del derivado verificable y error genérico que no revela la cuenta
- [ ] T081 [US6] Implementar la presentación del código una sola vez en `src/main/java/pe/org/beneficencia/legalcontrol/access/AccessCodeDisplay.java` y su plantilla: se muestra en la respuesta a JEFA tras el commit, no se repite al recargar ni al volver atrás, no se registra en logs ni en el historial, y la pantalla lleva `Cache-Control: no-store`
- [ ] T082 [US6] Implementar el servicio de cuentas en `src/main/java/pe/org/beneficencia/legalcontrol/access/UserAdminService.java`: alta pendiente, desactivación conservando expedientes e historial, reactivación pendiente y regeneración de código según estado
- [ ] T083 [US6] Implementar el cambio de contraseña propia de FR-025b en `src/main/java/pe/org/beneficencia/legalcontrol/access/SelfPasswordService.java` y las rutas `/account/password`: exige la contraseña actual, no requiere código ni intervención de JEFA, e invalida las demás sesiones
- [ ] T084 [US6] Implementar el bloqueo global de la última JEFA activa en `src/main/java/pe/org/beneficencia/legalcontrol/access/HeadGuard.java`, compartiendo transacción con el cambio de cuenta
- [ ] T085 [US6] Implementar las rutas de `/users` y el canje `/access/redeem` en `src/main/java/pe/org/beneficencia/legalcontrol/access/UserAdminController.java` y `AccessController.java` según [contracts/web.md](contracts/web.md); el código viaja siempre en el cuerpo de un POST, nunca en la URL
- [ ] T086 [P] [US6] Crear las vistas en `src/main/resources/templates/users/` y `src/main/resources/templates/access/`: formulario único de canje, pantalla de código mostrada una sola vez con aviso de que no se repetirá, y confirmación que informa el efecto antes de desactivar
- [ ] T087 [US6] Configurar `Referrer-Policy: no-referrer` y `Cache-Control: no-store` en las páginas que muestran o reciben un código, sin recursos externos, y excluir cadenas de consulta y cuerpos secretos de los registros, en `src/main/java/pe/org/beneficencia/legalcontrol/config/SecurityConfig.java`

**Punto de control**: el ciclo de vida de una cuenta funciona íntegro y la última JEFA no puede quedar fuera.

---

## Fase 8: Historia 5 — Mantener los días no laborables (P2)

**Objetivo**: que el área use un mismo calendario en todos sus plazos, con constancia de revisión anual.

**Prueba independiente**: administrar un año de prueba sin procesos, comprobar validaciones, permisos y cobertura, y luego verificar el recálculo con un expediente preparado.

### Pruebas

- [ ] T088 [P] [US5] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/CalendarCoverageIT.java` con años de 0, 1, 4 y 5 entradas, comprobando que cero impide confirmar, que de una a cuatro exige reconocimiento adicional y que cinco o más sigue exigiendo la declaración
- [ ] T089 [P] [US5] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/CalendarInvalidationIT.java` verificando que cualquier alta, edición o retiro invalida la revisión del año afectado y que una revisión concurrente devuelve 409
- [ ] T090 [P] [US5] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/web/CalendarPermissionContractTest.java`: ABOGADO consulta, y obtiene rechazo al modificar o confirmar cobertura

### Implementación

- [ ] T091 [P] [US5] Implementar el repositorio en `src/main/java/pe/org/beneficencia/legalcontrol/calendar/NonWorkingDayRepository.java` con fecha única y los tipos feriado nacional, feriado regional, día no laborable y otro, en inglés internamente y español en vista
- [ ] T092 [US5] Implementar el servicio en `src/main/java/pe/org/beneficencia/legalcontrol/calendar/CalendarService.java` con alta, edición y retiro que conserva evidencia, invalidando las revisiones afectadas
- [ ] T093 [US5] Implementar la confirmación anual en `src/main/java/pe/org/beneficencia/legalcontrol/calendar/CalendarReviewService.java`: el servidor calcula la cantidad y no acepta un total del cliente; `fullYearReviewed` llega sin marcar; de una a cuatro entradas exige `lowCountAcknowledged`
- [ ] T094 [US5] Implementar las rutas de `/non-working-days` en `src/main/java/pe/org/beneficencia/legalcontrol/calendar/CalendarController.java` según [contracts/web.md](contracts/web.md)
- [ ] T095 [P] [US5] Crear las vistas en `src/main/resources/templates/calendar/`, mostrando año, cantidad y estado de revisión, con la declaración «He revisado el calendario completo de este año» sin preseleccionar
- [ ] T096 [US5] Auditar las modificaciones del calendario en `audit_event` con entrada afectada, valores anterior y nuevo, autor y momento, advirtiendo el efecto sobre los plazos

**Punto de control**: el calendario es administrable, la cobertura anual exige revisión humana y los plazos se recalculan al consultar.

---

## Fase 9: Historia 7 — Administrar estados procesales (P2)

**Objetivo**: disponer de un catálogo de situaciones procesales sin tocar la base a mano.

**Prueba independiente**: partir de catálogo vacío, crear estados, deshabilitar y comprobar el comportamiento en fichas y filtros.

### Pruebas

- [ ] T097 [P] [US7] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/integration/ProceduralStatusIT.java`: catálogo vacío, nombre duplicado rechazado, borrado permitido sin usos y 409 al eliminar referenciado con opción de deshabilitar
- [ ] T098 [P] [US7] Escribir `src/test/java/pe/org/beneficencia/legalcontrol/web/StatusVisibilityContractTest.java` comprobando que un estado deshabilitado sigue apareciendo en ficha, filtro e historia, no se ofrece como nueva elección, y que un formulario que lo seleccionó antes se revalida al guardar

### Implementación

- [ ] T099 [P] [US7] Implementar el repositorio en `src/main/java/pe/org/beneficencia/legalcontrol/proceduralstatus/ProceduralStatusRepository.java` con unicidad de nombre
- [ ] T100 [US7] Implementar el servicio en `src/main/java/pe/org/beneficencia/legalcontrol/proceduralstatus/ProceduralStatusService.java` con habilitar, deshabilitar y borrado solo sin usos actuales ni históricos
- [ ] T101 [US7] Implementar las rutas de `/procedural-statuses` en `src/main/java/pe/org/beneficencia/legalcontrol/proceduralstatus/ProceduralStatusController.java`
- [ ] T102 [P] [US7] Crear las vistas en `src/main/resources/templates/procedural-statuses/`
- [ ] T103 [US7] Implementar los snapshots de nombre en `case_history_status_reference` para que renombrar un estado no altere la historia ya registrada

**Punto de control**: el catálogo se administra sin romper la historia ya escrita.

---

## Fase 10: Cierre y aspectos transversales

**Propósito**: cumplir las puertas de aceptación que la constitución exige y que ninguna historia cubre por sí sola.

- [ ] T104 [P] Revisar que toda la interfaz y todos los mensajes estén en español, con avisos también en texto y no solo por color, en `src/main/resources/templates/` y `messages_es.properties`
- [ ] T105 [P] Verificar navegación por teclado, orden de foco y comportamiento ante red fallida en `src/test/java/pe/org/beneficencia/legalcontrol/acceptance/AccessibilityAcceptanceTest.java` con Playwright
- [ ] T106 Crear el perfil de datos sintéticos `performance-tests` en `src/test/resources/fixtures/` con 5.000 procesos y 50 estados; no usar datos personales reales ni feriados oficiales reales como fixtures
- [ ] T107 Medir los presupuestos de SC-001 en `src/test/java/pe/org/beneficencia/legalcontrol/acceptance/PerformanceBudgetTest.java` con el equipo y la red de referencia (2 núcleos, 4 GB, 2 Mbps, 150 ms), comprobando p95 de listado, ficha y formulario ≤ 1 s y guardado ≤ 2 s
- [ ] T108 Verificar que ninguna pantalla supera 6 consultas SELECT de dominio en listado ni 7 en ficha, sin N+1 ni sondeo, en `src/test/java/pe/org/beneficencia/legalcontrol/integration/QueryBudgetIT.java`
- [ ] T109 Documentar y ejecutar el ensayo de restauración en `specs/001-control-procesos-judiciales/quickstart.md`, comprobando que recupera usuarios, procesos, calendario e historial, e invalidando códigos y sesiones recuperados antes de abrir la red
- [ ] T110 Comprobar el principio III de la constitución 4.0.1: que el acceso a la base exige TLS y credenciales, que no hay panel administrativo ni API de datos expuestos, que las credenciales viven fuera del repositorio y son rotables, y documentar si el alojamiento ofrece restricción por origen o red privada y si se usa
- [ ] T111 Verificar que el sistema no realiza ninguna conexión saliente de negocio: sin SMTP, sin proveedor externo y sin credenciales de envío que provisionar, conforme a [contracts/operations.md](contracts/operations.md)
- [ ] T112 Ejecutar el recorrido completo de [quickstart.md](quickstart.md) y vincular la evidencia de cada criterio SC-001 a SC-012 con su recorrido correspondiente

---

## Dependencias y orden de ejecución

### Dependencias entre fases

- **Fase 1 (Preparación)**: sin dependencias
- **Fase 2 (Fundacional)**: depende de la Fase 1 y **bloquea todas las historias**
- **Fases 3 a 9 (Historias)**: todas dependen de la Fase 2
- **Fase 10 (Cierre)**: depende de las historias que se quieran aceptar

### Dependencias entre historias

- **US1 (P1)**: primera. Todo lo demás exige sesión identificada
- **US6 (P1)**: depende de US1. Sin ella, las cuentas solo se crean con el comando de arranque
- **US2 (P1)**: depende de US1. Independiente del catálogo: funciona con `procedural_status` vacío
- **US3 (P1)**: depende de US2, que crea los procesos que se editan
- **US4 (P1)**: depende de US2 para tener fichas donde mostrar el plazo. Funciona sin US5 mostrando la advertencia de cobertura faltante
- **US5 (P2)**: completa a US4 sustituyendo la advertencia por conteos reales
- **US7 (P2)**: enriquece a US2 y US3; ninguna de las dos la necesita para funcionar

### Oportunidades de paralelismo

- Fase 1: T004 a T006, T008 a T012 y T014 en paralelo
- Fase 2: las migraciones T016 a T021 en paralelo; T029 a T032 en paralelo
- Dentro de cada historia, todas las pruebas marcadas [P] a la vez, y las vistas en paralelo con los servicios
- Terminada la Fase 2, US2 y US6 pueden avanzar en paralelo si hay más de una persona

---

## Estrategia de implementación

### Mínimo demostrable

La convención sugiere detenerse tras la primera historia, pero **US1 por sí sola no demuestra nada al cliente**: es una pantalla de acceso sin contenido detrás.

El mínimo que ya le gana al Excel es **US1 + US2 + US4**: entrar, registrar y consultar expedientes de toda el área, y ver el plazo interpretado. US4 funciona sin calendario mostrando la advertencia, así que la demostración es honesta desde el primer día.

1. Fase 1 y Fase 2 completas
2. US1 → US2 → US4
3. **Detener y validar** contra la laptop y la red de referencia
4. Enseñárselo al cliente antes de seguir

### Entrega incremental

1. Preparación y Fundacional → base lista
2. US1 + US2 + US4 → primera demostración
3. US5 → los plazos dejan de advertir y empiezan a contar
4. US3 → edición con permisos e historial
5. US6 → la jefa deja de depender del programador para las cuentas
6. US7 → catálogo de estados
7. Fase 10 → puertas de aceptación y ensayo de restauración

### Riesgo a vigilar

La Fase 10 no es cosmética: T107, T109 y T110 son puertas que la constitución exige y que ninguna historia cubre. Si se dejan para el final y fallan, el trabajo afectado ya está hecho. Conviene medir presupuestos (T107) en cuanto exista el listado de US2, no al terminar todo.

---

## Notas

- [P] significa archivos distintos y sin dependencias pendientes
- Cada historia debe poder completarse y validarse por separado
- Las pruebas se escriben antes de la implementación de su historia y deben fallar primero
- Confirmar cada tarea o grupo lógico con un commit
- No sustituir PostgreSQL por H2 en ninguna prueba de integración
- Ninguna prueba usa datos personales reales
