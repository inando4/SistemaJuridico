# Contrato de pantallas — Funcionalidad 002

Rutas visibles en español, como las fija el insumo (§29 y §30). Identificadores y nombres de
plantilla en inglés.

Todas exigen sesión iniciada. Todas devuelven HTML; no hay API de datos.

## Listado

`GET /administrativos`

Parámetros, todos opcionales: `q`, `ownerId`, `administrativeStatusId`, `requestingArea`,
`deadlinePresence` (`any`|`with`|`without`), `overdue`, `visibility`
(`active`|`inactive`|`all`), `sort` (`fileNumber`|`owner`|`deadline`), `direction`
(`asc`|`desc`), `page`.

Orden y visibilidad se validan contra **lista cerrada**; un valor fuera de ella devuelve 422,
no un listado vacío. Los filtros de texto van parametrizados con `%` y `_` escapados.

Columnas mostradas, las del Excel actual (§29): número, abogado responsable, número de
expediente, área solicitante, pedido, estado, fecha de recepción, fecha límite, días hábiles
restantes y observaciones.

Página de 25. Una página fuera de rango devuelve vacío recuperable, no error. Los filtros se
conservan al paginar y al volver de la ficha.

Estado vacío: siempre con salida —retirar filtros o registrar uno nuevo—, nunca un callejón.

## Ficha

`GET /administrativos/{id}`

Muestra, como pide la §30: información del expediente, estado actual, plazos e historial.
Los campos sin dato se muestran como ausentes, no se omiten: quien lee debe poder distinguir
«no hay dato» de «no cargó la pantalla».

**Fuera de alcance de esta funcionalidad**: la sección «Pendientes relacionados» de la §30.
Se deja el hueco previsto en el diseño de la pantalla, sin construirlo.

## Alta y edición

`GET /administrativos/nuevo` · `POST /administrativos`
`GET /administrativos/{id}/editar` · `POST /administrativos/{id}`

Solo `fileNumber` es obligatorio. El responsable lo fija el servidor con el usuario que crea
el registro; un intento de asignar a otra persona se rechaza en lugar de ignorarse.

La edición exige el `version` del formulario. Discrepancia → 409 sin guardar. Igualdad de
todos los valores → no-op sin historial.

Errores de validación: todos a la vez, por campo, devolviendo lo escrito. Nunca se trunca un
texto en silencio.

`POST /administrativos/{id}/visibilidad` cambia solo `active`. No toca el estado.

## Historial

`GET /administrativos/{id}/historial`

Solo lectura, paginado de 25, del cambio más reciente al más antiguo. Distingue autor de
responsable del momento. Consultarlo no genera entradas.

## Catálogo de estados

`GET /estados-administrativos` — consulta abierta a cualquier sesión.

`POST /estados-administrativos`
`POST /estados-administrativos/{id}/disponibilidad`
`POST /estados-administrativos/{id}/eliminar`

Estas tres exigen JEFA, comprobado en el servidor y no solo ocultando botones. Un ABOGADO
recibe 403 aunque componga la petición a mano.

Borrar solo si el estado nunca se usó ni aparece en ningún historial; en caso contrario se
ofrece deshabilitarlo.

## Códigos de respuesta

| Situación | Código |
| --- | --- |
| Filtro u orden fuera de lista cerrada | 422 |
| Sin permiso sobre el registro | 403 |
| Registro inexistente | 404 |
| Versión obsoleta al guardar | 409 |
| Escritura sin token CSRF | 403 |

## Presupuesto

Máximo 6 consultas de dominio en el listado y 7 en la ficha, con **una** lectura del calendario
por consulta, compartida por todas las filas. Verificado contando transacciones reales.
