# Plan de implementación: Cancelar registros y los filtros que faltan

**Rama**: `009-cancelar-y-filtros` | **Fecha**: 2026-09-09 | **Spec**: [spec.md](./spec.md)

## Resumen

Tres historias que comparten una forma: **el servidor ya sabe hacerlo, la pantalla no lo
ofrece.** Sólo una pieza es servidor nuevo —cancelar un pendiente— y tiene un precedente
literal en la retirada de la actividad diaria de la 006.

Sin migración: la marca de visibilidad existe en las tres tablas desde su origen y
`audit_event.action` es texto libre, sin restricción que ampliar.

## Contexto técnico

**Lenguaje**: Java 21 (`release=21`)
**Dependencias**: Spring Boot 4.1.1, Thymeleaf, HTMX servido localmente, `JdbcClient` con SQL explícito (sin JPA)
**Almacenamiento**: PostgreSQL 17, esquema `sistema_juridico`. **Sin migración nueva**
**Pruebas**: JUnit 5, MockMvc para contratos, Testcontainers con PostgreSQL real, Playwright para el recorrido
**Plataforma**: Render (Docker) + Supabase (us-east-2)
**Objetivo de rendimiento**: ver la tabla de presupuesto; las cifras de partida están **medidas**, no supuestas
**Escala**: 5 usuarios

## Verificación contra la constitución

| Principio | Cómo lo cumple |
|---|---|
| **I. Idioma según destinatario** | La ruta nueva es `/pendientes/{id}/cancelar`. `RutasSegunInsumoTest` falla si aparece una palabra en inglés en una ruta |
| **II. Multiusuario y atribución** | Cancelar exige `puedeActuar` —responsable o jefa—, la misma regla que cumplir o reprogramar. Los filtros no cambian quién ve qué |
| **III. Índice físico y protección de datos** | Sin datos nuevos |
| **IV. Ligereza como requisito de aceptación** | Es la restricción que más aprieta y la que decide D3. Las cifras de partida están medidas |
| **V. Sin persistencia de derivados** | Nada que derivar. Los desplegables se leen en cada petición |
| **VI. Plazos hábiles centralizados** | El filtro «vencidos» compara con la fecha de hoy, que es una comparación de fechas, no un conteo de días hábiles. No toca el calendario |
| **VII. Trazabilidad inmutable** | Cancelar y devolver escriben en `audit_event` como cualquier otra acción. **Nada se borra** |
| **VIII. Un solo proceso** | Sin componentes nuevos |

**Resultado: pasa.**

## Decisiones de diseño

### D1 — La ruta se llama `cancelar`, y las de expediente se quedan como están

`POST /pendientes/{id}/cancelar`, porque §25 la nombra «Cancelar». Los expedientes ya
tienen `POST /judiciales/{id}/visibilidad` y su equivalente administrativo, y **no se
renombran**: funcionan, están probadas y cambiarlas sería tocar la 001 y la 002 sin
ganancia.

Quedan dos nombres para una misma idea. Es deliberado y no un descuido: en el pendiente
el insumo manda, y en el expediente el nombre describe lo que de verdad hace —cambiar la
visibilidad en el listado, que no es lo mismo que dar por concluido el asunto, como
advierten los comentarios de la V4 y la V8.

### D2 — Volver al listado sin abrir un redirect

RF-012 exige que una acción ejecutada desde la fila devuelva al listado tal como estaba.
Hoy `cumplir` redirige a la ficha del pendiente.

**El destino se fija en el código y sólo viaja la cadena de consulta.**

```text
POST /pendientes/{id}/cancelar   con  filtros=?ownerId=…&page=2
  → redirect:/pendientes + la cadena validada
```

Pasar una ruta completa en un parámetro —el clásico `volverA=`— es la forma de un
redirect abierto. Con la base fija, el destino no se puede desviar por definición. La
cadena se valida igualmente contra `^\?[A-Za-z0-9=&_%.\-]*$`: hoy no es explotable, pero
una cadena sin comprobar concatenada a una cabecera `Location` es la forma que se vuelve
un fallo de verdad en cuanto alguien cambie la base.

**Falta un constructor**: `comoQuery(int)` toma la página de destino, y el controlador
sólo expone `queryAnterior` y `querySiguiente`. Desde una fila hace falta la **página
actual**, así que se añade `queryActual = filtros.comoQuery(page)`. Sin esto, cada acción
devolvería a la página 0 en silencio.

**Caso que hay que nombrar, no descubrir**: marcar como cumplida la última fila de la
página 2 la saca del conjunto activo, así que volver a la página 2 puede mostrar otras
filas o ninguna. No es un fallo —es lo que significa que el listado sea el trabajo
pendiente— pero tiene que estar en un recorrido de aceptación.

### D3 — Los desplegables de filtro cuestan **una** consulta, no una por catálogo

Medido antes de decidir:

| Pantalla | Hoy |
|---|---|
| `/pendientes` | **6** consultas |
| `/judiciales` | **4** |
| `/administrativos` | **4** |

Con una consulta por catálogo, `/pendientes` necesitaría cuatro más —tipos, prioridades,
estados y responsables— y pasaría de **6 a 10**: un 67 % más en la pantalla más usada del
sistema, y para pintar unos desplegables de menos de veinte filas cada uno.

Se resuelve con **una sola consulta para todos los catálogos de la pantalla**, con la
forma de `UNION ALL` que ya usa `AgendaRepository` desde la 006.

El riesgo obvio de un `UNION` a mano es duplicar el criterio de `habilitados()` —el
`WHERE enabled = true ORDER BY lower(btrim(name))`— y que las dos copias se separen. Se
evita construyendo el `UNION` **dentro de `CatalogRepository`**, en un método
`habilitadosDeVarios(List<CatalogDefinition>)` que compone el mismo fragmento que usa
`habilitados`. Una definición, dos formas de pedirla.

Los responsables van aparte: otra tabla, otra forma, y una razón de fondo en D4.

| Pantalla | Después | Desglose |
|---|---|---|
| `/pendientes` | **8** | 6 + catálogos (1) + responsables (1) |
| `/judiciales` | **6** | 4 + 1 + 1 |
| `/administrativos` | **6** | 4 + 1 + 1 |

Son techos; la cifra medida se anota aquí al implementar, como en las funcionalidades
004, 006, 007 y 008.

### D4 — El desplegable de responsables no es `DestinosDeAsignacion.activos`

Es la reutilización que parece evidente y sería un error. `activos(UUID excepto)` excluye
a una cuenta a propósito —sirve para reasignar, donde ofrecerse a uno mismo no tiene
sentido— y **sólo trae las activas**.

Un filtro necesita lo contrario: **todas las cuentas, también las desactivadas**. Si una
abogada deja el puesto, sus pendientes siguen existiendo y hay que poder buscarlos por su
nombre; con la lista de activos, ese trabajo se vuelve inencontrable y parece de nadie.
Es lo que exige RF-021.

Consulta propia sobre `app_user`, sin filtrar por estado, marcando en el texto las cuentas
desactivadas para que se entienda por qué aparecen.

### D5 — La fila pinta sólo lo que esa persona puede ejecutar

RF-013 y RF-014. La comprobación de verdad **sigue en el servidor**: `puedeActuar` en el
servicio, como hasta ahora. Lo que la fila decide es qué se dibuja, no quién puede.

Cada fila necesita además su `version` en un campo oculto, igual que los formularios de la
ficha, para que dos personas actuando a la vez den un aviso de conflicto y no un cambio
pisado en silencio.

**Sin coste**: `puedeActuar` compara dos identificadores en memoria y `SELECCION` ya trae
el responsable de cada fila. No hay una consulta por fila que pueda colarse; CE-006 lo
comprueba.

### D6 — Cancelar copia la retirada de la actividad diaria

`ManualActivityService.retirar` de la 006 es la plantilla: `exigirPermiso`, versión,
`auditoria.registrar(ENTIDAD, id, "WITHDRAW", …)` con `{active: false}`.

Para el pendiente la acción se llama `CANCEL` y su vuelta `RESTORE`, junto a las cinco que
ya audita (`COMPLETE`, `NOT_COMPLETED`, `REVERT_COMPLETION`, `RESCHEDULE`…). **Sin
migración**: `audit_event.action` es `text` sin restricción, y `PENDING_TASK` ya está entre
las entidades válidas.

Para los expedientes no hace falta nada de servidor: `cambiarVisibilidad` ya existe con su
permiso, su bloqueo optimista, su no-op cuando no hay cambio y su evento `VISIBILITY`.

### D7 — Cancelar no se propaga

RF-008. Ocultar un expediente **no** cancela sus pendientes, y cancelar un pendiente no
toca su expediente. Una cascada retiraría trabajo de otras personas sin que nadie lo
hubiera decidido, y el bloque de la 008 muestra pendientes de cualquier responsable. Es una
línea de código que no se escribe, pero conviene que esté escrita la razón.

## Estructura

```text
src/main/java/pe/org/beneficencia/legalcontrol/
├── pendingtask/
│   ├── PendingTaskActionService.java   ← + cancelar / devolver (D6)
│   ├── PendingTaskController.java      ← + ruta cancelar, + queryActual (D2)
│   └── PendingTaskFilters.java         ← sin cambios: ya acepta todo
├── catalog/CatalogRepository.java      ← + habilitadosDeVarios (D3)
├── shared/OpcionesDeFiltro.java        ← NUEVO: catálogos + responsables por pantalla
├── judicialcase/JudicialCaseController.java        ← opciones al modelo
└── administrativeprocedure/AdministrativeProcedureController.java ← ídem

src/main/resources/templates/
├── pending-tasks/list.html             ← acciones por fila + filtros nuevos
├── judicial-cases/{list,detail}.html   ← filtros + acción de ocultar
└── administrative-procedures/{list,detail}.html ← ídem
```

## Presupuesto

**Cifras medidas al implementar**, no techos. Coinciden exactamente con lo previsto:

| Pantalla | Antes | Techo del plan | **Medido** | Comprobación |
|---|---|---|---|---|
| `/pendientes` | 6 | 8 | **8** | `PendingTaskQueryBudgetIT` |
| `/judiciales` | 4 | 6 | **6** | `QueryBudgetIT` |
| `/administrativos` | 4 | 6 | **6** | `ProcedureQueryBudgetIT` |
| `/pendientes` con 25 filas frente a 0 | — | idéntico | **8 y 8** | delata una consulta por fila |
| Opciones de filtro, 3 catálogos | — | 2 | **2** | `OpcionesDeFiltroIT` |

Las dos consultas de las opciones son las mismas con uno, tres o cinco catálogos: es lo
que compra el `UNION` de D3. Con una consulta por desplegable, `/pendientes` habría
costado 10.

## Riesgos

| Riesgo | Mitigación |
|---|---|
| El `UNION` de catálogos se separa de `habilitados()` | Se construye dentro de `CatalogRepository`, compartiendo el fragmento (D3) |
| Reutilizar `activos()` y perder a las cuentas desactivadas | D4, con prueba sobre una cuenta desactivada que tiene pendientes |
| La acción de la fila devuelve a la página 0 | `queryActual` (D2), con prueba desde la página 2 |
| Redirect abierto por el parámetro de vuelta | Base fija en código y validación de la cadena (D2) |
| Botones que el servidor va a rechazar | D5, con prueba desde una cuenta que no es la responsable |
| Precedencia de atributos de Thymeleaf en las filas | `th:if` (300) antes que `th:with` (400): nunca en el mismo elemento. Cuatro tropiezos en este proyecto |

## Verificación posterior al diseño

Se vuelve a evaluar con el diseño delante: **sigue pasando**. El principio IV es el que
aprieta y D3 existe justamente por él; el VII queda intacto porque las dos acciones nuevas
escriben en el historial como todas las demás y ninguna borra nada. Sin desviaciones que
justificar.
