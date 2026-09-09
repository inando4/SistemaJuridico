# Especificación: Pendientes relacionados en la ficha del expediente

**Directorio**: `specs/008-pendientes-del-expediente/`
**Creada**: 2026-09-08
**Estado**: borrador para planificación
**Insumo del cliente**: secciones 28 («Ficha de expediente judicial») y 30 («Ficha de procedimiento administrativo»), con la regla de vínculo de la sección 9.

---

## Por qué ahora

Las dos fichas de expediente están completas salvo un bloque que el insumo pide en ambas: **«Pendientes relacionados»**. La ficha administrativa lo dice en el propio código, en un comentario que quedó de la feature 002:

> «La seccion "Pendientes relacionados" de la seccion 30 del insumo llegara con la funcionalidad de pendientes. Se deja el hueco previsto.»

La funcionalidad de pendientes llegó en la feature 003 y no volvió a por el hueco. Esto no amplía el alcance del sistema: **termina trabajo ya planificado**.

Hoy la relación es navegable en un solo sentido. Desde un pendiente se llega a su expediente; desde un expediente no se llega a sus pendientes. Quien abre un expediente para saber en qué estado está tiene que ir al listado de pendientes y filtrar a mano, y el listado no ofrece un filtro por expediente concreto: solo por *clase* de vínculo (judicial, administrativo o ninguno).

---

## Escenarios de usuario

### Historia 1 — Ver de un vistazo qué queda por hacer en un expediente (prioridad P1)

La abogada abre la ficha de un expediente judicial antes de una audiencia. Quiere saber qué pendientes tiene ese expediente: cuáles siguen abiertos, cuáles ya se cumplieron y de quién es cada uno.

**Por qué es lo primero**: es el motivo por el que el insumo pide el bloque. Sin él, la ficha describe el expediente pero no dice en qué está trabajando el equipo sobre él.

**Recorrido de aceptación**

1. **Dado** un expediente judicial con tres pendientes activos y uno ya cumplido, **cuando** abro su ficha, **entonces** veo los cuatro en un bloque «Pendientes relacionados», y de cada uno: título, responsable, estado, prioridad y fecha límite.
2. **Dado** que uno de esos pendientes es de otro abogado, **cuando** miro la lista, **entonces** ese pendiente aparece con el nombre de su responsable, no oculto.
3. **Dado** un expediente sin ningún pendiente vinculado, **cuando** abro su ficha, **entonces** el bloque aparece con un mensaje de lista vacía y no desaparece de la página.
4. **Dado** un pendiente de la lista, **cuando** hago clic en su título, **entonces** llego a su ficha.
5. Los recorridos 1 a 4 valen igual para un procedimiento administrativo.

---

### Historia 2 — Crear un pendiente ya vinculado al expediente que estoy mirando (prioridad P2)

Estando en la ficha, la abogada decide que hay que redactar un informe para ese expediente. Quiere registrarlo sin volver a buscar el expediente en un desplegable.

**Por qué es lo segundo**: el insumo lo pide con nombre propio («+ Crear nuevo pendiente relacionado») y es donde se ahorra el trabajo manual, pero depende de que el bloque de la historia 1 exista.

**Recorrido de aceptación**

1. **Dado** que estoy en la ficha de un expediente judicial, **cuando** uso «+ Crear nuevo pendiente relacionado», **entonces** llego al formulario de alta con ese expediente ya elegido como vínculo.
2. **Dado** el formulario así abierto, **cuando** lo guardo, **entonces** el pendiente queda vinculado a ese expediente y aparece en el bloque de la ficha al volver.
3. **Dado** que estoy en la ficha de un expediente **de otro abogado**, **cuando** abro el formulario desde ahí, **entonces** el formulario me advierte que el pendiente quedará a mi nombre aunque el expediente sea de otra persona.
4. **Dado** un enlace manipulado que apunta a un expediente inexistente, **cuando** se abre el formulario, **entonces** el sistema no lo acepta y no crea un pendiente con un vínculo roto.
5. El recorrido vale igual para un procedimiento administrativo.

---

### Historia 3 — Pasar de la ficha al listado completo con el expediente ya filtrado (prioridad P3)

Un expediente con muchos pendientes no cabe entero en la ficha. La abogada quiere ver todos, ordenarlos y filtrarlos con las herramientas que ya tiene el listado.

**Por qué es lo tercero**: es lo que hace que acotar la lista de la ficha no esconda información. Sin esta historia, la historia 1 tendría que mostrarlo todo.

**Recorrido de aceptación**

1. **Dado** un expediente con más pendientes de los que muestra la ficha, **cuando** abro la ficha, **entonces** el bloque avisa de que hay más y ofrece «Ver todos».
2. **Dado** ese enlace, **cuando** lo sigo, **entonces** llego al listado de pendientes mostrando únicamente los de ese expediente, y el filtro aplicado se ve en pantalla.
3. **Dado** el listado así filtrado, **cuando** cambio de página, ordeno o añado otro filtro, **entonces** el expediente sigue filtrado.
4. **Dado** un expediente con pocos pendientes, **cuando** abro la ficha, **entonces** no aparece el aviso de «hay más» (pero el enlace al listado filtrado sí sigue disponible).

---

## Requisitos funcionales

### El bloque en la ficha

- **RF-001**: La ficha de expediente judicial y la ficha de procedimiento administrativo muestran, ambas, un bloque «Pendientes relacionados» con los pendientes vinculados a ese expediente.
- **RF-002**: El bloque muestra tanto pendientes por hacer como ya cumplidos, porque forman parte de la historia del expediente. Los que están por hacer aparecen primero.
- **RF-003**: Cada fila muestra al menos: título, responsable, estado, prioridad y fecha límite; y enlaza a la ficha del pendiente.
- **RF-004**: El bloque muestra los pendientes de **cualquier** responsable, no solo los de quien mira. El responsable es visible en cada fila.
- **RF-005**: El bloque aparece también cuando no hay ningún pendiente, con un mensaje explícito de lista vacía.
- **RF-006**: El bloque muestra como máximo una página de resultados. Cuando hay más, lo dice y ofrece llegar al resto.
- **RF-007**: Un pendiente archivado (retirado) no aparece en el bloque de la ficha; se llega a él desde el listado filtrado cambiando la visibilidad, igual que en el resto del sistema.

### El filtro por expediente en el listado

- **RF-008**: El listado de pendientes acepta filtrar por un expediente judicial concreto o por un procedimiento administrativo concreto.
- **RF-009**: Ese filtro convive con los que ya existen (texto, responsable, tipo, prioridad, estado, plazo, visibilidad, foco) y se conserva al paginar y al ordenar.
- **RF-010**: Cuando el filtro está aplicado, la pantalla indica de qué expediente se están viendo los pendientes y ofrece quitarlo.
- **RF-011**: Un identificador de expediente que no existe produce una lista vacía y un aviso, no un error de sistema.

### El alta vinculada

- **RF-012**: Desde ambas fichas hay una acción «+ Crear nuevo pendiente relacionado» que abre el formulario de alta con ese expediente ya seleccionado como vínculo.
- **RF-013**: El vínculo pre-seleccionado se puede cambiar o quitar en el formulario antes de guardar; no queda fijado.
- **RF-014**: Se respeta la regla de la sección 9 del insumo: un pendiente se vincula como máximo a un expediente judicial **o** a un procedimiento administrativo, nunca a ambos. Pre-seleccionar uno no permite saltarse esa regla.
- **RF-015**: El pendiente creado así queda a nombre de quien lo registra, igual que cualquier otro. Cuando el expediente es de otra persona, el formulario lo advierte **antes** de guardar.
- **RF-016**: Un identificador de expediente inválido o inexistente en el enlace de alta se rechaza; no se crea un pendiente con un vínculo que no lleva a ninguna parte.

### Trazabilidad y permisos

- **RF-017**: Nada de esta feature cambia quién puede crear, editar o cumplir un pendiente. Ver el bloque no otorga permiso sobre lo que contiene.
- **RF-018**: El alta desde la ficha se registra en el historial exactamente igual que el alta desde el listado. No aparece una vía de registro sin rastro.

---

## Criterios de éxito

- **CE-001**: Desde la ficha de cualquier expediente con pendientes, quien la abre ve cuántos y cuáles hay sin salir de la página.
- **CE-002**: Registrar un pendiente para el expediente que se está mirando no obliga a buscar ese expediente en ningún desplegable.
- **CE-003**: El conjunto de pendientes que muestra la ficha coincide con el que muestra el listado filtrado por ese mismo expediente, comparando el mismo estado de visibilidad.
- **CE-004**: La ficha cuesta **el mismo número de consultas** con dos pendientes vinculados que con cincuenta. Es la comprobación que delata una consulta por fila.
- **CE-005**: La ficha añade **una sola consulta** respecto de lo que costaba antes de esta feature.
- **CE-006**: Una ficha con cincuenta pendientes vinculados se muestra en menos de 500 ms en condiciones de prueba.
- **CE-007**: Ningún pendiente vinculado a un expediente queda invisible desde ese expediente: el que no cabe en la ficha se alcanza desde el enlace al listado filtrado.

---

## Entidades

No se crea ninguna entidad ni ninguna columna. La feature **lee** relaciones que ya existen:

- **Pendiente** — ya tiene, desde la migración V9, un vínculo opcional a expediente judicial y otro a procedimiento administrativo, mutuamente excluyentes.
- **Expediente judicial** y **Procedimiento administrativo** — sin cambios.

---

## Supuestos

1. **No hay migración de base de datos.** Las columnas del vínculo existen desde la V9 y las consultas actuales ya las traen.
2. **La lista de la ficha se acota como todas las demás del sistema**, con el mismo tamaño de página, en vez de inventar un límite propio.
3. **Los cumplidos se muestran en la ficha.** El listado general los separa en su propia pantalla porque ahí estorban al trabajo del día; en la ficha son historia del expediente y quitarlos dejaría la ficha contando media verdad.
4. **La propiedad del pendiente no cambia.** Un pendiente creado desde la ficha de otra persona queda a nombre de quien lo registra. Cambiar eso sería una decisión sobre reparto de trabajo, no sobre esta pantalla, y tiene sus propias herramientas en la feature 005.
5. **El bloque respeta la lectura compartida** que ya rige en el sistema: todo el equipo ve el trabajo de todo el equipo (principio II de la constitución).

---

## Consecuencias aceptadas

**El enlace de alta hace más fácil crear pendientes bajo el nombre propio en expedientes ajenos.** El sistema ya lo permitía desde el listado: el formulario dice «El responsable será usted» y así se guarda. Esta feature no cambia la regla, pero sí pone la puerta más a mano, y por tanto es previsible que ocurra más a menudo.

Se acepta a cambio de que la advertencia del formulario diga de quién es el expediente al que se está vinculando (RF-015), que es información que hoy no da. La jefa dispone de la reasignación individual de la feature 005 para corregirlo después. Cambiar la regla de propiedad dentro de esta feature sería un efecto colateral en una decisión que no le corresponde.

---

## Fuera de alcance

- Vincular un pendiente a más de un expediente (el insumo lo aplaza explícitamente en la sección 9).
- Crear el pendiente sin salir de la ficha, en una ventana emergente. Se navega al formulario que ya existe.
- Cambiar la propiedad del pendiente creado desde una ficha ajena.
- Añadir a la ficha nada que el insumo no pida en las secciones 28 y 30.
- Exportación (sección 38) y estadísticas (sección 39): aplazadas por el cliente.
