# Contrato de pantallas: Cancelar registros y los filtros que faltan

Lo que cada pantalla acepta y garantiza. Es lo que comprueban las pruebas de contrato.

---

## `POST /pendientes/{id}/cancelar` — nueva

**Acepta**

| Parámetro | Para qué |
|---|---|
| `version` | Bloqueo optimista, igual que cumplir o reprogramar |
| `filtros` | La cadena de consulta a la que volver. Opcional |

**Garantiza**

1. El pendiente deja de aparecer en el listado de trabajo, en el bloque de su expediente,
   en las alertas, en el calendario y en la pantalla de hoy (RF-006).
2. **No se borra**: se encuentra pidiendo los registros ocultos, y se puede devolver.
3. Escribe `CANCEL` en el historial con quién y cuándo, y `reason` nulo (RF-003, RF-005).
4. Sólo lo ejecutan el responsable y la jefa. A cualquier otro se le rechaza **en el
   servidor**, no sólo ocultándole el botón (RF-004).
5. Con una `version` desfasada avisa del conflicto y **no** aplica el cambio (RF-009).
6. Cancelar un pendiente ya cancelado no hace nada y no escribe una segunda entrada.
7. **No toca su expediente** (RF-008).
8. Con `filtros`, redirige al listado con esa cadena; sin él, al listado sin filtros. El
   destino es fijo en el código: la cadena **no** puede desviar el redirect (D2).
9. Una cadena que no encaje con el patrón permitido se ignora, y se vuelve al listado sin
   filtros. Nunca produce un error.

---

## `POST /pendientes/{id}/devolver` — nueva

El inverso. Mismas garantías, acción `RESTORE`, y el pendiente vuelve al listado de
trabajo.

Devolver uno que no estaba cancelado no hace nada.

---

## `POST /judiciales/{id}/visibilidad` y `/administrativos/{id}/visibilidad`

**No cambian.** Ya existen con permiso, bloqueo optimista, no-op sin cambio y evento
`VISIBILITY`.

Lo que cambia es que **por fin hay una pantalla que las usa**: la ficha ofrece ocultar y
volver a mostrar (RF-007).

**Garantiza**, ahora comprobado desde la interfaz:

1. Ocultar saca el expediente del listado corriente; se encuentra con los registros
   ocultos y se puede volver a mostrar.
2. **No cancela sus pendientes** (RF-008). Un expediente con doce pendientes activos los
   conserva activos, y siguen en la lista de trabajo de quien los tenga.
3. El estado procesal «Archivado» y la visibilidad **son ejes distintos**: poner el estado
   no oculta, y ocultar no cambia el estado.

---

## `GET /pendientes` — listado

**Controles nuevos en pantalla** (los parámetros ya se aceptaban):

`ownerId`, `typeId`, `priorityId`, `statusId`, `overdue`.

**Acciones nuevas por fila**: abrir, editar, marcar como cumplido y cancelar.

**Garantiza**

1. Cada fila ofrece las cuatro acciones (RF-010).
2. «No cumplido» y «Reprogramar» **no** están en la fila: piden fecha o motivo y viven en
   la ficha (RF-011).
3. Una acción desde la fila devuelve al listado con **la misma página, los mismos filtros
   y el mismo orden** (RF-012).
4. Si esa acción vació la página —la última fila activa de la página 2, por ejemplo—, se
   vuelve a esa página igualmente y se muestra vacía con su salida. No es un error (R5).
5. A cada persona se le pintan **sólo** las acciones que puede ejecutar (RF-013), y las
   que ya no proceden no se ofrecen (RF-014).
6. Las acciones de la fila y las de la ficha hacen lo mismo: mismas comprobaciones, mismo
   historial, mismo aviso de conflicto (RF-015).
7. Los filtros se combinan y sobreviven a paginar y a ordenar (RF-019).
8. Coste: **8 consultas como techo**, y **el mismo número con 2 filas que con 25**.

---

## `GET /judiciales` — listado

**Controles nuevos**: `ownerId`, `proceduralStatusId`, `subject`, `overdue`. Con los que
ya tenía, quedan los ocho que enumera §27 (RF-016).

**Garantiza**

1. Los ocho filtros se aplican **sin escribir la dirección a mano** (CE-004).
2. Se combinan entre sí, y sobreviven a paginar y a ordenar (RF-019).
3. «Quitar filtros» los retira todos (RF-020).
4. El desplegable de responsables incluye las **cuentas desactivadas**, marcadas como
   tales. Sin ellas, el trabajo de quien dejó el puesto sería inencontrable (RF-021, D4).
5. Un catálogo deshabilitado desaparece del desplegable pero **no** desaparece de los
   registros que ya lo tenían.
6. Coste: **6 consultas como techo**, de las cuales **una** para todos los catálogos.

---

## `GET /administrativos` — listado

Igual, con `ownerId`, `administrativeStatusId` y `overdue`. Mismo techo de 6.

---

## Lo que ninguna pantalla hace

- Ninguna borra nada (RF-022).
- Ningún filtro cambia quién ve qué: filtrar por otra persona muestra su trabajo, como
  siempre (RF-023).
- Ninguna acción se propaga de un expediente a sus pendientes ni al revés (RF-008).
- Ningún botón dibujado sustituye a la comprobación del servidor (D5).
