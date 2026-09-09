# Fase 0 — Investigación y decisiones

Todo lo de aquí se comprobó contra el código y el esquema actuales, no de memoria.

---

## Decisión 1 — La actividad manual es el segundo uso de un catálogo, y la comprobación de borrado no lo sabe

**Hallazgo**: `CatalogRepository.enUsoActual` compone `SELECT count(*) FROM <tablaEnUso> WHERE <columnaEnUso> = :id`. **Una tabla, una columna.** `CatalogDefinition.TIPOS_DE_PENDIENTE` las fija en `pending_task` / `pending_task_type_id`. Hasta ahora ningún catálogo tenía dos usuarios, así que el registro nunca necesitó más.

Se trazó qué pasa exactamente si `manual_activity` referencia `pending_task_type` y la jefa intenta borrar un tipo usado solo por una actividad manual:

1. `enUsoActual` mira solo `pending_task` → **falso**, aunque sí esté en uso.
2. `enUsoHistorico` mira `pending_task_history_reference` → **verdadero**, porque el alta de la actividad habrá escrito ahí su referencia de catálogo.
3. Se rechaza el borrado con «Este valor aparece en el historial y no puede borrarse».

O sea: **no se cae, pero miente**. Dice «historial» de un valor que está en uso ahora mismo, y la jefa no recibe la sugerencia correcta. Y el que no se caiga depende por entero de que alguien se acuerde de llamar a `referenciarCatalogosDePendiente` al auditar el alta. Si se olvida, el borrado pasa las dos comprobaciones, llega a la base y la clave foránea `ON DELETE RESTRICT` lanza una excepción de integridad: la jefa vería una traza en vez de un mensaje.

**Decisión**: ampliar `CatalogDefinition` para que `tablaEnUso`/`columnaEnUso` admitan **más de un par**, y que `enUsoActual` los recorra. Es un cambio pequeño en una clase ya probada, y deja de depender de que nadie olvide una llamada.

**Alternativas descartadas**:

- *No tocar nada y confiar en `enUsoHistorico`*: funciona por accidente, da el mensaje equivocado, y convierte una llamada olvidable en el único seguro contra una traza en pantalla.
- *No usar clave foránea y guardar el nombre del tipo como texto*: quitaría el problema quitando la integridad. Además rompe RF-015c, que necesita distinguir un tipo del catálogo de uno escrito a mano.

---

## Decisión 2 — El tipo son dos columnas, no una

**Decisión**: `pending_task_type_id uuid REFERENCES pending_task_type(id) ON DELETE RESTRICT` y `other_type text`, con un `CHECK` que impide que estén las dos.

**Fundamento**: RF-015a admite tres estados —sin tipo, del catálogo, escrito a mano— y RF-015c exige poder distinguirlos. Con una sola columna de texto habría que adivinar por el contenido si «Audiencia» salió del desplegable o lo escribió alguien, y un recuento futuro los mezclaría sin advertirlo.

La clave foránea además decide lo que pasa cuando la jefa **renombra** un tipo: el cambio alcanza a las actividades pasadas, que es el mismo comportamiento que ya tiene `pending_task` y lo que la jefa espera de un catálogo que administra. El texto libre, en cambio, es un registro de lo que se escribió ese día y no cambia nunca — que también es lo correcto, porque nadie lo administra.

**Alternativas descartadas**:

- *Una columna de texto con el nombre*: pierde la integridad, el renombrado y la distinción de RF-015c.
- *Una tabla de tipos propia de la actividad manual*: un sexto catálogo que administrar para algo que el usuario ya puede escribir libremente. El insumo pide libertad, no otra pantalla de mantenimiento.

---

## Decisión 3 — Cómo se resuelve la tensión con el principio V en la sección 33

**Decisión**: no existe ninguna tabla de «actividad del día». Se persiste **solo** la actividad manual; los pendientes cumplidos se leen de `pending_task` en cada consulta.

**Fundamento**: el principio V prohíbe persistir valores derivados. Un pendiente cumplido ya está guardado con su `completed_at`: copiarlo a una tabla de actividad sería exactamente el derivado prohibido, y bastaría con revertir el cumplimiento para que las dos versiones dejaran de coincidir. La actividad manual **no deriva de nada**: si no se guarda, no existe en ninguna parte. Por eso una se calcula y la otra se escribe, y no es una excepción al principio sino su aplicación.

Consecuencia comprobable, y por eso CE-005 se redactó como se redactó: consultar un día pasado dos veces puede dar resultados distintos si entre medias alguien revirtió un cumplido o registró una actividad con fecha anterior. Eso **es lo correcto**: la pantalla refleja el estado actual, no una foto.

---

## Decisión 4 — El día se corta con marcas de tiempo, no casteando a fecha

**Decisión**: la frontera del día se calcula en Java con `ClockConfig.ZONA` y llega a SQL como dos marcas de tiempo: `completed_at >= :inicio AND completed_at < :inicioDelSiguiente`.

**Fundamento**: `pending_task.completed_at` es `timestamptz`. `CAST(completed_at AS date)` lo resuelve en la zona **del servidor**, no en la de Lima: un pendiente cumplido a las 19:30 hora de Lima caería en el día siguiente si el servidor está en UTC. El caso límite de la especificación —23:50 pertenece a ese día— exige lo contrario.

Hay un segundo motivo, y es el que importa para el principio IV: una expresión sobre la columna inutiliza el índice `pending_task_cumplidos (completed_at DESC)`. Comparar la columna desnuda contra dos límites lo aprovecha.

**Nota de higiene**: `ClockConfig.ZONA` ya existe, pero dos sitios repiten `ZoneId.of("America/Lima")` a mano (`PendingTaskRepository:234`, `PendingTaskController:361`). Esta feature añade varias fronteras de día más. Se usa la constante, y de paso se unifican esos dos.

---

## Decisión 5 — El calendario es una consulta por rango, con cinco ramas

**Decisión**: un `UNION ALL` de cinco ramas, una por cada columna de fecha que produce un evento, sobre el rango pedido:

| Rama | Origen | Tipo de evento |
|---|---|---|
| 1 | `pending_task.scheduled_for` | Programado |
| 2 | `pending_task.deadline` | Vencimiento |
| 3 | `judicial_case.deadline` | Vencimiento |
| 4 | `judicial_case.last_action_date` | Actuación |
| 5 | `administrative_procedure.deadline` | Vencimiento |

**Fundamento**: es lo que sostiene CE-007. La alternativa natural —preguntar por cada día de la vista— son 31 consultas para un mes y 1 para un día, y el problema no se nota con datos de prueba. Con una sola consulta por rango, el mes y el día cuestan lo mismo, y eso es lo que `AgendaQueryBudgetIT` comprueba: **el mismo número de consultas en las tres vistas**.

Un pendiente con fecha programada y fecha límite dentro del rango produce **dos** eventos, y así debe ser: son dos cosas distintas que pasan en dos días distintos.

**El reparto en día, semana y mes no toca la base**: la consulta devuelve eventos con fecha; agruparlos por día y repartirlos en semanas es aritmética sobre la lista, en `RejillaDelMes`.

---

## Decisión 6 — Las audiencias no se detectan por nombre

**Decisión**: cada evento de un pendiente muestra **el nombre de su tipo**, sea el que sea. No hay ninguna comparación con la cadena `'Audiencia'`.

**Fundamento**: RF-024 pide que las audiencias se vean en el calendario, y «Audiencia» es un valor del catálogo `pending_task_type` que la jefa administra: puede renombrarlo, deshabilitarlo o añadir «Audiencia de conciliación». Un `WHERE tipo.name = 'Audiencia'` quedaría roto el día que lo renombre, en silencio y sin que ninguna prueba lo note.

Mostrar el tipo de cada evento cumple el requisito de forma más general y no depende de ningún nombre concreto: una audiencia se ve como audiencia porque su tipo se llama así, no porque el código lo busque.

---

## Decisión 7 — El buscador son tres consultas, no una

**Decisión**: una consulta por tipo de registro, cada una con su propia paginación.

**Fundamento**: los resultados se presentan agrupados por tipo (RF-005) y las tres tablas no comparten columnas —un judicial tiene demandante y demandado, un pendiente tiene prioridad y fecha programada—. Un `UNION` obligaría a rellenar con nulos las columnas que no aplican y **perdería la paginación por grupo**: no se podría avanzar en judiciales sin avanzar en los otros dos.

Tres es además un número **fijo**: no depende de cuántos resultados haya, que es lo que CE-008 exige. Buscar un término con 300 coincidencias cuesta las mismas tres consultas que uno con 3.

**Alternativas descartadas**:

- *Un `UNION ALL` de las tres*: pierde la paginación por grupo y obliga a un modelo de fila común que ninguna de las tres tiene.
- *Un `count(*)` por grupo para dar el total exacto*: tres consultas más, para un dato que RF-005 dejó fuera a propósito. Ver decisión 8.

---

## Decisión 8 — «Cuántos muestra y si hay más» ya está resuelto

**Decisión**: se reutiliza `Paging.limitConSondeo()`, que pide un registro de más que el tamaño de página.

**Fundamento**: es exactamente la respuesta a RF-005 y RF-009: si vuelven 26 con página de 25, hay más; se muestran 25 y se dice que hay más. Sin `count(*)`, sin `count(*) OVER ()`, sin coste que crezca con el resultado. Es el mecanismo que todos los listados del sistema ya usan, y escribirlo aquí es para que nadie alcance el `count(*)` por reflejo y rompa CE-008 sin darse cuenta.

---

## Decisión 9 — Qué campos se amplían, y qué pruebas existentes entran en el alcance

**Hallazgo comprobado** (`grep` sobre los tres repositorios, no de memoria):

| Repositorio | `q` busca hoy | RF-012 añade |
|---|---|---|
| `JudicialCaseRepository:` | `case_number`, `claimant`, `respondent` | `subject`, `notes` |
| `AdministrativeProcedureRepository:66` | `file_number`, `requesting_area`, `request` | `notes` |
| `PendingTaskRepository:90` | `title`, `description` | `notes` |

Las columnas **existen** en el esquema. Es un hueco de la consulta, no del modelo, y por eso RF-012 no necesita migración.

**Pruebas existentes que quedan dentro del alcance** por tocar esos repositorios:

- `JudicialCaseListContractTest`, `AdministrativeProcedureListContractTest`, `PendingTaskListContractTest`
- `ProcedureQueryBudgetIT`, `PendingTaskQueryBudgetIT`
- `PerformanceBudgetTest`, `ProcedurePerformanceTest`

Se enumeran aquí para que `/speckit-tasks` las cuente como trabajo y no aparezcan como fallos inesperados. Añadir dos `OR ... ILIKE` a una condición no debería cambiar ningún resultado existente, pero el escape de `%` y `_` sí se aplica a los campos nuevos igual que a los viejos, y eso necesita comprobación.

---

## Decisión 10 — Los presupuestos se escriben primero como invariantes

**Decisión**: cada presupuesto se enuncia como «el número no cambia al crecer X», y solo después como techo absoluto, marcado **provisional** hasta medirlo.

**Fundamento**: en la 005 el plan dijo 6 consultas y la medición dio 7. Ninguna era redundante: el número escrito era una estimación hecha antes de ejecutar nada, y lo que se corrigió fue el documento. Lo que sí encontró problemas de verdad fue la **invariante** —el mismo número con 50 pendientes que con 5—, porque esa no es una estimación sino una propiedad del diseño.

Aquí las invariantes son dos, y son las que hay que defender: el mes cuesta lo mismo que el día (CE-007), y 300 resultados cuestan lo mismo que 3 (CE-008).

---

## Decisión 11 — El `REVOKE` sobre `flyway_schema_history` es seguro

**Comprobación**: `application.yml` deja `spring.flyway.enabled: false` y le da credencial propia (`DB_MIGRATION_URL`, `DB_MIGRATION_USERNAME`, `DB_MIGRATION_PASSWORD`), separada de la de ejecución. La aplicación **no migra al arrancar**.

Por tanto, quitarle a `sistema_juridico_app` los permisos de escritura sobre `flyway_schema_history` no puede romper el arranque: ese rol nunca escribe ahí. Es la deuda que la 005 dejó anotada para la primera migración que hiciera falta, y esta lo es.
