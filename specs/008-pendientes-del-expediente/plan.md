# Plan de implementación: Pendientes relacionados en la ficha del expediente

**Rama**: `008-pendientes-del-expediente` | **Fecha**: 2026-09-08 | **Spec**: [spec.md](./spec.md)

## Resumen

Las dos fichas de expediente mostrarán los pendientes vinculados y ofrecerán crear uno ya vinculado.

**La decisión que ordena todo el resto**: la ficha no obtiene su lista con una consulta propia, sino con **la misma llamada** que atiende al listado general. Para que eso sea posible, el listado gana un filtro por expediente concreto, un valor de visibilidad y un orden; y entonces el enlace «Ver todos» de la ficha es, literalmente, la misma consulta sin acotar.

La alternativa —un método de repositorio dedicado a la ficha— exigiría mantener dos fragmentos `WHERE` sincronizados. El javadoc de `listar` ya registra lo que ocurrió en la feature 004 cuando la condición de una tarjeta y la de su listado se separaron: la tarjeta contaba pendientes que su propio listado no mostraba. Reutilizar el camino elimina la posibilidad en vez de vigilarla.

Sin migración: las columnas del vínculo existen desde la V9.

## Contexto técnico

**Lenguaje**: Java 21 (`release=21`)
**Dependencias**: Spring Boot 4.1.1, Thymeleaf, HTMX servido localmente, `JdbcClient` con SQL explícito (sin JPA)
**Almacenamiento**: PostgreSQL 17, esquema `sistema_juridico`. **Sin migración nueva** — se leen columnas de la V9
**Pruebas**: JUnit 5, MockMvc para contratos web, Testcontainers con PostgreSQL real para integración, Playwright (Chromium headless) para el recorrido
**Plataforma**: Render (Docker) + Supabase (us-east-2)
**Tipo**: aplicación web de un solo proceso
**Objetivo de rendimiento**: una consulta más por ficha; invariante al número de pendientes vinculados; ficha cargada (50 vínculos) por debajo de 500 ms
**Escala**: 5 usuarios, expedientes en el orden de centenas

## Verificación contra la constitución

*Puerta previa a la fase 0. Se vuelve a evaluar tras la fase 1.*

| Principio | Cómo lo cumple |
|---|---|
| **I. Idioma según destinatario** | Toda la pantalla en español con tildes; identificadores y SQL en el idioma del código |
| **II. Multiusuario, acceso compartido y atribución** | El bloque muestra pendientes de cualquier responsable y **nombra a cada uno** (RF-004). Ver no otorga permiso: las acciones sobre cada pendiente siguen en su ficha, con sus reglas (RF-017) |
| **III. Índice físico y protección de datos** | Sin datos nuevos. Los fixtures son sintéticos |
| **IV. Ligereza como requisito de aceptación** | CE-004 y CE-005 son la puerta: coste medido y **invariante** al número de vínculos. Se verifica con `PendingTaskQueryBudgetIT` |
| **V. Interpretación activa sin persistencia de derivados** | No se persiste nada. El recuento de vínculos y el aviso de «hay más» se derivan en cada petición mediante el sondeo de una fila extra, sin `count(*)` |
| **VI. Plazos hábiles con cálculo centralizado** | Las filas muestran fecha límite; el semáforo de plazo se calcula con el mismo servicio central que usan las demás pantallas, nunca con aritmética propia |
| **VII. Trazabilidad inmutable** | El alta desde la ficha usa el mismo servicio que el alta desde el listado, así que deja el mismo rastro (RF-018). No se abre una vía sin auditoría |
| **VIII. Arquitectura de un solo proceso** | Sin componentes nuevos |

**Resultado: pasa.** Sin desviaciones que justificar.

## Decisiones de diseño

### D1 — Dos componentes nuevos en `PendingTaskFilters`

`judicialCaseId` y `administrativeProcedureId`, con los nombres de las columnas y la convención de `ownerId`. El record pasa de 13 a 15 componentes; solo tiene **dos** sitios de construcción (`porDefecto()` y el controlador), así que el cambio está contenido.

`valido()` gana la comprobación de que no lleguen los dos a la vez: filtrar por un expediente judicial *y* uno administrativo es una contradicción, porque la regla de la sección 9 impide que un pendiente cuelgue de ambos, y devolvería siempre vacío sin decir por qué.

En el repositorio son dos líneas: `anadirIgual(...)`, igual que `ownerId`.

### D2 — Un cuarto valor de visibilidad: `notArchived`

Los tres valores actuales son:

| Valor | Condición | Significado |
|---|---|---|
| `active` | `t.active = true AND t.completed_at IS NULL` | Lo que queda por hacer |
| `inactive` | `t.active = false` | Lo retirado |
| `all` | — | Todo |

**Ninguno produce el conjunto que la ficha necesita.** RF-002 pide que los cumplidos se vean (son historia del expediente) y RF-007 pide que los archivados no. Eso es exactamente `t.active = true`, y no está.

Con `active`, «Ver todos» mostraría *menos* de lo que la ficha acaba de enseñar y los cumplidos que no cupieron quedarían inalcanzables, incumpliendo CE-007. Con `all`, mostraría archivados que la ficha ocultaba. Añadir el cuarto valor cuesta cuatro retoques pequeños —`VISIBILIDADES`, el `switch`, el `<select>` de `list.html` y `PendingTaskListContractTest`— y a cambio hace que CE-003 sea una **igualdad comprobable** en vez de una coincidencia que hay que vigilar.

Etiqueta en pantalla: «Visibles, incluidos los cumplidos».

### D3 — Un quinto orden: `pendingFirst`

RF-002 pide que lo que queda por hacer aparezca primero. `orden()` traduce `sort` a columna; ninguno de los cuatro órdenes actuales lo hace.

```text
ORDER BY (t.completed_at IS NULL) DESC, t.deadline ASC NULLS LAST, t.id ASC
```

Con esto, la ficha y «Ver todos» coinciden también en el **orden**, no solo en el conjunto. Etiqueta: «Por hacer primero».

### D4 — La ficha llama a `listar`, sin método propio

```text
filtros = por defecto
        + judicialCaseId = el de la ficha
        + visibility     = notArchived
        + sort           = pendingFirst
lista   = pendientes.listar(filtros, Paging.of(0), hoy)
enlace  = "/pendientes" + filtros.comoQuery(0)
```

El sondeo de una fila extra que ya hace `Paging.limitConSondeo()` da el aviso de «hay más» sin `count(*)`, igual que en el resto del sistema. Una consulta por ficha, invariante al número de vínculos porque `SELECCION` ya trae el nombre del responsable en su `JOIN app_user` —no hay resolución fila por fila que pueda colarse.

### D5 — Preservar el vínculo pre-seleccionado que no está en el desplegable

`PendingTaskCatalogs.poblar` llena los desplegables con `WHERE active = true ... LIMIT 500`. Dos huecos reales:

1. **`porId` no filtra por `active`**, así que la ficha de un expediente archivado se abre con normalidad. Su «+ Crear pendiente relacionado» pre-seleccionaría un identificador que el desplegable no contiene.
2. Pasados 500 expedientes, los que quedan fuera del corte tienen el mismo problema.

En ambos casos `th:selected` no encajaría con ninguna opción, el `<select>` enviaría vacío y **el vínculo se perdería en silencio al guardar**.

La solución es **añadir la opción que falta** en vez de fijar el valor con un campo oculto. Un campo oculto junto a un desplegable deshabilitado contradiría RF-013 (el vínculo debe poder cambiarse o quitarse) y dos campos con el mismo nombre enlazan mal.

Esto cierra además un fallo que **ya existe hoy**: `formularioEdicion` usa el mismo `poblar`, así que editar un pendiente cuyo expediente se archivó después ya pierde el vínculo sin avisar. No es una regresión que introduzca esta feature; es una que la feature tapa de paso.

### D6 — La existencia del vínculo se comprueba en el POST, no solo al pintar

Hoy `PendingTaskValidator` solo comprueba `vinculoDoble()`. Un `judicialCaseId` inventado llega a la restricción de clave foránea y sale como error 500. Desde el formulario el desplegable lo impedía; con un parámetro en la URL pasa a ser trivial de alcanzar, así que entra en el alcance (RF-016).

La comprobación va en el validador, del lado del POST. La misma consulta que D5 necesita para construir la opción responde a esta pregunta: **una consulta, no dos**.

### D7 — Dos portadores distintos para el filtro

El filtro tiene que sobrevivir a dos caminos que no se pisan:

- **Paginar y ordenar** pasan por `comoQuery`, que construye el enlace → añadir ahí `judicialCaseId` y `administrativeProcedureId`.
- **Aplicar filtros** pasa por el `<form method="get">` de `list.html`, que **solo envía sus propios campos** → hace falta un `<input type="hidden">` que lo transporte.

Olvidar el segundo haría que el filtro desapareciera en cuanto el usuario tocara cualquier otro filtro. Es el caso que RF-009 exige.

### D8 — El nombre del expediente filtrado cuesta una consulta más, solo cuando hay filtro

RF-010 pide decir **de qué expediente** se están viendo los pendientes, y para eso hace falta su número, que no está en los filtros. Una consulta puntual por identificador, únicamente cuando el filtro viene puesto. `PendingTaskQueryBudgetIT` incorpora este caso; sin él, el parámetro nuevo no se mediría nunca.

### D9 — El aviso de propiedad ajena

RF-015 pide que el formulario advierta, antes de guardar, cuándo el expediente es de otra persona. Hoy dice «El responsable sera usted» sin más contexto. Se añade, solo cuando se llega con un vínculo pre-seleccionado y el expediente es de otro, una línea que lo nombre.

El dato del responsable del expediente sale de la misma consulta de D5/D6, que ya trae la fila. **Sigue siendo una sola consulta.**

## Estructura

### Documentación

```text
specs/008-pendientes-del-expediente/
├── spec.md
├── plan.md              ← este archivo
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/pantallas.md
├── checklists/requirements.md
└── tasks.md             ← lo genera /speckit-tasks
```

### Código que se toca

```text
src/main/java/pe/org/beneficencia/legalcontrol/
├── pendingtask/
│   ├── PendingTaskFilters.java      ← +2 componentes, valido(), comoQuery()
│   ├── PendingTaskRepository.java   ← +2 anadirIgual, +caso visibilidad, +caso orden
│   ├── PendingTaskController.java   ← +2 @RequestParam, pre-selección en /nuevo
│   ├── PendingTaskCatalogs.java     ← poblar con opción añadida (D5)
│   ├── PendingTaskValidator.java    ← existencia del vínculo (D6)
│   └── ExpedienteVinculado.java     ← NUEVO: la consulta única de D5/D6/D9
├── judicialcase/JudicialCaseController.java              ← lista + enlaces
└── administrativeprocedure/AdministrativeProcedureController.java ← ídem

src/main/resources/templates/
├── judicial-cases/detail.html                 ← bloque nuevo
├── administrative-procedures/detail.html      ← bloque nuevo (sustituye el hueco de 002)
├── pending-tasks/list.html                    ← hidden, aviso, opciones nuevas
├── pending-tasks/form.html                    ← aviso de propiedad ajena
└── fragments/…                                ← fragmento compartido del bloque
```

## Presupuesto

| Pantalla | Antes | Después | Comprobación |
|---|---|---|---|
| `/judiciales/{id}` | *n* | *n* + 1 | CE-005 |
| `/administrativos/{id}` | *m* | *m* + 1 | CE-005 |
| ambas, con 2 vs. 50 vínculos | — | **idéntico** | CE-004 |
| `/pendientes` sin filtro | *k* | *k* | sin cambio |
| `/pendientes?judicialCaseId=…` | — | *k* + 1 | D8 |
| `/pendientes/nuevo?judicialCaseId=…` | *j* | *j* + 1 | D5+D6+D9 comparten consulta |

Las cifras exactas se miden en la implementación y se anotan aquí, como en las features 004, 006 y 007. Los números de esta tabla son el techo que la medición no puede superar.

## Riesgos

| Riesgo | Mitigación |
|---|---|
| Los dos fragmentos `WHERE` se separan y ficha y listado discrepan | **Eliminado por construcción**: es la misma llamada (D4). CE-003 lo comprueba igualmente |
| El vínculo pre-seleccionado se pierde en silencio | D5, con prueba sobre un expediente archivado |
| El filtro desaparece al tocar otro filtro | D7, con prueba que aplica un segundo filtro y comprueba que el expediente sigue |
| Una consulta por fila para el responsable | `SELECCION` ya lo trae; CE-004 lo delata si alguien lo cambia |
| Precedencia de atributos de Thymeleaf en el bloque nuevo | `th:if` (300) va antes que `th:with` (400): nunca en el mismo elemento. Ya costó tres fallos en este proyecto |

## Verificación posterior al diseño

Se vuelve a evaluar la puerta con el diseño encima de la mesa: **sigue pasando**. El principio IV es el que más aprieta y las decisiones D4, D8 y D9 están tomadas precisamente para no rebasar su presupuesto; el principio VII queda intacto porque no aparece ninguna vía de escritura nueva. Sin entradas en una tabla de desviaciones, porque no hay ninguna.
