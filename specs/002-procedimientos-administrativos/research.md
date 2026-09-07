# Investigación técnica — Funcionalidad 002

**Rama Git actual**: `002-procedimientos-administrativos`

Decisiones tomadas antes de diseñar. Ninguna reabre lo ya resuelto en la 001: el stack, el
acceso, el calendario y la auditoría se dan por buenos y en producción.

## D1. ¿Abstraer lo común con los expedientes judiciales o duplicar?

**Decisión**: **duplicar la estructura, compartir solo lo que ya es genérico.**

Se crea un paquete `administrativeprocedure` paralelo a `judicialcase`, con su propio modelo,
repositorio, validador, servicio y controlador. Se comparten sin tocar: `DeadlineEvaluator`,
`CalendarRepository`, `AuditRecorder`, `AuditQueryRepository`, `Paging`, `ErrorHandling`,
`HtmxSupport` y `ClockConfig`.

**Razón**: los dos registros se parecen en la forma —número único, responsable, fecha límite,
estado, historial— pero no en el contenido. El judicial tiene demandante, demandado, materia,
monto y dirección de inmueble; el administrativo tiene área solicitante, pedido y fecha de
recepción. Ninguno de esos campos existe en el otro.

Una clase base común obligaría a que cada campo propio viviera en la subclase y cada consulta
se compusiera por partes. El SQL dejaría de leerse de un vistazo, que es justo lo que se ganó
al elegir `JdbcClient` sobre JPA. Y en cuanto uno de los dos evolucione —los administrativos
tendrán pendientes vinculados en la 003— la jerarquía habría que romperla.

La duplicación aquí es de **estructura**, no de lógica: la regla de plazos, la auditoría y los
permisos siguen viviendo en un solo sitio. Lo que se repite son los recorridos de campos, que
son distintos en cada caso y por tanto no son duplicación real.

**Alternativas descartadas**:

- *Tabla única con columna `tipo`*: dejaría la mitad de las columnas nulas en cada fila y
  obligaría a validar «este campo solo si es judicial», que es donde se cuelan los errores.
- *Clase base abstracta con genéricos*: cada consulta se compondría por fragmentos y el SQL
  dejaría de ser legible. Se ganaría poco: los métodos comunes de verdad son cuatro.
- *Interfaz común solo para el plazo*: innecesaria. Ambos ya pasan su `LocalDate` al mismo
  evaluador; no hace falta un contrato para eso.

**Cuándo reconsiderarlo**: si al construir la 003 aparece una tercera entidad con la misma
forma, tres repeticiones sí justificarían extraer lo común. Con dos, no.

## D2. ¿Cómo ampliar `audit_event` sin romper lo ya registrado?

**Decisión**: una migración **V8** que sustituye la restricción `CHECK` de `entity_type`
añadiendo `ADMINISTRATIVE_PROCEDURE` y `ADMINISTRATIVE_STATUS`, mediante
`ALTER TABLE ... DROP CONSTRAINT` seguido de `ADD CONSTRAINT`.

**Razón**: sustituir una restricción no toca ninguna fila. Las entradas existentes siguen
cumpliendo la nueva, que es un superconjunto de la anterior. La operación la ejecuta el rol de
migración, que sí es dueño de la tabla; el rol de la aplicación conserva únicamente `SELECT` e
`INSERT`, así que **la inmutabilidad garantizada en la 001 no se debilita**.

La ampliación se hace en la misma migración que crea las tablas nuevas, para que no exista un
estado intermedio donde la aplicación pueda intentar auditar un tipo que la base rechaza.

**Alternativas descartadas**:

- *Quitar la restricción y validar solo en Java*: perdería la garantía a nivel de base, que es
  precisamente lo que hace creíble el historial.
- *Una tabla de auditoría separada para administrativos*: dos historiales que consultar y dos
  garantías que mantener. El historial es transversal por diseño.

## D3. ¿Referencia histórica de estados administrativos?

**Decisión**: una tabla propia, `procedure_history_status_reference`, con la misma forma que
`case_history_status_reference`.

**Razón**: esa tabla existe para impedir que se borre un estado que alguna vez se usó, y lo
consigue con una clave foránea restrictiva al catálogo. Como los catálogos son dos tablas
distintas, la clave foránea no puede apuntar a ambas: una sola tabla necesitaría dos columnas
opcionales y perdería la restricción, que es todo su valor.

Son cuatro columnas y una clave primaria compuesta. El coste de duplicarla es menor que el de
debilitar la garantía.

**Alternativa descartada**: una tabla polimórfica con `status_type` y `status_id` sin clave
foránea. Funcionaría en el papel y fallaría el día que alguien borre un estado: la base ya no
lo impediría.

## D4. Presupuesto de consultas por pantalla

**Decisión**: los mismos límites que la 001 — **6 consultas en el listado, 7 en la ficha**, con
**una sola lectura del calendario** por consulta, no una por fila.

**Razón**: el presupuesto no es por pantalla en abstracto sino por viaje a Supabase, que está
en otra región. La 001 mide 44 ms de servidor con 5.000 expedientes precisamente porque el
listado se resuelve con una consulta y dos joins. El mismo patrón se aplica aquí: `SELECT` con
`JOIN` al responsable y `LEFT JOIN` al catálogo, más una lectura de días no laborables y otra
de años cubiertos, reutilizadas para todas las filas de la página.

Se verifica con una prueba equivalente a `QueryBudgetIT`, contando transacciones reales contra
PostgreSQL.

## D5. Numeración de las migraciones

**Decisión**: la funcionalidad empieza en **V8**. Las migraciones V1 a V7 están aplicadas en
producción y **no se modifican jamás**, ni siquiera un comentario: Flyway compara sumas de
verificación y un cambio dejaría la base en estado fallido.

Si hiciera falta corregir algo de V1–V7, se hace con una migración nueva que lo enmiende.

## D6. Rutas en español, identificadores en inglés

**Decisión**: las rutas visibles son `/administrativos` y `/administrativos/{id}`, como pide el
insumo (§29 y §30). Las tablas y clases van en inglés: `administrative_procedure`,
`AdministrativeProcedureController`.

**Razón**: el insumo fija las rutas visibles y son todas en español. El catálogo, que el
insumo no nombra, va como `/estados-administrativos` por coherencia.

**Deuda detectada en la 001**: el insumo fija `/judiciales` y `/judiciales/{id}` (§27 y §28),
pero la 001 se construyó con `/judicial-cases`. Es una desviación introducida al implementar y
no señalada en su momento. Conviene corregirla antes de que existan enlaces guardados o
costumbre de uso; hacerlo ahora es renombrar rutas y plantillas, y más adelante sería una
migración de marcadores del equipo.

El insumo fija además estas rutas para funcionalidades posteriores, que conviene respetar
desde el principio: `/pendientes`, `/pendientes/hoy`, `/cumplidos`, `/calendario`, `/alertas`,
`/configuracion` y `/actividad-diaria`.
