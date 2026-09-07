# Modelo de datos — Funcionalidad 003

**Diseño**, no migraciones ejecutadas. Se añade sobre el esquema de las 001 y 002, ya aplicado
en producción. Convenciones heredadas: PK UUID, momentos `timestamptz`, fechas civiles `date`,
versiones `bigint`, identificadores en inglés, referencias de usuario restrictivas.

**Las migraciones V1 a V8 no se tocan.** Esta funcionalidad empieza en V9.

## `pending_task`

| Campo | Tipo / regla |
| --- | --- |
| `id` | UUID PK |
| `owner_id` | FK usuario obligatorio; **quien lo registra**, y queda fijo |
| `title` | text obligatorio; hasta 150 |
| `description` | text opcional; hasta 10.000 |
| `pending_task_type_id` | FK catálogo opcional, ON DELETE RESTRICT |
| `priority_id` | FK catálogo opcional, ON DELETE RESTRICT |
| `pending_task_status_id` | FK catálogo opcional, ON DELETE RESTRICT |
| `judicial_case_id` | FK opcional, ON DELETE RESTRICT |
| `administrative_procedure_id` | FK opcional, ON DELETE RESTRICT |
| `received_at` | date opcional; referencia de antigüedad cuando no hay plazo |
| `registered_at` | date obligatorio; el día del alta |
| `scheduled_for` | date opcional; el día previsto para hacerlo |
| `deadline` | date opcional |
| `completed_at` | timestamptz opcional; instante real del cumplimiento |
| `output_document_type` | text opcional; «Informe Legal», «Oficio», «Carta» |
| `output_document_number` | text opcional; hasta 150 |
| `notes` | text opcional |
| `active` | boolean true por defecto |
| `created_at`, `updated_at` | instantes |
| `version` | bigint para edición optimista |

**Vínculo excluyente**: restricción `CHECK` que exige que `judicial_case_id` y
`administrative_procedure_id` no estén ambos informados. Uno, el otro, o ninguno (§9).

**El documento de salida es un dato, no un archivo** (§44 y §3.5): se guarda su tipo y su
número, nunca el fichero.

**`completed_at` es la marca de cumplimiento.** Al revertir se pone a nulo; `scheduled_for`
**no se toca en ningún momento** de cumplir ni de revertir, de modo que al deshacer el
pendiente recupera su fecha sin tener que guardarla en otro sitio: nunca se perdió (§20.1).

Índices iniciales: `(active, scheduled_for NULLS LAST, id)`, `(owner_id, id)`,
`(pending_task_status_id, id)`, `(deadline NULLS LAST, id)`,
`(judicial_case_id)`, `(administrative_procedure_id)`, y `(completed_at DESC)` para el
historial de cumplidas.

## Catálogos: `pending_task_type`, `priority`, `pending_task_status`

Tres tablas con la **misma forma** que `procedural_status` y `administrative_status`:
`id` UUID PK; `name` text obligatorio único `lower(btrim(name))`; `description` opcional;
`enabled` boolean; `created_by` FK usuario; tiempos; `version`.

Los tres arrancan vacíos. El cliente enumera sus valores en el insumo —trece tipos (§10), las
prioridades (§11) y los seis estados (§12)— pero **no se siembran en la migración**: los carga
JEFA desde la aplicación, para que el catálogo vacío siga siendo un estado válido y probado.

**Son independientes entre sí y de los dos catálogos ya existentes.** Un mismo nombre puede
existir en varios sin conflicto.

## `pending_task_history_reference`

PK `(audit_event_id, catalog_kind, catalog_id)`, con `audit_event_id` FK restrictiva.

Cumple para los tres catálogos nuevos lo que `case_history_status_reference` y
`procedure_history_status_reference` cumplen para los suyos: impedir que se borre un valor que
alguna vez se usó. `catalog_kind` distingue de cuál de los tres se trata.

Aquí sí conviene una tabla con discriminador y no tres: las tres claves foráneas apuntarían a
tablas distintas y una restricción por cada una obligaría a tres tablas casi idénticas. Se
acepta perder la clave foránea al catálogo a cambio de no triplicar; **la protección se
mantiene** porque la comprobación de uso histórico consulta esta tabla antes de borrar.

## Historial: se usa `audit_event`

No hay tabla nueva de historial. La restricción `CHECK` de `entity_type` se amplía con
`PENDING_TASK`, `PENDING_TASK_TYPE`, `PRIORITY` y `PENDING_TASK_STATUS`, sustituyéndola con
`DROP CONSTRAINT` + `ADD CONSTRAINT`, que no toca ninguna fila.

Acciones registradas: `CREATE`, `UPDATE`, `COMPLETE`, `REVERT_COMPLETION`, `NOT_COMPLETED`,
`RESCHEDULE`, `VISIBILITY`.

`reason` es opcional salvo en `REVERT_COMPLETION`, donde el servicio lo exige antes de escribir
(§20.2). La base no puede expresar «obligatorio solo para esta acción» sin una restricción
condicional que complicaría la tabla compartida; se comprueba en el servicio y se verifica con
prueba.

`before_values` y `after_values` guardan la fecha anterior y la nueva en las reprogramaciones,
que es lo que la §22 pide conservar.

## Valores derivados: ninguno se persiste

| Valor | Cómo se obtiene |
| --- | --- |
| Días hábiles restantes | Evaluador de plazos, con el calendario compartido |
| Antigüedad sin plazo | Días hábiles entre `received_at` y hoy |
| Aviso de más de 15 días | Comparación de esa antigüedad, si el estado no es cumplido |
| Tiempo de atención | Días hábiles entre `received_at` y `completed_at` |
| N.º de reprogramaciones | Cuenta de entradas de historial con acción de reprogramación |

Ninguno existe como columna. El conteo de reprogramaciones se obtiene con **una** consulta
agregada para toda la página, no una por fila.

## Transiciones

| Origen | Acción | Destino | Efectos |
| --- | --- | --- | --- |
| Activo | Marcar cumplido | Cumplido | Fija `completed_at`; NO toca `scheduled_for` |
| Cumplido | Revertir | Pendiente | Limpia `completed_at`; **motivo obligatorio** |
| Activo | No cumplido | Reprogramado | `scheduled_for` al siguiente día hábil |
| Activo | Reprogramar | Sin cambio de estado | `scheduled_for` a la fecha elegida |

Revertir sobre algo no cumplido se rechaza. Cumplir dos veces es no-op sin historial.

Sin cobertura de calendario confirmada, «no cumplido» **no elige fecha**: avisa y no cambia
nada.

## Orden de transacciones

1. Pendiente: usuario actor, pendiente, catálogos referenciados cuando cambien, evidencia.
2. Catálogo: usuario actor, valor; comprobar usos actuales e históricos antes de eliminar.
3. Lecturas: snapshot sin `FOR UPDATE`; una comprobación de sesión por petición.

El mismo orden que en las funcionalidades anteriores, y por la misma razón.
