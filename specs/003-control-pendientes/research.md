# Investigación técnica — Funcionalidad 003

**Rama Git actual**: `003-control-pendientes`

Decisiones previas al diseño. El stack, el acceso, el calendario y la auditoría vienen dadas
por las funcionalidades 001 y 002, desplegadas en producción.

## D1. ¿Historial propio o la auditoría existente?

**Decisión**: **reutilizar `audit_event`**, ampliando su restricción con los tipos nuevos. No
se crea `historial_pendientes`.

**Razón**: el insumo (§13) pide que no se pierda el historial de modificaciones y describe las
columnas que necesita. Todas tienen equivalente exacto en lo ya construido:

| §13 pide | `audit_event` tiene |
| --- | --- |
| `pendiente_id` | `entity_id` con `entity_type = 'PENDING_TASK'` |
| `fecha` | `occurred_at` |
| `accion` | `action` |
| `fecha_anterior` / `fecha_nueva` | `before_values` / `after_values` |
| `usuario_autor_id` | `actor_id` |
| `motivo` | `reason` |

La `descripcion` que pide la §13 no se almacena: se compone al mostrarla a partir de la acción
y las fechas, porque es texto derivado y guardarlo lo dejaría desincronizado si algún día
cambia la redacción.

Lo decisivo no es ahorrar una tabla, sino que **la inmutabilidad ya está garantizada por la
base** sobre `audit_event`: el rol de la aplicación solo tiene `SELECT` e `INSERT`. Una tabla
nueva exigiría repetir esa garantía y dejaría dos sitios donde mirar cuando alguien pregunte
qué pasó con un registro.

**Alternativa descartada**: tabla propia `pending_task_history` con las columnas literales del
insumo. Cumpliría la letra y no el fondo: dos historiales que mantener y dos garantías que
demostrar.

## D2. ¿Cómo se impide un pendiente vinculado a dos expedientes a la vez?

**Decisión**: dos claves foráneas anulables más una restricción `CHECK` que exige que como
mucho una esté informada.

**Razón**: la §9 dice que un pendiente cuelga de un proceso judicial, de un procedimiento
administrativo **o de nada**. Con dos columnas sin restricción, nada impediría rellenar ambas
y quedaría un registro que ninguna pantalla sabría mostrar. La base lo impide y no depende de
que el código se porte bien.

**Alternativas descartadas**:

- *Una columna genérica más un tipo*: perdería las claves foráneas, que son lo que impide
  vincular a un expediente inexistente o borrado.
- *Tabla de vínculos aparte*: permitiría varios vínculos, que es justo lo que la §9 no quiere.

## D3. ¿Dónde viven las reglas de las acciones?

**Decisión**: un servicio con un método explícito por acción —cumplir, revertir, no cumplido,
reprogramar—, cada uno transaccional y escribiendo su entrada de historial en la misma
transacción.

**Razón**: son cuatro acciones sobre seis estados. Una máquina de estados genérica añadiría una
capa de indirección para expresar algo que cabe en cuatro métodos legibles, y las reglas
—motivo obligatorio solo al revertir, siguiente día hábil solo en «no cumplido»— son distintas
en cada una, no un patrón común.

Cada método revalida el permiso **después** de bloquear la fila, igual que en las
funcionalidades anteriores, para que una revocación concurrente impida el guardado.

**Alternativa descartada**: una tabla de transiciones permitidas. Convertiría reglas explícitas
en datos que hay que interpretar, y el error se descubriría en ejecución en vez de al compilar.

## D4. El siguiente día hábil

**Decisión**: añadir `siguienteDiaHabil(fecha, calendario)` al evaluador de plazos existente,
que ya sabe qué días son hábiles. **No** se duplica la lógica de calendario.

**Razón**: la §16 lo pide como función, y el sistema ya tiene una sola función que decide qué
es un día hábil. Cualquier otra implementación sería una segunda verdad sobre lo mismo.

**Regla de cobertura**: si el año que hay que atravesar no tiene revisión confirmada, la
función **no devuelve una fecha**: avisa. Es la misma decisión que en los plazos, y por la misma
razón — reprogramar a un día que resultó ser feriado es peor que no reprogramar, porque nadie
se entera hasta que llega el vencimiento.

## D5. Valores derivados

**Decisión**: antigüedad, días hábiles restantes, tiempo de atención y número de
reprogramaciones se **calculan al consultar** y no se persisten.

El número de reprogramaciones sale de contar entradas de historial con acción de
reprogramación; el tiempo de atención, de los días hábiles entre recepción y cumplimiento.

**Razón**: principio V. Todos cambian con el calendario o con el día. Un contador de
reprogramaciones guardado se desincronizaría en cuanto alguien revirtiera un cumplido.

**Consecuencia a vigilar**: el historial de tareas cumplidas (§32) muestra ambos por fila. Se
resuelve con una consulta agregada que los calcula para toda la página, no una por fila.

## D6. Cinco catálogos con la misma forma: ¿ha llegado el momento de abstraer?

**Decisión**: **sí, extraer ahora.** Se crea una base compartida de catálogo y los cinco pasan
a usarla: estados procesales, estados administrativos, tipos de pendientes, prioridades y
estados de pendientes.

**Razón**: al planificar la 002 se decidió no abstraer con dos casos, y se dejó escrito que
*«si al construir la 003 aparece una tercera entidad con la misma forma, tres repeticiones sí
justificarían extraer lo común»*. Aparecen tres más. Los cinco comparten exactamente:

- Las mismas columnas: `id`, `name`, `description`, `enabled`, `created_by`, tiempos, `version`.
- Las mismas operaciones: crear con nombre único normalizado, habilitar, deshabilitar, y
  borrar solo si nunca se usó ni aparece en historial.
- La misma regla de negocio: deshabilitar no toca lo que ya lo usa; borrar un valor usado
  alguna vez está prohibido.

Lo que cambia entre ellos es el nombre de la tabla, la tabla que los referencia y la de
referencia histórica. Eso se parametriza sin volver ilegible el SQL.

**Alcance de la refactorización**: se hace **al final**, en la fase de cierre, y con las
funcionalidades 001 y 002 ya cubiertas por sus pruebas. Si algo se rompiera, las pruebas
existentes lo dirían antes de llegar a producción. Construir primero los tres catálogos nuevos
por duplicado y unificar después es más seguro que abstraer sobre la marcha.

**Alternativa descartada**: seguir duplicando. Cinco copias del mismo servicio significan que
una corrección hay que aplicarla cinco veces, y la sexta vez que alguien la olvide en una.

## D7. Numeración de migraciones

**Decisión**: la funcionalidad empieza en **V9**. Las V1 a V8 están aplicadas en producción y
no se modifican jamás: Flyway compara sumas de verificación.

La refactorización de catálogos de D6 **no cambia el esquema**: las cinco tablas se quedan como
están. Lo que se unifica es el código que las opera.

## D8. Rutas

**Decisión**: `/pendientes`, `/pendientes/hoy` y `/cumplidos`, tal como las fija el insumo
(§25, §26, §32). Los tres catálogos nuevos, sin ruta fijada, van como
`/tipos-de-pendiente`, `/prioridades` y `/estados-de-pendiente`.

`RutasSegunInsumoTest` ya reserva las tres primeras y fallará si alguna otra pantalla las
ocupa.
