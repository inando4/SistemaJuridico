# Modelo de datos: Cancelar registros y los filtros que faltan

**Ninguna tabla, ninguna columna, ninguna migración.** Lo que sigue documenta lo que se
lee y las dos estructuras en memoria que aparecen.

---

## Lo que ya existe (sin cambios)

### La marca de visibilidad

Las tres tablas la tienen desde su origen:

| Tabla | Columna | Desde |
|---|---|---|
| `judicial_case` | `active boolean NOT NULL DEFAULT true` | V4 |
| `administrative_procedure` | `active boolean NOT NULL DEFAULT true` | V8 |
| `pending_task` | `active boolean NOT NULL DEFAULT true` | V9 |

Los comentarios de la V4 y la V8 fijan lo que significa, y conviene repetirlo aquí porque
es lo que impide confundir dos cosas:

> Solo visibilidad en el listado corriente. **NO** significa concluido: eso es una
> situación procesal y vive en `procedural_status_id`.

Son **dos ejes distintos**. Un procedimiento en estado «Archivado» sigue a la vista si su
marca de visibilidad está puesta, y así debe ser: el estado describe el trámite, la
visibilidad describe si estorba en la lista de trabajo.

### `audit_event`

Sin cambios. `action` es `text NOT NULL` **sin restricción de valores**, así que las
acciones nuevas no necesitan migración. La única lista cerrada es sobre `entity_type`, y
`PENDING_TASK` ya está en ella desde la 003.

---

## Acciones nuevas en el historial

| Acción | Entidad | Antes | Después |
|---|---|---|---|
| `CANCEL` | `PENDING_TASK` | `{active: true}` | `{active: false}` |
| `RESTORE` | `PENDING_TASK` | `{active: false}` | `{active: true}` |

Se suman a las que ya escribe el pendiente: `CREATE`, `UPDATE`, `COMPLETE`,
`NOT_COMPLETED`, `REVERT_COMPLETION`, `RESCHEDULE`.

Los expedientes siguen usando `VISIBILITY`, que ya escriben hoy con el antes y el después
en el mismo formato. **No se renombra**: cambiaría el significado de los eventos ya
guardados, y el historial es inmutable por el principio VII.

`reason` queda **nulo**: RF-005 no pide motivo.

---

## Estructuras en memoria

### `OpcionesDeFiltro` (nueva)

Lo que necesita una pantalla para pintar sus desplegables de filtro. Existe para que las
tres listas pidan lo mismo de la misma forma y para que el coste sea **dos consultas**, no
una por desplegable.

| Campo | Contenido | Origen |
|---|---|---|
| `catalogos` | Las opciones de cada catálogo de esa pantalla, agrupadas | **Una** consulta (`habilitadosDeVarios`) |
| `responsables` | Todas las cuentas, activas y desactivadas | **Una** consulta sobre `app_user` |

**Por qué las cuentas desactivadas también**: si una abogada deja el puesto, sus pendientes
siguen existiendo. Sin su nombre en el desplegable ese trabajo deja de poder buscarse y
parece de nadie (RF-021). Se marcan en el texto para que se entienda por qué aparecen.

### `CatalogRepository.habilitadosDeVarios` (método nuevo)

Devuelve las opciones de varios catálogos en **una** consulta, con una columna que dice de
cuál es cada fila:

```text
SELECT 'TIPOS_DE_PENDIENTE' AS catalogo, id, name FROM pending_task_type WHERE …
UNION ALL
SELECT 'PRIORIDADES', id, name FROM priority WHERE …
UNION ALL
…
```

El fragmento de cada rama —`WHERE enabled = true ORDER BY lower(btrim(name))`— es **el
mismo** que usa `habilitados`, compartido y no copiado. Dos copias del criterio acabarían
separándose, que es lo que hubo que corregir en la 004.

---

## Lo que no se persiste

Por el principio V:

- **Cuántos registros hay cancelados.** Se cuenta al pedirlo, si se pide.
- **Qué acciones puede ejecutar cada persona sobre cada fila.** Se decide en cada
  petición comparando dos identificadores en memoria; `SELECCION` ya trae el responsable
  de cada fila, así que no hay consulta por fila que pueda colarse.

---

## Índices

**Ninguno nuevo.** Los filtros nuevos son igualdades sobre columnas que ya existen
(`owner_id`, los identificadores de catálogo) y una comparación de fecha para «vencidos».
Con expedientes en el orden de centenas y pendientes en el de millares, un recorrido
filtrado está muy por debajo del presupuesto.

Se deja anotado como el primer sitio donde mirar si el volumen crece: crear el índice
después es una migración de una línea y no cambia ninguna consulta.
