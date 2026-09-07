# Modelo de datos — Funcionalidad 002

**Diseño**, no migraciones ejecutadas. Se añade sobre el esquema de la 001, ya aplicado en
producción. Convenciones heredadas sin cambios: PK UUID, momentos `timestamptz`, fechas
civiles `date`, versiones `bigint`, identificadores en inglés, referencias de usuario
restrictivas.

**Las migraciones V1 a V7 no se tocan.** Esta funcionalidad empieza en V8.

## `administrative_procedure`

| Campo | Tipo / regla |
| --- | --- |
| `id` | UUID PK |
| `sequence_number` | integer opcional; informativo, no identifica |
| `owner_id` | FK usuario obligatorio; fijo al creador |
| `file_number` | text obligatorio; único `lower(btrim(file_number))` |
| `requesting_area` | text opcional; **texto libre, sin catálogo** (confirmado por el cliente) |
| `request` | text opcional; hasta 10.000 caracteres |
| `administrative_status_id` | FK estado opcional, ON DELETE RESTRICT |
| `received_at` | date opcional; **no se autocompleta** con la fecha del día |
| `deadline` | date opcional |
| `notes` | text opcional |
| `active` | boolean true por defecto; solo visibilidad corriente |
| `created_at`, `updated_at` | instantes |
| `version` | bigint para edición optimista |

La unicidad de `file_number` es **independiente de `judicial_case.case_number`**: un mismo
número puede existir en ambos registros. Son expedientes de naturaleza distinta y sus series
las lleva cada uno por su lado (confirmado por el cliente; revisable si resultara que el área
usa una serie única).

`active` es visibilidad y nada más. Que el estado sea «Archivado» NO implica `active=false`,
igual que en los judiciales: son dos ejes distintos y confundirlos impediría archivar algo que
se quiere seguir viendo.

**Coherencia de fechas**: si `deadline < received_at` se **advierte** pero se guarda. El
sistema no corrige fechas por su cuenta; puede haber un pedido con plazo retroactivo y
adivinar cuál de las dos está mal sería inventar.

Índices iniciales: único de expediente; `(active, lower(btrim(file_number)), id)`,
`(owner_id, id)`, `(administrative_status_id, id)`, `(deadline NULLS LAST, id)`.

Edición: bloquear y revalidar usuario actor y fila objetivo; comparar `version` del formulario
con la actual. Diferencia → 409 sin cambios; igualdad de todos los valores → no-op sin
historial. El responsable histórico se guarda en la evidencia, no se relee después.

## `administrative_status`

`id` UUID PK; `name` text obligatorio único `lower(btrim(name))`; `description` text opcional;
`enabled` boolean; `created_by` FK usuario; `created_at`, `updated_at`; `version`.

**Catálogo distinto** del de estados procesales judiciales (`procedural_status`) y del de
pendientes, que llegará en la 003. No comparten filas, ni identificadores, ni filtros, aunque
«Archivado» exista en dos de las tres listas.

Inicia vacío. El cliente confirmó los valores —Pendiente de atención, Pendiente de
documentación, Atendido, Observado, Archivado— pero **no se siembran en la migración**: los
crea JEFA desde la aplicación, igual que en la 001, para que el catálogo vacío siga siendo un
estado válido y comprobable.

Habilitado → deshabilitado → habilitado. Borrar solo si no aparece en procedimientos ni en
historial. Deshabilitar no modifica los procedimientos que lo usan ni se ofrece para elegir de
nuevo; un procedimiento existente puede conservar su estado deshabilitado al editarlo.

## `procedure_history_status_reference`

PK `(audit_event_id, administrative_status_id)`, ambas FK restrictivas.

Equivalente a `case_history_status_reference` de la 001, con tabla propia porque la clave
foránea debe apuntar a `administrative_status` y una sola tabla no puede referenciar dos
catálogos distintos con integridad. Registrar los estados antes y después de cada cambio,
incluida el alta.

Sin `ON DELETE CASCADE` hacia la evidencia, en ninguna dirección.

## Ampliación de `audit_event`

La restricción `CHECK` de `entity_type` se **sustituye** para admitir
`ADMINISTRATIVE_PROCEDURE` y `ADMINISTRATIVE_STATUS` junto a los cinco valores existentes.

`DROP CONSTRAINT` + `ADD CONSTRAINT` no toca ninguna fila: las entradas ya escritas cumplen la
nueva restricción, que es un superconjunto. La ejecuta el rol de migración, dueño de la tabla.
El rol de la aplicación conserva únicamente `SELECT` e `INSERT`, así que la garantía de
inmutabilidad de la 001 queda intacta.

La ampliación va en la **misma migración** que crea las tablas, para que no exista un momento
en que la aplicación pueda intentar auditar un tipo que la base rechaza.

## Snapshot de consulta

Se reutiliza `DeadlineView` sin cambios. El plazo de un procedimiento administrativo se
calcula con el **mismo** `DeadlineEvaluator` y el **mismo** calendario que los judiciales: son
los mismos días no laborables del mismo país y de la misma institución.

Listado en transacción de solo lectura: procedimientos con joins al responsable y al catálogo
en **una** consulta, más **una** lectura de días no laborables y **una** de años cubiertos,
compartidas por todas las filas. Nunca una consulta por fila.

Nada de lo derivado se persiste: ni días restantes, ni estado de vencimiento, ni la advertencia
de fechas incoherentes.

## Orden de transacciones

1. Procedimiento: usuario actor, procedimiento, estado referenciado cuando cambie, evidencia.
2. Catálogo: usuario actor, estado; comprobar usos actuales e históricos antes de eliminar.
3. Lecturas: snapshot sin `FOR UPDATE`; una comprobación de sesión por petición.

El mismo orden que la 001, y por la misma razón: bloquear primero al actor permite que una
revocación concurrente impida un guardado posterior.
