# Fase 1 — Contrato de pantallas

Rutas, parámetros y respuestas. **Los nombres de parámetro de aquí son los que van en el código y en las plantillas**, en inglés como el resto del sistema (`ownerId`, `page`, `q`), aunque las rutas y los textos sean en español (principio I).

Esto no es una formalidad: en la 005, el contrato inventó parámetros en español (`responsable`, `visibilidad`) que no existían, los enlaces no filtraban y el listado enseñaba los pendientes de todo el mundo. El fallo lo encontró el recorrido con navegador, no las pruebas.

---

## `GET /buscar` — buscador global

| Parámetro | Valores | Por omisión |
|---|---|---|
| `q` | término de búsqueda, **mínimo 3 caracteres** | — (obligatorio) |
| `pageJ` | página del grupo judicial, desde 0 | `0` |
| `pageA` | página del grupo administrativo | `0` |
| `pageP` | página del grupo de pendientes | `0` |

Tres parámetros de página y no uno: cada grupo avanza por su cuenta, que es lo que permite tenerlos separados en tres consultas (`research.md`, decisión 7).

**Respuesta**: `search/results.html` con tres grupos. Cada grupo trae `resultados` y `hayMas`.

- Con `q` ausente o en blanco: la pantalla del buscador vacía, sin ejecutar ninguna consulta.
- Con `q` de menos de 3 caracteres: mensaje explicando el mínimo, **sin consultar** (RF-008).
- Sin coincidencias en ningún grupo: mensaje explícito, no una pantalla en blanco (RF-007).
- Cada resultado enlaza a `/judiciales/{id}`, `/administrativos/{id}` o `/pendientes/{id}`.
- Un registro archivado o inactivo se muestra **señalado como tal** (RF-011).

**Nunca en la URL**: nada que no sea el término que el usuario escribió. El término es dato del usuario y viaja en `q` como en los tres listados; no se registra en la auditoría.

---

## `GET /actividad-diaria` — ¿qué hice hoy?

| Parámetro | Valores | Por omisión |
|---|---|---|
| `dia` | fecha ISO (`2026-09-08`) | hoy en zona de Lima |
| `ownerId` | uuid de una cuenta activa | la del que consulta |

**Respuesta**: `activity/day.html` con `cumplidos`, `manuales` y el día.

- `dia` futuro: se rechaza y se muestra hoy. No hay actividad realizada en el futuro.
- Día sin nada: mensaje explícito (RF-007 equivalente para esta pantalla).
- `ownerId` de otra persona: se muestra, y el formulario de alta **no aparece** — se registra actividad propia, no ajena.

## `POST /actividad-diaria` — registrar

| Campo | Reglas |
|---|---|
| `description` | obligatorio, hasta 10 000 caracteres |
| `performedOn` | obligatorio, fecha ISO, **no futura** (RF-016) |
| `typeId` | opcional, uuid de `pending_task_type` **habilitado** |
| `otherType` | opcional, hasta 150 caracteres |

**Regla que la base también sostiene**: `typeId` y `otherType` no pueden venir los dos. El formulario ofrece un desplegable con «(sin tipo)», los tipos del catálogo y «Otro»; al elegir «Otro» aparece el campo de texto.

Respuestas: `303` a `/actividad-diaria?dia=…` con mensaje de éxito; `422` con el formulario y los errores si la validación falla. El `POST` lleva CSRF, como todos los del sistema.

## `POST /actividad-diaria/{id}/editar` y `POST /actividad-diaria/{id}/retirar`

Mismos campos. Ambos exigen `version` para el bloqueo optimista.

**Autorización, comprobada en el servicio**: solo el autor o la jefa (RF-020). Un `POST` directo de otra persona recibe rechazo del servidor. Ocultar el botón no es autorización.

Retirar pone `active = false`; **no borra** (RF-022).

## `GET /actividad-diaria/{id}/historial`

`activity/history.html`, con la misma forma que los otros historiales del sistema: alta, modificaciones y retirada, con autor, fecha y lo que decía antes.

---

## `GET /calendario` — el calendario

| Parámetro | Valores | Por omisión |
|---|---|---|
| `vista` | `dia`, `semana`, `mes` | `mes` |
| `ancla` | fecha ISO dentro del periodo que se quiere ver | hoy en zona de Lima |
| `ownerId` | uuid de una cuenta activa, o ausente para toda el área | el que consulta |

`ancla` y no `desde`/`hasta`: el rango lo deriva el servidor de la vista, y así no hay forma de pedir un rango arbitrario de tres años que dispare el coste.

**Respuesta**: `agenda/calendar.html`.

- Vista `mes`: rejilla completa de lunes a domingo, incluidos los días de los meses vecinos que la completan, **distinguidos** de los del mes en curso.
- Cada evento indica su tipo (Programado, Vencimiento, Actuación), de qué registro viene, y para los pendientes **el nombre de su tipo de catálogo** — así una audiencia se ve como audiencia sin que el código busque esa palabra (`research.md`, decisión 6).
- Cada evento enlaza a la ficha de su registro.
- Días no laborables **confirmados**: sombreados. Año sin confirmar: el calendario **funciona igual** y avisa de que el sombreado no está disponible (RF-027).
- Día con más eventos de los que caben: se indica cuántos hay y se llega a todos.
- Periodo sin eventos: la rejilla se pinta igualmente con sus días.

Enlaces de navegación: anterior, siguiente y «hoy», conservando `vista` y `ownerId`.

---

## Cambios en pantallas existentes

**`/judiciales`, `/administrativos`, `/pendientes`**: el parámetro `q` **no cambia de nombre ni de forma**. Lo que cambia es en qué columnas busca (RF-012). Ningún enlace ni marcador existente deja de funcionar.

**`fragments/navegacion.html`**: tres enlaces nuevos —Buscar, Actividad diaria, Calendario—, visibles para toda cuenta activa.

---

## Presupuestos

Se miden con 5 personas y 5.000 pendientes (`BusquedaQueryBudgetIT`, `ActividadQueryBudgetIT`, `AgendaQueryBudgetIT`).

| Operación | Invariante — **lo que de verdad se defiende** | Medido | Techo |
|---|---|---|---|
| `GET /buscar` | **las mismas consultas con 600 coincidencias que con 1** | **4** | ≤ 6 |
| `GET /actividad-diaria` | **las mismas con 40 actividades que con 2** | **5** | ≤ 7 |
| `GET /calendario` | **las mismas en día, semana y mes** | **5** | ≤ 7 |

Tiempo medido: un mes cargado a propósito —31 días con seis pendientes cada uno, y cada uno con programación y vencimiento, unos 370 eventos— se pinta en **156 ms** (el peor de diez), contra un techo de 400 ms. Es la medición que importa en el calendario, porque es la única pantalla que no pagina y ahí el número de consultas no dice nada del volumen de filas.

**Las invariantes no se negocian**: son propiedades del diseño, y son las que encontraron los fallos. La del calendario es la que justifica la consulta por rango: si se hubiera preguntado día a día, el mes habría dado 35 consultas donde da 5.
