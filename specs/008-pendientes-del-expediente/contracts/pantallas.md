# Contrato de pantallas: Pendientes relacionados

Lo que cada pantalla acepta y garantiza. Es lo que comprueban las pruebas de contrato con MockMvc.

---

## `GET /judiciales/{id}` — ficha de expediente judicial

**Cambia**: gana el bloque «Pendientes relacionados». Todo lo demás sigue igual.

**El modelo gana**

| Atributo | Contenido |
|---|---|
| `pendientesRelacionados` | Hasta una página de pendientes vinculados, por hacer primero |
| `hayMasPendientes` | `true` si el sondeo encontró una fila de más |
| `enlaceAPendientes` | `/pendientes` con el filtro de este expediente ya puesto |
| `enlaceANuevoPendiente` | `/pendientes/nuevo` con este expediente pre-seleccionado |

**Garantiza**

1. El bloque aparece **siempre**, también sin pendientes, con mensaje de lista vacía (RF-005).
2. Cada fila muestra título, **responsable**, estado, prioridad y fecha límite, y enlaza a `/pendientes/{id}` (RF-003).
3. Incluye cumplidos; excluye archivados (RF-002, RF-007).
4. **Una sola consulta** más que antes, y **el mismo número** con 2 vínculos que con 50 (CE-004, CE-005).
5. Un expediente inexistente sigue devolviendo 404, como hoy.
6. Un expediente archivado se abre igual que antes, con su bloque.

---

## `GET /administrativos/{id}` — ficha de procedimiento administrativo

Idéntico al anterior, punto por punto, con `/administrativos` en lugar de `/judiciales`.

Sustituye el hueco que dejó la feature 002 en `administrative-procedures/detail.html:66`, donde un comentario anunciaba que la sección llegaría con la funcionalidad de pendientes.

---

## `GET /pendientes` — listado

**Parámetros nuevos**

| Parámetro | Tipo | Por defecto |
|---|---|---|
| `judicialCaseId` | `UUID` | ninguno |
| `administrativeProcedureId` | `UUID` | ninguno |

**Valores nuevos en parámetros que ya existían**

| Parámetro | Valor nuevo | Significado |
|---|---|---|
| `visibility` | `notArchived` | No archivados, cumplidos incluidos |
| `sort` | `pendingFirst` | Lo que queda por hacer primero |

**Garantiza**

1. Con `judicialCaseId=X`, solo aparecen los pendientes de ese expediente (RF-008).
2. El filtro **sobrevive** a paginar, a ordenar y a aplicar otro filtro desde el formulario (RF-009). Son dos caminos distintos: la cadena de consulta de los enlaces y el campo oculto del formulario.
3. Con el filtro puesto, la pantalla **nombra el expediente** y ofrece quitarlo (RF-010).
4. Un identificador inexistente da lista vacía con aviso, **nunca un error de sistema** (RF-011).
5. Los dos identificadores a la vez se rechazan como filtro inválido: ningún pendiente puede colgar de ambos.
6. Sin filtro, el coste en consultas **no cambia**. Con filtro, sube exactamente en una (el nombre del expediente).
7. `visibility=notArchived` + `sort=pendingFirst` + `judicialCaseId=X` devuelve **exactamente** lo que muestra la ficha de X, en el mismo orden (CE-003).

---

## `GET /pendientes/nuevo` — alta

**Parámetros nuevos**: `judicialCaseId` o `administrativeProcedureId` (UUID, opcionales).

**Garantiza**

1. El expediente indicado llega **pre-seleccionado** en su desplegable (RF-012).
2. Se puede **cambiar o quitar** antes de guardar; no queda fijado (RF-013).
3. Un expediente **archivado**, o uno fuera del corte de 500 del desplegable, aparece igualmente como opción. El vínculo no se pierde en silencio (R5).
4. Si el expediente es de **otra persona**, la pantalla lo advierte y lo nombra, antes de guardar (RF-015).
5. Un identificador inexistente **se rechaza**; no se llega a un formulario que produciría un vínculo roto (RF-016).
6. Los dos a la vez se rechazan (RF-014, sección 9 del insumo).
7. Una consulta más que antes, y **una sola** para las tres preguntas: si existe, cómo se llama, de quién es.

---

## `POST /pendientes` — crear

**No cambia su firma.** Cambia lo que valida.

**Garantiza**

1. Un `judicialCaseId` que no existe se rechaza con un error de validación **en el formulario**, no con un error 500 desde la clave foránea (RF-016).
2. Lo mismo para `administrativeProcedureId`.
3. Los dos a la vez se siguen rechazando, como hoy.
4. El pendiente queda a nombre de **quien lo registra**, venga de donde venga. Esta feature no cambia la propiedad (RF-015).
5. Deja el **mismo rastro en el historial** que un alta desde el listado. No hay vía sin auditoría (RF-018).

---

## Lo que ninguna pantalla hace

- Ninguna otorga permisos por mostrar. Ver el bloque no habilita a actuar sobre lo que contiene (RF-017).
- Ninguna oculta pendientes por ser de otro responsable. La lectura es compartida (principio II).
- Ninguna escribe nada al pintar la ficha.
