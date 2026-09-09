# Modelo de datos: Pendientes relacionados en la ficha del expediente

**No se crea ninguna tabla, ninguna columna ni ninguna migración.** Esta feature lee relaciones que existen desde la V9. Lo que sigue documenta lo que se lee y las estructuras en memoria que aparecen.

---

## Lo que ya existe (sin cambios)

### `pending_task`

Las dos columnas del vínculo, ambas opcionales:

| Columna | Tipo | Referencia |
|---|---|---|
| `judicial_case_id` | `uuid` | → `judicial_case (id)` |
| `administrative_procedure_id` | `uuid` | → `administrative_procedure (id)` |

**Regla de la sección 9 del insumo**: como máximo una de las dos. Se valida en la aplicación (`PendingTaskForm.vinculoDoble()`), no con una restricción de la base.

Las claves foráneas garantizan que un vínculo apunta a algo que existe. Hoy esa garantía se cobra como error 500 cuando la aplicación no comprueba antes; R6 lo corrige.

### `judicial_case`, `administrative_procedure`

Sin cambios. De cada uno se leen el identificador, el número (`case_number` / `file_number`), el responsable y `active`.

---

## Estructuras en memoria

### `ExpedienteVinculado` (nueva)

Una fila que resume el expediente al que se quiere vincular un pendiente. Existe para que **una sola consulta** responda a tres preguntas que se hacen en el mismo instante:

| Campo | Para qué |
|---|---|
| `id` | Identificar |
| `numero` | La opción del desplegable (R5) y el aviso del listado (R8) |
| `responsableId` | Comparar con quien registra (RF-015) |
| `responsableNombre` | Nombrarlo en el aviso |
| `activo` | Saber si el desplegable ya lo trae o hay que añadirlo |

Que exista la fila es, a la vez, la comprobación de existencia de RF-016. Que no exista es el rechazo.

**Sin esta estructura** harían falta tres consultas separadas —existe, cómo se llama, de quién es— para una pantalla que debe costar una.

### `PendingTaskFilters` (ampliado)

Dos componentes nuevos:

| Componente | Tipo | Por defecto | Condición SQL |
|---|---|---|---|
| `judicialCaseId` | `UUID` | `null` | `t.judicial_case_id = :judicialCaseId` |
| `administrativeProcedureId` | `UUID` | `null` | `t.administrative_procedure_id = :administrativeProcedureId` |

`valido()` rechaza que lleguen los dos a la vez. Un pendiente no puede colgar de ambos, así que la combinación devolvería siempre vacío sin explicar por qué.

---

## Valores nuevos en las listas cerradas

### `VISIBILIDADES` — se añade `notArchived`

| Valor | Condición | Cumplidos | Archivados |
|---|---|---|---|
| `active` | `t.active = true AND t.completed_at IS NULL` | no | no |
| **`notArchived`** | `t.active = true` | **sí** | no |
| `inactive` | `t.active = false` | — | sí |
| `all` | — | sí | sí |

`notArchived` es el conjunto de la ficha: RF-002 y RF-007 juntos.

### `ORDENES` — se añade `pendingFirst`

```sql
ORDER BY (t.completed_at IS NULL) DESC, t.deadline ASC NULLS LAST, t.id ASC
```

Lo pendiente delante, dentro de cada grupo lo más urgente primero, y `t.id` como desempate para que la paginación no repita ni se salte filas.

---

## Lo que no se persiste

Por el principio V de la constitución, ninguna de estas cosas se guarda:

- **Cuántos pendientes tiene un expediente.** Se derivan de la consulta de cada petición.
- **Si hay más de los que caben.** Sale del sondeo de una fila extra que ya hace `Paging.limitConSondeo()`, sin `count(*)`.
- **El estado del plazo** de cada fila. Lo calcula el servicio central de días hábiles en cada petición (principio VI).

---

## Índices

**No se crea ninguno.** `judicial_case_id` y `administrative_procedure_id` son claves foráneas; PostgreSQL no las indexa por sí solo, pero con expedientes en el orden de centenas y pendientes en el de millares, un recorrido secuencial filtrado por igualdad está muy por debajo del presupuesto.

Se deja anotado como el primer sitio donde mirar si el volumen crece: crear el índice después es una migración de una línea y no cambia ninguna consulta.
