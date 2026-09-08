# Revisión de código previa a dashboard y alertas

Fecha: 7 de septiembre de 2026. Rama: `004-dashboard-alertas`. Commit revisado: `d513418`.

La rama contiene el cierre de la funcionalidad 003. No existe todavía `specs/004-*` y `.specify/feature.json` apunta a `specs/003-control-pendientes`. Por tanto, esta revisión examina la base actual y sus riesgos para la 004; no evalúa una implementación de dashboard aún inexistente. Se contrastaron los flujos con las especificaciones 001–003 y el insumo del cliente.

Se utilizó Graphify, regenerando el grafo desactualizado, y se inspeccionaron servicios, controladores, consultas SQL, migraciones, plantillas, pruebas y scripts. No se modificó código de aplicación ni se ejecutaron operaciones contra producción.

## Hallazgos

P1: atender con prioridad por seguridad, pérdida de información o alertas omitidas. P2: corregir por inconsistencias funcionales o de historial.

### 1. [P1] El login no aplica el límite de intentos fallidos

**Ubicación:** [SecurityConfig.java:49](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/config/SecurityConfig.java:49), [AuthAttemptService.java:62](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/access/AuthAttemptService.java:62).

`formLogin` redirige los fallos a `/login?error`, pero ningún componente del flujo registra `LOGIN_FAILURE` ni consulta su límite. La búsqueda en producción encuentra uso de `demasiadosFallos` únicamente para canjear códigos. Un origen puede continuar probando contraseñas después de diez errores; no se aplica la espera exigida por FR-026 de la spec 001.

La limitación de cinco emisiones por hora también está definida en `demasiadasEmisiones`, pero no tiene llamadores ni registro de `CODE_ISSUE` en los flujos de emisión.

**Corrección:** conectar el control y registro al proceso real de autenticación y emisión, conservando respuestas genéricas. Automatizar pruebas del undécimo fallo, sexta emisión y recuperación al vencer la ventana.

**Evidencia:** inspección del flujo y búsqueda de referencias; pendiente de comprobación HTTP con PostgreSQL.

### 2. [P1] Un código de restablecimiento anterior sobrevive al cambio de contraseña del titular

**Ubicación:** [SelfPasswordService.java:59](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/access/SelfPasswordService.java:59), [AccessCodeService.java:94](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/access/AccessCodeService.java:94).

**Secuencia:** JEFA emite un código RESET; el titular cambia su contraseña desde su sesión; alguien que conserva el código lo canjea antes de que expire. El cambio propio incrementa `auth_version`, pero no revoca los tokens. La búsqueda del código comprueba consumo, revocación y caducidad, pero no compara `t.auth_version` con `u.auth_version`. `RedeemService` admite el RESET porque la cuenta continúa ACTIVE.

El código anterior permite volver a sustituir la contraseña, pese a la renovación de credenciales del titular.

**Corrección:** invalidar los códigos vivos al cambiar la contraseña y exigir coincidencia de versión al canjear, con un orden coherente de bloqueos. Probar esta secuencia y su variante concurrente.

**Evidencia:** seguimiento de las consultas y transacciones; pendiente de reproducción con PostgreSQL.

### 3. [P1] Se omiten antigüedades y tiempos de atención de años anteriores

**Ubicación:** [CalendarRepository.java:59](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/calendar/CalendarRepository.java:59), [PendingTaskController.java:166](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskController.java:166).

`paraListado(hoy)` carga exclusivamente desde el año actual hasta cinco años después. Se utiliza también para antigüedad desde la recepción y tiempo de atención de cumplidos. Aunque el calendario histórico esté revisado, una recepción del año anterior produce `Optional.empty`; la plantilla muestra un guion y desaparece la alerta de más de quince días.

El mismo rango puede impedir «No cumplido» sobre una programación del año anterior, aunque exista cobertura. Los límites posteriores a cinco años también quedan artificialmente fuera del rango.

**Corrección:** calcular el intervalo necesario a partir de las fechas de los registros, cargarlo una vez por consulta y distinguir expresamente falta de cobertura de falta de fecha. Para la 004, los totales deben considerar el conjunto filtrado completo, no solo los 25 registros de una página.

**Evidencia ejecutada:** reloj fijo en 2026-09-07, recepción 2025-12-01 y calendario sintético revisado: `199` días con el intervalo completo; `Optional.empty` usando el método real `paraListado`. Se utilizaron dobles de persistencia, no feriados oficiales ni PostgreSQL.

### 4. [P1] Editar puede borrar asociaciones que el usuario no cambió

**Ubicación:** [PendingTaskCatalogs.java:30](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskCatalogs.java:30), [edit.html:32](/home/n4nd0/Documentos/SistemaJuridico/src/main/resources/templates/pending-tasks/edit.html:32).

**Secuencia:** asignar un tipo/prioridad/estado, deshabilitarlo y después editar únicamente el título del pendiente. Los selectores se rellenan solo con valores habilitados; el valor actual desaparece y queda seleccionada la opción vacía. El guardado escribe `null` y pierde la clasificación.

Ocurre también con el expediente relacionado cuando queda oculto o fuera de los primeros 500 resultados. Esas opciones tampoco se incorporan específicamente para conservar el vínculo actual. Además, el límite fijo impide elegir normalmente expedientes posteriores al resultado 500.

**Corrección:** incluir los valores actuales en los formularios de edición aunque ya no sean seleccionables para altas, conservarlos al guardar cambios ajenos y reemplazar la lista fija de expedientes por búsqueda paginada. Validar en servidor las nuevas asignaciones.

**Evidencia:** seguimiento de opciones HTML y escritura de los campos; pendiente de recorrido de navegador con datos reales de prueba.

### 5. [P2] Se puede reprogramar una tarea cumplida y dejar estados contradictorios

**Ubicación:** [PendingTaskActionService.java:164](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskActionService.java:164).

La acción manual no comprueba `completed_at`, a diferencia de «No cumplido». Su formulario también aparece en la ficha de cumplidos. Cambia fecha y estado de catálogo a Reprogramado, pero mantiene la marca de cumplimiento: la tarea sigue en `/cumplidos` y fuera de la lista activa. Una reversión posterior ya no conserva la programación que tenía al cumplirse.

**Corrección:** rechazar esta transición en servidor y ofrecer primero la reversión con motivo; revisar también la edición general de fecha/estado sobre cumplidos para mantener la misma regla.

**Evidencia ejecutada:** llamada al servicio real con una fila cumplida y versión vigente; aceptó la operación y emitió el UPDATE de fecha/estado sin limpiar `completed_at`. Persistencia sustituida por un doble.

### 6. [P2] Cambiar la programación desde «Editar» no aumenta el contador de reprogramaciones

**Ubicación:** [PendingTaskService.java:109](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskService.java:109), [PendingTaskRepository.java:234](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskRepository.java:234).

El formulario general permite modificar `scheduledFor`, pero registra la acción como UPDATE. El contador de reprogramaciones solo cuenta NOT_COMPLETED y RESCHEDULE. Mover una tarea del 3 al 8 de septiembre desde «Editar» y luego cumplirla deja un contador de cero; usando «Reprogramar», la misma modificación cuenta como una.

**Corrección:** centralizar la transición de fecha o clasificar sus cambios efectivos de forma consistente, evitando dobles eventos y distinguiendo programación inicial de reprogramación. Probar equivalencia de ambas rutas.

**Evidencia:** inspección del formulario, servicio y agregación SQL.

### 7. [P2] Las acciones omiten las referencias históricas a estados utilizados

**Ubicación:** [PendingTaskActionService.java:227](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskActionService.java:227).

Crear y editar sí llaman a `referenciarCatalogosDePendiente`; cumplir, revertir y reprogramar no lo hacen. Crear sin estado, cumplir y revertir puede dejar «Cumplido» sin uso actual ni referencia histórica. `CatalogService.eliminar` permite entonces borrar un valor que sí se utilizó en el ciclo del pendiente. Los eventos de acciones tampoco conservan los identificadores de estado anterior/nuevo.

**Corrección:** registrar estados anteriores/nuevos y sus referencias dentro de la misma transacción de cada acción. Probar que un estado utilizado únicamente por una acción no se pueda borrar después de otra transición.

**Evidencia ejecutada:** la acción real de reprogramar escribió estado y evento, con cero llamadas al registro de referencias; la consecuencia de borrado se identificó inspeccionando las consultas de uso.

### 8. [P2] «No cumplido» inventa una fecha anterior cuando no había programación

**Ubicación:** [PendingTaskActionService.java:147](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/pendingtask/PendingTaskActionService.java:147).

Si `scheduled_for` es nulo, usar hoy como base del cálculo es correcto. Sin embargo, ese mismo valor se envía como fecha anterior al historial. Una tarea nunca programada queda registrada como si hubiera estado programada para hoy. Al tratarse de evidencia inmutable, el error persiste.

**Corrección:** separar la fecha anterior real, que puede ser nula, de la fecha base del cálculo automático.

**Evidencia ejecutada:** entrada original `scheduled_for=null`; el servicio produjo `before_values={scheduledFor=2026-09-07}`.

### 9. [P2] El lector del historial interpreta claves JSON como fechas

**Ubicación:** [AuditQueryRepository.java:90](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/audit/AuditQueryRepository.java:90).

La extracción de `scheduledFor` busca las siguientes comillas, sin interpretar JSON. Para `{"scheduledFor": null, "outputDocumentType": "Oficio"}`, devuelve `outputDocumentType` como fecha. Las instantáneas completas de UPDATE contienen campos nulos y otros campos posteriores, por lo que el historial puede mostrar frases con nombres internos en lugar de fechas.

**Corrección:** usar el ObjectMapper ya disponible y validar que el nodo sea texto no nulo con una fecha válida. Al mostrar UPDATE, distinguir además si la programación realmente cambió.

**Evidencia ejecutada:** llamada directa a `EntradaHistorial.fechaAnterior()` con el JSON anterior; resultado `outputDocumentType`.

## Automatizaciones recomendadas

| Prioridad | Automatización | Resultado verificable |
| --- | --- | --- |
| Alta | CI en cada PR con JDK de referencia, Docker y `./mvnw -B verify` | Ejecutar tanto Surefire como Failsafe; conservar reportes y bloquear integración si falla. Actualmente no hay `.github/workflows`. |
| Alta | Pruebas de regresión de los nueve casos anteriores | Cubrir errores de seguridad, cambio de año, conservación de campos y equivalencia entre rutas de modificación. |
| Alta | Validación previa al despliegue | `desplegar.sh` empaqueta con `-DskipTests`; exigir evidencia de `verify` sobre el mismo commit antes de migrar y solicitar despliegue. Verificar que el commit migrado coincide con el que se desplegará. |
| Alta para 004 | Pruebas automáticas de coherencia entre tarjetas, listas y filtros | Un cumplimiento sale de activos, una reversión corrige el total mensual y una tarea que vence y está programada hoy no se duplica en «Urgentes hoy». Incluir límite mensual en America/Lima y conjuntos de más de 25 registros. |
| Media | Aviso de cobertura del calendario que falta o fue invalidada | Detectar años necesarios sin revisión y orientar a JEFA. La confirmación de feriados debe seguir siendo una decisión explícita. |
| Media | Comprobación de rama y selector de Spec Kit | Detectar la combinación `004-dashboard-alertas` + selector 003 antes de generar tareas o ejecutar implementación sobre la funcionalidad equivocada. |
| Media | Verificación de scripts y dependencias | ShellCheck para scripts; empaquetado reproducible y revisión automática de actualizaciones. No se realizó aquí una auditoría de vulnerabilidades de dependencias. |

Para la 004 conviene compartir la definición de pendiente activo y las reglas temporales entre tarjetas y listados. Los contadores deben derivarse de los datos vigentes y respetar los mismos filtros. No hace falta un proceso nocturno que cambie tareas de estado para producir alertas: la lógica temporal puede evaluarse al consultar, como ya exige la spec 003.

## Validación realizada y límites

- `./mvnw -B '-Dtest=pe.org.beneficencia.legalcontrol.unit.*Test' test`: **37 pruebas, cero fallos y cero errores**.
- `./mvnw -B verify`: no pudo completar Surefire ni alcanzar Failsafe. Registró 155 pruebas, 108 errores de inicialización y cero fallos de aserción. La causa es infraestructura: Testcontainers no encontró Docker. Incluso fuera del sandbox, el socket configurado de Docker Desktop no existe. Estos 108 errores no se clasifican como 108 bugs del programa.
- Cinco comprobaciones aisladas ejecutaron clases reales con dobles de persistencia o datos directos: calendario histórico, reprogramación de cumplidos, referencias históricas, fecha anterior nula y lectura de JSON. No prueban transacciones, bloqueos, SQL en PostgreSQL ni la interfaz completa.
- No se ejecutó el despliegue, no se accedió a datos de producción y no se modificaron las especificaciones ni los servicios. Los hallazgos son del código base existente; no se atribuyen a cambios de la 004.

Orden recomendado: resolver acceso y conservación de datos (1–4), unificar transiciones e historial (5–9), y utilizar sus regresiones como condición de entrada a dashboard y alertas.
