# Contrato de pantallas — Funcionalidad 003

Rutas visibles en español, las tres primeras fijadas por el insumo (§25, §26, §32). Todas
exigen sesión. Todas devuelven HTML; no hay API de datos.

## Listado

`GET /pendientes`

Filtros opcionales: `q`, `ownerId`, `typeId`, `priorityId`, `statusId`, `linkedTo`
(`judicial`|`administrative`|`none`|`any`), `deadlinePresence`, `overdue`, `visibility`,
`sort` (`scheduledFor`|`deadline`|`priority`|`title`), `direction`, `page`.

Orden y visibilidad contra **lista cerrada**; valor fuera de ella devuelve 422. Filtros de
texto parametrizados con `%` y `_` escapados. Página de 25, fuera de rango devuelve vacío
recuperable.

Columnas de la §25: título, expediente relacionado, tipo, prioridad, estado, fecha programada,
fecha límite, días hábiles restantes y antigüedad.

Los pendientes sin plazo con más de quince días hábiles muestran su aviso **en texto**.

## Pendientes de hoy

`GET /pendientes/hoy`

Muestra los programados para hoy **y los vencidos de días anteriores que sigan activos**: si
solo mostrara los de hoy, lo que se quedó atrás desaparecería de la vista justo cuando más
importa.

No incluye los cumplidos.

## Tareas cumplidas

`GET /cumplidos`

Columnas de la §32: título, tipo, expediente, fecha de recepción, fecha programada, fecha
límite, fecha de cumplimiento, tiempo de atención y número de reprogramaciones. Los dos
últimos se calculan al consultar.

Un pendiente revertido desaparece de aquí.

## Alta, edición y ficha

`GET /pendientes/nuevo` · `POST /pendientes`
`GET /pendientes/{id}` · `GET /pendientes/{id}/editar` · `POST /pendientes/{id}`

Solo el título es obligatorio. El responsable lo fija el servidor con quien registra.

El formulario permite vincular a un expediente judicial **o** a uno administrativo. Elegir
ambos se rechaza.

La edición exige el `version` del formulario: discrepancia → 409 sin guardar; igualdad de todos
los valores → no-op sin historial.

## Acciones

| Ruta | Efecto |
| --- | --- |
| `POST /pendientes/{id}/cumplir` | Estado a cumplido, fija el instante, sale de la lista activa |
| `POST /pendientes/{id}/revertir` | Vuelve a pendiente; **`motivo` obligatorio** |
| `POST /pendientes/{id}/no-cumplido` | Fecha programada al siguiente día hábil, estado reprogramado |
| `POST /pendientes/{id}/reprogramar` | Fecha programada a la elegida; guarda anterior y nueva |
| `POST /pendientes/{id}/visibilidad` | Cambia solo `active`; no toca el estado |

Todas exigen `version` y CSRF. Todas revalidan el permiso dentro de la transacción.

`revertir` sin motivo devuelve el formulario con el error, sin cambiar nada.
`revertir` sobre algo no cumplido devuelve 409.
`no-cumplido` sin cobertura de calendario devuelve el aviso y **no cambia la fecha**.

## Historial

`GET /pendientes/{id}/historial`

Solo lectura, paginado de 25, del cambio más reciente al más antiguo. Muestra acción, autor,
responsable del momento, fechas anterior y nueva, y motivo. La descripción legible —«Reprogramado
del 03/09 al 08/09»— se compone al mostrar.

Consultarlo no genera entradas.

## Catálogos

`GET /tipos-de-pendiente` · `GET /prioridades` · `GET /estados-de-pendiente`

Consulta abierta a cualquier sesión. Las escrituras —crear, cambiar disponibilidad, eliminar—
exigen JEFA, comprobado en el servidor. Un ABOGADO recibe 403 aunque componga la petición.

## Códigos de respuesta

| Situación | Código |
| --- | --- |
| Filtro u orden fuera de lista cerrada | 422 |
| Sin permiso sobre el pendiente | 403 |
| Pendiente inexistente | 404 |
| Versión obsoleta, o revertir algo no cumplido | 409 |
| Escritura sin token CSRF | 403 |

## Presupuesto

Máximo 6 consultas de dominio en los listados y 8 en la ficha, con **una** lectura del
calendario y **una** agregación de reprogramaciones por pantalla, compartidas por todas las
filas. Verificado con el contador exacto de consultas.
