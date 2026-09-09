# Investigación: Cancelar registros y los filtros que faltan

Fase 0. Todo verificado leyendo el código o midiendo, no de memoria.

---

## R1 — ¿Cuánto cuestan hoy los tres listados?

**Medido** con `ContadorDeConsultas`, antes de decidir nada:

| Pantalla | Consultas |
|---|---|
| `/pendientes` | **6** |
| `/judiciales` | **4** |
| `/administrativos` | **4** |

**Por qué importa la medición y no el techo**: `PendingTaskQueryBudgetIT` afirma `≤ 10`
para `/pendientes`. Si hubiera partido de ese número, cuatro consultas más habrían parecido
un 40 % y en realidad son un 67 %. El techo era generoso; el dato es otro.

---

## R2 — ¿Una consulta por catálogo o una para todos?

**Decisión**: una para todos, dentro de `CatalogRepository`.

**Cuentas**: `/pendientes` necesita cuatro desplegables nuevos —tipos, prioridades, estados
y responsables—. Una consulta cada uno lleva la pantalla más usada del sistema de 6 a 10.

**Alternativas descartadas**:

- *Cuatro consultas, sin más.* Es lo más simple y en un sistema de cinco usuarios no se
  notaría. Se descarta porque el principio IV convierte el presupuesto en condición de
  aceptación, y gastar un 67 % de la pantalla más usada en pintar cuatro desplegables de
  menos de veinte filas es un mal cambio cuando la alternativa cuesta quince líneas.
- *Un `UNION` escrito a mano en cada controlador.* Duplicaría el criterio de
  `habilitados()` —`WHERE enabled = true ORDER BY lower(btrim(name))`— en tantos sitios
  como pantallas. Es exactamente la forma que se corrigió en la 004 y que la 008 evitó por
  construcción.
- *Guardar los catálogos en memoria entre peticiones.* Rápido, pero introduce invalidación:
  deshabilitar un catálogo dejaría de verse hasta reiniciar. No compensa para ahorrar una
  consulta.

**Lo elegido**: `CatalogRepository.habilitadosDeVarios(List<CatalogDefinition>)`, que
compone con `UNION ALL` **el mismo fragmento** que usa `habilitados`, más una columna que
diga de qué catálogo es cada fila. Una definición del criterio, dos formas de pedirla. La
forma tiene precedente: `AgendaRepository` resuelve el calendario con cinco ramas
`UNION ALL` en una consulta desde la 006.

---

## R3 — ¿Sirve `DestinosDeAsignacion.activos` para el desplegable de responsables?

**Decisión**: no. Consulta propia sobre `app_user`, sin filtrar por estado.

**Por qué no**: la firma es `activos(UUID excepto)`. Hace dos cosas que están bien para lo
suyo y mal para un filtro:

1. **Excluye una cuenta**, porque al reasignar no tiene sentido ofrecerse a uno mismo.
2. **Sólo trae las activas.**

Un filtro necesita lo contrario. Si una abogada deja el puesto y su cuenta se desactiva,
sus pendientes siguen existiendo: con la lista de activos, ese trabajo deja de poder
buscarse por su nombre y parece de nadie. Es lo que exige RF-021.

Las cuentas desactivadas se marcan en el texto de la opción, para que se entienda por qué
aparecen.

---

## R4 — ¿Cómo vuelve al listado una acción ejecutada desde una fila?

**Decisión**: destino fijo en el código, y sólo viaja la cadena de consulta.

**El problema**: `cumplir` redirige hoy a `/pendientes/{id}`, la ficha. Desde una fila hay
que volver al listado con sus filtros, su página y su orden (RF-012).

**Alternativas descartadas**:

- *Un parámetro con la ruta completa* (`volverA=/pendientes?…`). Es la forma canónica del
  redirect abierto: basta con que alguien pase una dirección externa.
- *La cabecera `Referer`.* No siempre llega —hay navegadores y configuraciones que la
  suprimen— y además la controla el cliente igual que un parámetro, sin la ventaja de
  poder validarla contra algo conocido.

**Lo elegido**: el controlador escribe `redirect:/pendientes` y le concatena la cadena
recibida. El destino no se puede desviar porque no viene de fuera. La cadena se valida
igualmente contra `^\?[A-Za-z0-9=&_%.\-]*$` o vacía: hoy `/pendientes//algo` sigue siendo
una ruta propia y no es explotable, pero una cadena sin comprobar pegada a una cabecera
`Location` es la forma que se convierte en fallo en cuanto alguien cambie la base.

**Falta una pieza**: el controlador expone `queryAnterior` y `querySiguiente`, ambas de
`comoQuery(int)`, que toma la **página de destino**. Desde una fila hace falta la página
**actual**. Sin añadir `queryActual = filtros.comoQuery(page)`, cada acción devolvería a la
página 0 sin avisar de nada.

---

## R5 — ¿Qué pasa al actuar sobre la última fila de una página?

**Decisión**: se acepta y se nombra en un recorrido de aceptación.

Marcar como cumplida la última fila visible de la página 2 la saca del conjunto activo. Al
volver a la página 2, el contenido puede ser otro, o estar vacía.

No es un fallo: es lo que significa que el listado por defecto sea el trabajo que queda.
Pero es el tipo de comportamiento que, sin estar escrito, se descubre en producción y se
denuncia como error. Va al recorrido.

---

## R6 — ¿Hace falta migración para las acciones nuevas?

**Decisión**: no.

**Comprobado en `V7__audit.sql`**: `action` es `text NOT NULL` **sin ninguna restricción de
valores**. La única lista cerrada es `audit_event_entidad_valida`, sobre `entity_type`, y
`PENDING_TASK` ya está dentro —lo escriben `COMPLETE`, `RESCHEDULE` y las demás desde la
003—.

Las acciones nuevas son `CANCEL` y `RESTORE`. La marca de visibilidad existe en las tres
tablas desde su origen, con el comentario de la V4 y la V8 avisando de que **no** significa
concluido: eso es una situación procesal y vive en su propio campo.

---

## R7 — ¿Cómo se llama la ruta nueva?

**Decisión**: `POST /pendientes/{id}/cancelar`. Los expedientes conservan
`/{id}/visibilidad`.

**Por qué dos nombres**: §25 dice «Cancelar» para el pendiente, y el insumo manda sobre el
nombre que ve el usuario (principio I). En el expediente, «visibilidad» describe lo que de
verdad hace —sacarlo del listado corriente— y el propio esquema advierte de que eso no es
darlo por concluido.

**Comprobado**: `RutasSegunInsumoTest` afirma que **ninguna** ruta contiene una palabra de
su lista inglesa, y `visibility` está en esa lista. `cancelar` y `visibilidad` son ambas
españolas y pasan. La ruta nueva no obliga a tocar `FIJADAS_POR_EL_INSUMO`, que enumera
pantallas, no acciones.

---

## R8 — ¿La retirada de la actividad diaria sirve de plantilla?

**Decisión**: sí, literalmente.

`ManualActivityService.retirar` hace lo que hace falta y en el orden que hace falta:
`exigirPermiso`, versión para el conflicto, y `auditoria.registrar` con `{active: false}`.
Para el pendiente el permiso es `puedeActuar` —responsable o jefa—, que es la regla que ya
rige cumplir, reprogramar y revertir.

Para los expedientes **no hay servidor que escribir**: `cambiarVisibilidad` ya existe con
permiso, bloqueo optimista, no-op cuando no hay cambio, y evento `VISIBILITY` con el antes
y el después.

---

## R9 — Precedencia de atributos en las filas con acciones

**Decisión**: `th:if` y `th:with` nunca en el mismo elemento, y la condición de permiso en
un elemento propio.

El orden es `th:replace` (100) < `th:each` (200) < `th:if` (300) < `th:with` (400). Cada
fila va a combinar un `th:each`, una condición de permiso y probablemente una variable
local. Han sido cuatro tropiezos en este proyecto —el último, la rejilla del mes de la
006— y todos compilaban limpios y pintaban de menos sin dar error.
