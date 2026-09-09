# Especificación: Cancelar registros y los filtros que faltan en pantalla

**Directorio**: `specs/009-cancelar-y-filtros/`
**Creada**: 2026-09-09
**Estado**: borrador para planificación
**Insumo del cliente**: secciones 25 («Pantalla de pendientes») y 27 («Procesos judiciales»).

---

## Por qué ahora

Dos huecos de la misma forma, y la misma que tenía la funcionalidad 008: **el servidor ya
sabe hacerlo y la pantalla no lo ofrece.**

**Uno.** La sección 25 pide, entre las acciones rápidas de cada pendiente, «Cancelar».
No existe. Y como la constitución prohíbe borrar registros, hoy **un pendiente creado por
error no se puede quitar de ninguna manera**: se queda en la lista de trabajo para
siempre. Los expedientes están a medias: la acción existe entera en el servidor —con su
control de permisos, su bloqueo optimista y su registro en el historial— pero ninguna
pantalla la ofrece.

**Dos.** La sección 27 enumera ocho filtros para el listado de expedientes judiciales.
El servidor acepta los ocho; la pantalla ofrece tres. Los otros cinco solo se alcanzan
escribiendo la dirección a mano, o desde los enlaces del panel y de la vista de equipo.
Lo mismo pasa, en distinta medida, en las otras dos listas.

---

## Escenarios de usuario

### Historia 1 — Quitar de en medio un registro que sobra (prioridad P1)

Una abogada registró un pendiente por equivocación, o duplicado. Quiere que deje de
aparecer en su lista de trabajo sin perder constancia de que existió.

**Por qué es lo primero**: es lo único de esta funcionalidad que hoy **no tiene ninguna
salida**. Un filtro que falta se rodea escribiendo la dirección; un registro que sobra
se queda ahí.

**Recorrido de aceptación**

1. **Dado** un pendiente mío, **cuando** uso «Cancelar», **entonces** deja de aparecer
   en el listado de trabajo y en el bloque de su expediente.
2. **Dado** ese pendiente cancelado, **cuando** miro el listado con los registros
   ocultos, **entonces** lo encuentro y puedo **devolverlo** a la lista.
3. **Dado** un pendiente cancelado, **cuando** abro su historial, **entonces** consta
   quién lo canceló y cuándo.
4. **Dado** un pendiente **de otra persona**, **cuando** intento cancelarlo sin ser la
   jefa, **entonces** el sistema lo rechaza.
5. **Dado** un expediente judicial o un procedimiento administrativo, **cuando** uso su
   acción de ocultar, **entonces** ocurre lo mismo: sale del listado corriente, se puede
   recuperar y queda constancia.
6. **Dado** que dos personas actúan sobre el mismo registro a la vez, **cuando** la
   segunda guarda, **entonces** se le avisa del conflicto en lugar de pisar el cambio.

---

### Historia 2 — Actuar sobre un pendiente sin abrirlo (prioridad P2)

Una abogada repasa su lista al final del día y va marcando lo que ya hizo. Hoy tiene que
abrir cada pendiente, marcarlo y volver al listado, que ha perdido su sitio.

**Por qué es lo segundo**: la sección 25 lo pide con nombre propio —«acciones rápidas»—
y es el gesto más repetido del día, pero se puede hacer hoy navegando; la historia 1
resuelve algo que no se puede hacer de ninguna forma.

**Recorrido de aceptación**

1. **Dado** el listado de pendientes, **cuando** miro una fila, **entonces** puedo abrir
   el pendiente, editarlo, marcarlo como cumplido y cancelarlo sin salir de la lista.
2. **Dado** que marco uno como cumplido desde la fila, **cuando** vuelve la pantalla,
   **entonces** sigo en la misma página y con los mismos filtros que tenía.
3. **Dado** un pendiente **de otra persona**, **cuando** miro su fila sin ser la jefa,
   **entonces** no se me ofrecen las acciones que no puedo ejecutar.
4. **Dado** un pendiente ya cumplido, **cuando** miro su fila, **entonces** no se me
   ofrece cumplirlo otra vez.
5. **Dado** que «No cumplido» y «Reprogramar» necesitan una fecha o un motivo, **cuando**
   los busco, **entonces** los encuentro en la ficha del pendiente, donde hay sitio para
   pedirlos.

---

### Historia 3 — Componer un filtro sin escribir la dirección a mano (prioridad P3)

La jefa quiere ver los expedientes vencidos de una abogada concreta. Hoy tiene que
conocer la sintaxis de la dirección o llegar rebotando desde el panel.

**Por qué es lo segundo**: mejora algo que ya se puede hacer, mientras que la historia 1
resuelve algo que no se puede hacer en absoluto.

**Recorrido de aceptación**

1. **Dado** el listado de expedientes judiciales, **cuando** miro sus filtros,
   **entonces** puedo elegir responsable, estado procesal, materia y sólo los vencidos,
   además de los que ya había.
2. **Dado** que aplico dos filtros a la vez, **cuando** se muestra el resultado,
   **entonces** se cumplen los dos.
3. **Dado** un filtro aplicado, **cuando** paso de página u ordeno, **entonces** el
   filtro se conserva.
4. **Dado** un filtro aplicado, **cuando** uso «Quitar filtros», **entonces** vuelvo al
   listado completo.
5. Lo mismo, con sus propios campos, en el listado de procedimientos administrativos y
   en el de pendientes.

---

## Requisitos funcionales

### Cancelar y recuperar

- **RF-001**: Cada pendiente ofrece una acción para **cancelarlo**, que lo retira del
  listado de trabajo sin borrarlo.
- **RF-002**: Un pendiente cancelado **se puede devolver** a la lista. La cancelación es
  una corrección, no una condena.
- **RF-003**: Cancelar y devolver quedan registrados en el historial del pendiente, con
  quién y cuándo, igual que cumplir o reprogramar.
- **RF-004**: Sólo pueden cancelar quien es responsable del pendiente y la jefa, que es
  la misma regla que ya rige para actuar sobre él.
- **RF-005**: **No se pide motivo.** Cancelar es reversible y deja rastro; exigir una
  justificación para algo que se deshace en un clic sólo añade fricción.
- **RF-006**: Un pendiente cancelado no aparece en el listado de trabajo, ni en el bloque
  de pendientes de su expediente, ni en las alertas, ni en el calendario, ni en la
  pantalla de hoy. Sí aparece al pedir expresamente los registros ocultos.
- **RF-007**: La ficha del expediente judicial y la del procedimiento administrativo
  ofrecen su acción de **ocultar y volver a mostrar**, que el servidor ya sabe ejecutar.
- **RF-008**: Cancelar un pendiente **no** toca su expediente, y ocultar un expediente
  **no** cancela sus pendientes. Son decisiones separadas.
- **RF-009**: Si dos personas actúan sobre el mismo registro a la vez, la segunda recibe
  un aviso de conflicto y su cambio no se aplica en silencio.

### Las acciones rápidas del listado

- **RF-010**: Cada fila del listado de pendientes ofrece: abrir, editar, marcar como
  cumplido y cancelar.
- **RF-011**: «No cumplido» y «Reprogramar» se quedan en la ficha. Piden una fecha o un
  motivo, y un formulario desplegado dentro de una tabla de veinticinco filas estorba
  más de lo que ahorra.
- **RF-012**: Una acción ejecutada desde la fila **devuelve al listado tal como estaba**:
  misma página, mismos filtros, mismo orden.
- **RF-013**: A cada persona sólo se le ofrecen en la fila las acciones que puede
  ejecutar. Ofrecer un botón que va a ser rechazado es peor que no ofrecerlo.
- **RF-014**: Una acción que ya no procede —cumplir algo cumplido, cancelar algo
  cancelado— no se ofrece.
- **RF-015**: Las acciones de la fila y las de la ficha hacen exactamente lo mismo: las
  mismas comprobaciones, el mismo registro en el historial y el mismo aviso de conflicto.

### Los filtros que faltan

- **RF-016**: El listado de expedientes judiciales ofrece en pantalla los filtros que
  enumera la sección 27: responsable, estado procesal, materia, con fecha límite, sin
  fecha límite y vencidos.
- **RF-017**: El listado de procedimientos administrativos ofrece responsable, estado y
  vencidos, además de los que ya tiene.
- **RF-018**: El listado de pendientes ofrece responsable, tipo, prioridad, estado y
  vencidos, además de los que ya tiene.
- **RF-019**: Los filtros se combinan entre sí y se conservan al paginar y al ordenar.
- **RF-020**: Cada listado ofrece quitar todos los filtros de una vez.
- **RF-021**: Un valor de filtro que ya no está disponible —un catálogo deshabilitado,
  una cuenta desactivada— no rompe la pantalla ni desaparece de los registros que ya lo
  tenían.

### Lo que no cambia

- **RF-022**: Ninguna acción de esta funcionalidad borra nada. Todo se retira y se
  recupera.
- **RF-023**: Los filtros no cambian quién ve qué. La lectura sigue siendo compartida:
  filtrar por otra persona muestra su trabajo, como hasta ahora.

---

## Criterios de éxito

- **CE-001**: Un registro creado por error deja de estorbar en menos de tres clics, y se
  puede recuperar en otros tres.
- **CE-002**: Ningún registro cancelado u oculto desaparece del sistema: todos se
  alcanzan pidiendo los registros ocultos.
- **CE-003**: Toda cancelación y toda recuperación tiene una entrada en el historial. No
  hay forma de retirar algo sin dejar rastro.
- **CE-004**: Marcar un pendiente como cumplido desde el listado no obliga a recomponer
  los filtros ni a buscar otra vez dónde se estaba.
- **CE-005**: Los ocho filtros que enumera la sección 27 se pueden aplicar sin escribir
  una dirección a mano.
- **CE-006**: Añadir los desplegables de filtro **no multiplica el coste** de abrir cada
  listado: los catálogos que alimentan los filtros se leen una vez por pantalla, no una
  por opción.
- **CE-007**: Un listado con sus filtros nuevos se muestra en el mismo tiempo que antes,
  dentro del margen de medición.

---

## Entidades

No se crea ninguna entidad ni ninguna columna, y **no hace falta migración**:

- **Pendiente** — ya tiene desde su origen la marca de visibilidad que esta funcionalidad
  aprende a cambiar.
- **Expediente judicial** y **Procedimiento administrativo** — ídem, y su acción ya está
  construida en el servidor.
- **Historial** — la acción nueva encaja en el registro que ya existe, sin ampliarlo.

---

## Supuestos

1. **«Cancelar» de la sección 25 es la marca de visibilidad que ya existe**, no un estado
   nuevo del pendiente. Los estados describen en qué punto va el trabajo; la visibilidad
   dice si estorba en la lista. Son ejes distintos, como ya lo son en los expedientes.
2. **La cancelación es recuperable y no pide motivo.** El insumo dice «Cancelar» y ahí se
   detiene; se resuelve como la retirada de la actividad diaria, que es el precedente más
   cercano del propio sistema.
3. **Ocultar un expediente no es una petición del insumo**, sino una capacidad que el
   sistema ya tiene a medio construir. Se termina porque dejar una acción sin salida en
   pantalla es peor que no tenerla.
4. **«Acciones rápidas» se interpreta como «en la fila del listado»**, decidido el
   2026-09-09. La sección 25 titula así la lista dentro de la pantalla de pendientes, y
   «rápida» pierde el sentido si obliga a navegar. Se llevan a la fila las cuatro que se
   resuelven de un clic o con un enlace; las dos que piden datos se quedan donde hay
   sitio para pedirlos.
5. **Los filtros ya funcionan en el servidor.** Esta funcionalidad les pone controles, no
   los inventa; por eso el comportamiento al combinarlos y al paginar ya está probado.

---

## Fuera de alcance

- Borrar registros de verdad. La constitución lo prohíbe y no se discute aquí.
- Cancelar varios pendientes de una vez.
- Un motivo obligatorio o un flujo de aprobación para cancelar.
- Guardar filtros como preferencia del usuario.
- Llevar «No cumplido» y «Reprogramar» a la fila del listado.
- Exportación (sección 38) y estadísticas (sección 39): aplazadas por el cliente.
