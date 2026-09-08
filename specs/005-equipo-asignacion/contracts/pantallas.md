# Fase 1 — Contrato de pantallas

Rutas, parámetros y textos exactos. Los textos son los que verá el área, y van **con tildes y eñes**: la guardia contra regresión ortográfica de `RevisionConNavegadorTest` los comprueba en pantalla.

---

## `GET /equipo` — vista de equipo

**Quién**: cualquier usuario autenticado. La lectura es compartida (RF-021); no depende del rol.

**Parámetros**: ninguno.

**Título**: `Carga del equipo`

**Navegación**: se añade a `fragments/navegacion.html` entre «Cumplidos» y «Judiciales», con `actual = 'equipo'`:

```html
<a th:href="@{/equipo}" th:attrappend="aria-current=${actual == 'equipo'} ? 'page'">Equipo</a>
```

**Contenido**: una fila por persona con cuenta activa —la jefa incluida—, ordenada de mayor a menor carga de la semana.

| Columna | Encabezado | Contenido |
|---|---|---|
| Persona | `Responsable` | Nombre. La jefa lleva la marca `(jefatura)` |
| Suma | `Carga de la semana` | `vencidos + esta semana`. **Es la cifra que ordena y tiene que verse** (RF-017a) |
| Vencidos | `Vencidos` | Enlace a `/pendientes?...` |
| Semana | `Vence esta semana` | Enlace a `/pendientes?...` |
| Sin plazo | `Sin plazo, antiguos` | Enlace, o el aviso de calendario |
| Total | `Activos` | Enlace |
| Acción | — | `Ver pendientes` |

**Rango de fechas visible**: bajo el título, `Semana del D de MMMM al D de MMMM`, para que el lector sepa qué está contando el número.

**Enlaces de cada recuento** (RF-020: el listado debe contener exactamente lo que el número contaba):

| Recuento | Destino |
|---|---|
| Vencidos | `/pendientes?alerta=vencidos&ownerId={id}&visibility=all` |
| Vence esta semana | `/pendientes?alerta=semana&ownerId={id}&visibility=all` |
| Sin plazo, antiguos | `/pendientes?alerta=sin-plazo-antiguos&ownerId={id}&visibility=all` |
| Activos | `/pendientes?ownerId={id}&visibility=active` |

`alerta=semana` es un valor **nuevo** del filtro que la 004 dejó en `PendingTaskRepository`; los otros ya existen. El `ownerId` y la `visibility` van explícitos en el enlace porque sin ellos el listado mostraría lo de todo el mundo y el número no cuadraría con la lista — que es exactamente el desajuste que la 004 tuvo que corregir en sus tarjetas.

**Sin calendario confirmado**, la columna «Sin plazo, antiguos» sustituye el número por:

```
Faltan días no laborables por revisar
```

Es el mismo texto que ya usan el panel y las alertas. Las demás columnas **siguen mostrando su número** (`research.md`, decisión 3).

**Estado vacío**: si solo hay una persona activa, la tabla la muestra igual. No hay estado vacío posible: siempre existe al menos la cuenta con la que se está mirando.

---

## `POST /judiciales/{id}/responsable` — reasignar un expediente judicial

**Quién**: solo jefa. Se comprueba **en el servidor**; ocultar el formulario no es autorización.

**Parámetros**: `ownerId` (destino), `version` (del expediente, para el bloqueo optimista).

**Respuestas**:

| Situación | Resultado |
|---|---|
| Correcto | Redirección a la ficha con `Expediente reasignado a {nombre}. Se traspasaron {n} pendientes.` |
| Sin permiso | 403 con `Solo la jefatura puede reasignar expedientes.` |
| Destino inactivo | Vuelve al formulario con `Esa cuenta está inactiva y no puede recibir trabajo.` |
| Destino igual al actual | Vuelve con `El expediente ya está a nombre de esa persona.` y **no escribe historial** |
| Versión desfasada | `Otra persona modificó este expediente.` |

**`POST /administrativos/{id}/responsable`** es idéntico, con «procedimiento» en lugar de «expediente». Se enuncia aparte para que no se implemente uno y se olvide el otro (RF-001 y RF-002 están separados por lo mismo).

---

## Formulario de reasignación — `fragments/reasignacion.html`

Un solo fragmento reutilizado por las tres fichas. Que sea uno y no tres es la razón de existir del paquete `assignment`.

```html
<form th:fragment="formulario(destino, actual, version, aviso)" th:action="@{${destino}}" method="post">
  <label for="ownerId">Nuevo responsable</label>
  <select id="ownerId" name="ownerId" required> … cuentas activas … </select>
  <input type="hidden" name="version" th:value="${version}">
  <p th:if="${aviso != null}" role="alert" class="warning" th:text="${aviso}"></p>
  <button type="submit">Reasignar</button>
</form>
```

**El aviso previo** (RF-004b) se arma antes de mostrar el formulario y dice lo que va a pasar:

- Sin pendientes: `Este expediente no tiene pendientes. Solo cambiará el responsable del expediente.`
- Todos del responsable saliente: `Se traspasarán {n} pendientes, incluidos los ya cumplidos.`
- Con pendientes de terceros: `Se traspasarán {n} pendientes, incluidos los ya cumplidos. {m} son de otras personas ({nombres}), que perderán el acceso de edición.`

El tercer texto es el que impide que la reasignación le quite trabajo a alguien en silencio. La jefa decide con el dato delante.

**El desplegable** lista las cuentas **activas**, la jefa incluida, excluyendo al responsable actual. Si no queda ninguna, en lugar de un desplegable vacío:

```
No hay otra cuenta activa a la que reasignar.
```

---

## `POST /pendientes/{id}/responsable` — reasignar un pendiente suelto

**Quién**: solo jefa.

**Condición**: el pendiente **no** debe estar vinculado a ningún expediente (RF-027).

| Situación | Resultado |
|---|---|
| Correcto | `Pendiente reasignado a {nombre}.` |
| Vinculado a un expediente | 400 con `Este pendiente pertenece a un expediente. Se reasigna reasignando el expediente.` |
| Resto | Igual que en los expedientes |

En la ficha de un pendiente **vinculado**, el formulario no se muestra; en su lugar va un enlace al expediente del que cuelga, para que la jefa llegue a la operación correcta en vez de encontrarse un botón ausente sin explicación.

---

## Cambios en el alta de expedientes (RF-012, RF-013)

En `judicial-cases/form.html` y `administrative-procedures/form.html`, **solo si quien mira es la jefa**:

```html
<label for="ownerId">Responsable</label>
<select id="ownerId" name="ownerId"> … cuentas activas, la propia por omisión … </select>
```

Un abogado no ve el campo y queda como responsable (RF-013). Si un abogado envía `ownerId` de todas formas, el servidor lo **ignora** y usa su propia identidad: la autorización no depende de lo que el formulario muestre.

El historial de este alta guarda `after_values` con el responsable y `before_values` **nulo** — la ausencia de responsable anterior es lo que distingue asignar de reasignar (RF-014).

---

## Textos que la guardia ortográfica comprobará

`Carga del equipo` · `Responsable` · `Carga de la semana` · `Vencidos` · `Vence esta semana` · `Sin plazo, antiguos` · `Activos` · `Ver pendientes` · `Nuevo responsable` · `Reasignar` · `(jefatura)` · `Semana del … al …` · `Solo la jefatura puede reasignar expedientes.` · `Esa cuenta está inactiva y no puede recibir trabajo.` · `El expediente ya está a nombre de esa persona.` · `Este pendiente pertenece a un expediente. Se reasigna reasignando el expediente.` · `No hay otra cuenta activa a la que reasignar.` · `Faltan días no laborables por revisar`
