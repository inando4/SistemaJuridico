# Investigación: Pendientes relacionados en la ficha del expediente

Fase 0. Todo lo de aquí se verificó leyendo el código, no de memoria.

---

## R1 — ¿Existen las columnas del vínculo?

**Decisión**: no hace falta migración.

**Comprobación**: `PendingTaskRepository.SELECCION` ya selecciona `t.judicial_case_id` con su `LEFT JOIN judicial_case jc` y `t.administrative_procedure_id` con `LEFT JOIN administrative_procedure ap`, y trae de paso `jc.case_number` y `ap.file_number`. Las columnas vienen de la V9.

**Alternativas descartadas**: una tabla de relación N–N habilitaría el vínculo múltiple, que la sección 9 del insumo aplaza explícitamente («posteriormente se puede desarrollar»). Añadirla ahora sería inventar alcance.

---

## R2 — ¿Cuánto cuesta ampliar `PendingTaskFilters`?

**Decisión**: dos componentes nuevos, `judicialCaseId` y `administrativeProcedureId`.

**Comprobación**: el record se construye en **dos** sitios de todo el código — `porDefecto()` (línea 47) y `PendingTaskController` (línea 93). Un record de 15 componentes es incómodo de leer, pero el coste de tocarlo es de dos líneas.

**Alternativas descartadas**:

- *Reutilizar `linkedTo` metiendo el UUID dentro* (`"judicial:<uuid>"`). Obligaría a analizar la cadena en el repositorio y rompería `VINCULOS.contains(linkedTo)` en `valido()`. La feature 005 ya dejó la lección sobre parámetros con nombres inventados.
- *Un objeto `Vinculo(tipo, id)` como componente único*. Más limpio en abstracto, pero no encaja con `@RequestParam` sin un conversor, y el binding de Spring sobre records anidados en formularios GET es justo donde este proyecto no quiere sorpresas.

---

## R3 — ¿Qué visibilidad produce el conjunto de la ficha?

**Decisión**: ninguna de las tres. Se añade `notArchived` (`t.active = true`).

**Comprobación**: el `switch` de `listar` mapea `active` a `t.active = true AND t.completed_at IS NULL`, `inactive` a `t.active = false` y `all` a nada. El conjunto que piden RF-002 (cumplidos sí) y RF-007 (archivados no) es `t.active = true` a secas.

**Por qué importa y no es un capricho**: sin el cuarto valor, el enlace «Ver todos» miente. Con `active` esconde los cumplidos que la ficha acababa de mostrar —y CE-007 dice que nada vinculado puede quedar inalcanzable—; con `all` muestra archivados que la ficha ocultaba. Además, sin un valor que reproduzca el conjunto de la ficha, CE-003 deja de ser comprobable: no habría con qué comparar.

**Alternativas descartadas**:

- *Que la ficha use `all` y se acepte la diferencia.* Barato, pero convierte CE-003 en una afirmación que ninguna prueba puede sostener, y es justo la afirmación que detecta que los dos caminos se han separado.
- *Un método de repositorio dedicado con su propio `WHERE`.* Es lo que hizo falta corregir en la feature 004, cuando la condición de una tarjeta del panel y la de su listado se separaron y la tarjeta contaba lo que su lista no enseñaba. El javadoc de `listar` lo deja escrito.

---

## R4 — ¿Cómo se ordena «lo que queda por hacer primero»?

**Decisión**: un quinto valor de `sort`, `pendingFirst`:

```sql
ORDER BY (t.completed_at IS NULL) DESC, t.deadline ASC NULLS LAST, t.id ASC
```

**Comprobación**: `orden()` es un `switch` sobre `sort` con cuatro casos y un `default`; ninguno separa cumplidos de por-hacer. En PostgreSQL, `(t.completed_at IS NULL)` es un booleano y `DESC` pone `true` —lo pendiente— delante.

El desempate por `t.id ASC` no es decorativo: todos los órdenes existentes lo llevan, porque sin él la paginación con `LIMIT/OFFSET` puede repetir o saltarse filas cuando hay empates.

**Alternativa descartada**: ordenar en Java después de traer las filas. Rompería la paginación, que se resuelve en SQL con el sondeo de una fila extra.

---

## R5 — ¿Qué pasa con un vínculo pre-seleccionado que no está en el desplegable?

**Decisión**: añadir la opción que falta al construir el modelo.

**Comprobación** — dos caminos reales hasta el fallo:

1. `JudicialCaseRepository.porId` es `SELECCION + " WHERE c.id = :id"`, **sin filtro por `active`**. La ficha de un expediente archivado se abre con normalidad.
2. `PendingTaskCatalogs.poblar` llena los desplegables con `WHERE active = true ... LIMIT 500`.

Un archivado (o el 501.º) no está entre las opciones, `th:selected` no encaja con ninguna, el `<select>` envía vacío y **el vínculo se pierde al guardar sin ningún aviso**.

**Hallazgo colateral**: `catalogos.poblar` se llama en cuatro sitios, entre ellos `formularioEdicion`. O sea que **hoy ya** se pierde el vínculo al editar un pendiente cuyo expediente se archivó después. No lo introduce esta feature; lo arregla de paso.

**Alternativas descartadas**:

- *Campo oculto con el identificador y desplegable deshabilitado.* Contradice RF-013 —el vínculo tiene que poder cambiarse o quitarse— y dos campos con el mismo `name` enlazan de forma impredecible.
- *Quitar el `WHERE active = true` del desplegable.* Ofrecería expedientes archivados a cualquiera que abra el formulario, que es justo lo que ese filtro evita.
- *Subir el `LIMIT`.* Aplaza el problema sin resolver el caso del archivado.

---

## R6 — ¿Qué ocurre hoy con un identificador de vínculo inventado?

**Decisión**: comprobar la existencia en `PendingTaskValidator`, del lado del POST.

**Comprobación**: el validador solo mira `form.vinculoDoble()`. No hay ninguna comprobación de existencia, así que un UUID inventado llega a la restricción de clave foránea de la V9 y sale como error 500.

Desde el formulario el desplegable lo hacía inalcanzable en la práctica. Un parámetro en la URL lo pone a un clic, así que entra en el alcance (RF-016).

**Aprovechamiento**: la consulta que R5 necesita para construir la opción devuelve la fila del expediente. Si no hay fila, no existe. **Una consulta responde a las dos preguntas**, y de camino trae el responsable que necesita el aviso de RF-015.

---

## R7 — ¿Por dónde viaja el filtro entre peticiones?

**Decisión**: por los dos sitios, porque son caminos distintos.

**Comprobación**: `comoQuery(int)` reconstruye la cadena de consulta enlace a enlace para paginar; el formulario de `list.html` es `<form th:action="@{/pendientes}" method="get">` y un formulario GET **descarta todo lo que no sean sus propios campos**.

Con solo el primero, el filtro sobrevive a paginar y ordenar pero desaparece en cuanto el usuario pulsa «Aplicar filtros». Con solo el segundo, al revés. RF-009 exige ambos.

---

## R8 — ¿De dónde sale el número del expediente para el aviso del listado?

**Decisión**: una consulta puntual por identificador, solo cuando el filtro viene puesto.

**Justificación**: RF-010 pide nombrar el expediente, y los filtros solo llevan su UUID. Un identificador en pantalla no le dice nada a nadie.

**Coste**: una consulta más, y únicamente en las peticiones filtradas; `/pendientes` sin filtro no cambia. `PendingTaskQueryBudgetIT` incorpora el caso filtrado, porque si no el parámetro nuevo no entraría nunca en la medición.

---

## R9 — Precedencia de atributos en el bloque nuevo

**Decisión**: `th:if` y `th:with` nunca en el mismo elemento.

**Justificación**: el orden es `th:each` (200) < `th:if` (300) < `th:with` (400). En el mismo elemento, `th:if` se evalúa **antes** que el `th:with` que define la variable que la condición consulta, y la condición ve algo indefinido. Ha costado tres fallos en este proyecto, el último en la rejilla del mes de la feature 006: compilaba limpio, no daba error y pintaba cero eventos. Solo lo cazó una prueba de integración.

El bloque nuevo combina condición y variable local, así que el riesgo es el mismo. La variable se define en el elemento contenedor.
