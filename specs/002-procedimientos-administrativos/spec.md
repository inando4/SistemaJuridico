# Especificación de funcionalidad: Control de procedimientos administrativos

**Rama Git actual**: `002-procedimientos-administrativos`, coincidente con la funcionalidad resuelta por `.specify/feature.json`.

**Creada**: 2026-09-06

**Última revisión del insumo**: 2026-09-06

**Estado**: Borrador validado para planificación

**Entrada**: Segunda funcionalidad: registrar, editar, listar y consultar los procedimientos
administrativos que tramita el área, con su propio catálogo de estados, permisos, historial
y plazos hábiles. Fuente: [insumo del cliente](../../docs/insumo-cliente.md), secciones 7,
7.1, 29, 30 y 37. Rige la [constitución 4.0.1](../../.specify/memory/constitution.md).
Se construye sobre la funcionalidad 001, ya desplegada.

## Contexto

El área tramita dos clases de expediente. La funcionalidad 001 cubrió los **procesos
judiciales**; esta cubre los **procedimientos administrativos**, que hoy viven en otra hoja
del mismo Excel con unos 197 registros (§37).

No son lo mismo ni se parecen tanto como sugiere el nombre. Un procedimiento administrativo
nace de un **área de la institución que pide algo** —un informe, una opinión legal— y lo que
importa es quién lo pidió, qué pidió, cuándo llegó y para cuándo hay que responder. No hay
demandante ni demandado, no hay materia procesal, no hay monto.

**Lo que esta funcionalidad NO vuelve a construir.** El acceso identificado, la sesión de
jornada, el cálculo de plazos en días hábiles con su calendario compartido, la auditoría
inmutable, la matriz de permisos y la separación de roles en base de datos ya existen y se
reutilizan tal cual. Un calendario o un historial paralelos serían dos verdades sobre lo
mismo, que es peor que ninguna.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar y consultar procedimientos administrativos (Priority: P1)

Como integrante del área, registro un procedimiento administrativo con los datos que hoy
llevo en el Excel y lo localizo después sin recorrer la lista entera.

**Por qué esta prioridad**: sin registro y consulta no hay funcionalidad. Es la que sustituye
la hoja de cálculo y la única que aporta valor por sí sola.

**Prueba independiente**: con una cuenta activa, registrar un procedimiento indicando solo su
número de expediente, encontrarlo por búsqueda y abrir su ficha.

**Escenarios de aceptación**:

1. **Dado** un usuario con sesión iniciada, **cuando** registra un procedimiento indicando
   únicamente el número de expediente, **entonces** el registro se guarda y queda visible en
   el listado.
2. **Dado** un procedimiento con todos sus campos, **cuando** se consulta su ficha,
   **entonces** se muestran área solicitante, pedido, fechas de recepción y límite,
   responsable, estado y observaciones, y los campos sin dato se muestran como ausentes en
   lugar de omitirse.
3. **Dado** un número de expediente ya usado por otro procedimiento, **cuando** se intenta
   registrar otro igual, **entonces** el sistema lo rechaza indicando el conflicto, aunque el
   existente sea de otra persona o esté oculto.
4. **Dado** un formulario con una fecha imposible o un texto que excede lo permitido,
   **cuando** se intenta guardar, **entonces** no se crea ningún registro parcial y el
   formulario vuelve con lo escrito y los errores señalados.
5. **Dado** un listado con más de veinticinco procedimientos, **cuando** se pagina,
   **entonces** los filtros aplicados se conservan.
6. **Dado** un filtro sin coincidencias, **cuando** se muestra el resultado vacío,
   **entonces** se ofrece una salida para retirar los filtros o registrar uno nuevo.

---

### User Story 2 - Interpretar el plazo de respuesta (Priority: P1)

Como integrante del área, veo de un vistazo cuántos días hábiles quedan para responder un
procedimiento, con la misma regla y el mismo calendario que los expedientes judiciales.

**Por qué esta prioridad**: el insumo (§7) pide explícitamente que los días restantes se
calculen dinámicamente. Un procedimiento administrativo vencido tiene consecuencias para el
área que lo pidió.

**Prueba independiente**: con calendario cargado y revisado, registrar procedimientos con
fecha límite pasada, de hoy y futura, y comprobar los tres estados.

**Escenarios de aceptación**:

1. **Dado** un procedimiento con fecha límite futura y calendario revisado, **cuando** se
   consulta, **entonces** se muestran los días hábiles restantes excluyendo hoy e incluyendo
   la fecha límite si es hábil.
2. **Dado** un procedimiento cuya fecha límite ya pasó, **cuando** se consulta, **entonces**
   se indica que está vencido mediante texto, no solo por color.
3. **Dado** un año sin cobertura de calendario confirmada, **cuando** se consulta un
   procedimiento cuyo plazo lo atraviesa, **entonces** se avisa de que el cálculo no está
   disponible y se nombran los años pendientes, en lugar de mostrar un número.
4. **Dado** el mismo procedimiento, **cuando** se compara el plazo mostrado en el listado con
   el de la ficha, **entonces** ambos coinciden.
5. **Dado** un procedimiento sin fecha límite, **cuando** se consulta, **entonces** se indica
   que no tiene plazo, sin tratarlo como vencido.

---

### User Story 3 - Editar con permisos y consultar el historial (Priority: P1)

Como responsable de un procedimiento lo mantengo al día; como jefatura puedo intervenir sobre
cualquiera, y todo cambio queda atribuido a quien lo hizo.

**Por qué esta prioridad**: un registro que no se puede corregir se abandona. Y sin historial
no se puede reconstruir quién cambió una fecha límite.

**Prueba independiente**: con dos cuentas de ABOGADO y una de JEFA, comprobar la matriz de
permisos mediante peticiones directas y revisar el historial resultante.

**Escenarios de aceptación**:

1. **Dado** un ABOGADO y un procedimiento propio, **cuando** lo edita, **entonces** el cambio
   se guarda y queda registrado con su nombre.
2. **Dado** un ABOGADO y un procedimiento de otra persona, **cuando** intenta modificarlo
   —incluso componiendo la petición a mano—, **entonces** el sistema lo rechaza y el registro
   queda intacto.
3. **Dado** JEFA y un procedimiento ajeno, **cuando** lo modifica, **entonces** el cambio se
   aplica y el historial distingue quién lo hizo de quién era responsable en ese momento.
4. **Dado** un formulario abierto y un cambio guardado por otra persona entretanto,
   **cuando** se intenta guardar, **entonces** el sistema informa del conflicto y no
   sobrescribe.
5. **Dado** un guardado que no modifica ningún valor, **cuando** se confirma, **entonces** no
   se registra ninguna entrada de historial.
6. **Dado** cualquier consulta de listado, ficha o historial, **cuando** se realiza,
   **entonces** no se genera ninguna entrada de historial.

---

### User Story 4 - Administrar el catálogo de estados administrativos (Priority: P2)

Como JEFA, mantengo la lista de estados que el área usa para los procedimientos
administrativos, sin depender de quien programó el sistema.

**Por qué esta prioridad**: los procedimientos se pueden registrar y consultar sin estado
asignado, así que no bloquea; pero el insumo (§29) pide el estado como columna y como filtro.

**Prueba independiente**: con el catálogo vacío, crear los estados, asignarlos, deshabilitar
uno e intentar borrar uno en uso.

**Escenarios de aceptación**:

1. **Dado** el catálogo vacío, **cuando** JEFA crea «Pendiente de atención» o «Atendido»,
   **entonces** quedan disponibles para asignar.
2. **Dado** un nombre ya existente, **cuando** se intenta crear otro igual con distinta caja o
   espacios, **entonces** se rechaza.
3. **Dado** un estado en uso por algún procedimiento, **cuando** se intenta borrar,
   **entonces** el sistema lo impide y ofrece deshabilitarlo.
4. **Dado** un estado deshabilitado, **cuando** se registra un procedimiento nuevo,
   **entonces** no se ofrece; y los procedimientos que ya lo tenían lo conservan.
5. **Dado** un ABOGADO, **cuando** intenta modificar el catálogo, **entonces** el sistema lo
   rechaza; consultarlo sí está permitido.
6. **Dado** un estado que aparece en el historial de algún procedimiento, **cuando** se
   intenta borrar, **entonces** el sistema lo impide para no dejar el historial sin
   explicación.

---

### Edge Cases

- Un procedimiento cuya fecha límite es anterior a su fecha de recepción: el sistema lo acepta
  y lo muestra, pero advierte de la incoherencia; no la corrige por su cuenta.
- Un procedimiento con fecha de recepción pero sin fecha límite: válido; no todos los pedidos
  tienen plazo.
- El responsable de un procedimiento queda desactivado: el registro sigue legible y editable
  por JEFA, y conserva su responsable.
- Dos personas registran el mismo número de expediente a la vez: solo prospera uno.
- Un número de expediente administrativo que coincide con uno judicial: se permite. Son
  registros de naturaleza distinta y sus numeraciones son independientes.
- Un texto pegado que excede el máximo permitido: se rechaza con mensaje; nunca se recorta en
  silencio.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir registrar un procedimiento administrativo indicando
  como mínimo su número de expediente; los demás campos son opcionales.
- **FR-002**: El sistema DEBE registrar de cada procedimiento: número correlativo, abogado
  responsable, número de expediente, área solicitante, pedido, estado, fecha de recepción,
  fecha límite, observaciones y si está visible en el listado corriente.
- **FR-003**: El número de expediente DEBE ser único entre procedimientos administrativos,
  comparando sin distinguir mayúsculas ni espacios de borde, e incluyendo los ocultos y los de
  otras personas. La unicidad NO se comparte con los expedientes judiciales.
- **FR-004**: El sistema DEBE conservar el número de expediente tal como se escribió,
  incluidos ceros iniciales, guiones y mayúsculas.
- **FR-005**: El responsable de un procedimiento DEBE fijarse al usuario que lo registra. La
  reasignación entre integrantes queda fuera de esta funcionalidad.
- **FR-006**: El sistema DEBE validar fechas y longitudes de texto devolviendo todos los
  errores a la vez, sin crear registros parciales y sin perder lo escrito.
- **FR-007**: El sistema DEBE advertir cuando la fecha límite sea anterior a la fecha de
  recepción, sin impedir el guardado ni modificar las fechas.
- **FR-008**: El listado DEBE mostrar las columnas que hoy tiene el Excel: número, abogado
  responsable, número de expediente, área solicitante, pedido, estado, fecha de recepción,
  fecha límite, días hábiles restantes y observaciones.
- **FR-009**: El listado DEBE permitir filtrar por responsable, estado, área solicitante,
  presencia de fecha límite, vencidos y visibilidad, y ordenar por número de expediente,
  responsable o fecha límite. Los filtros DEBEN conservarse al paginar y al volver de la ficha.
- **FR-010**: Todos los integrantes DEBEN poder consultar todos los procedimientos, sean de
  quien sean.
- **FR-011**: ABOGADO DEBE editar únicamente los procedimientos de los que es responsable;
  JEFA DEBE poder editar cualquiera, quedando registrado como intervención.
- **FR-012**: El sistema DEBE detectar que otra persona modificó el registro mientras el
  formulario estaba abierto y rechazar el guardado sin sobrescribir.
- **FR-013**: Toda modificación efectiva DEBE registrarse en el historial junto con el cambio,
  indicando quién la hizo y quién era el responsable en ese momento. Un guardado sin cambios y
  cualquier consulta NO generan historial.
- **FR-014**: La visibilidad en el listado DEBE ser independiente del estado. Que un
  procedimiento esté «Archivado» NO lo oculta automáticamente.
- **FR-015**: El sistema DEBE mostrar los días hábiles restantes hasta la fecha límite,
  excluyendo el día en curso e incluyendo la fecha límite cuando sea hábil, usando el mismo
  calendario de días no laborables que los procesos judiciales.
- **FR-016**: Cuando falte cobertura de calendario confirmada para algún año que el plazo
  atraviese, el sistema DEBE avisarlo y nombrar los años pendientes en lugar de mostrar un
  conteo. Los estados «vencido» y «vence hoy» DEBEN seguir mostrándose, por deducirse de la
  comparación de fechas.
- **FR-017**: El estado del plazo DEBE distinguirse por texto y no únicamente por color.
- **FR-018**: Los días restantes y el estado de vencimiento NO DEBEN persistirse en ningún
  momento.
- **FR-019**: El catálogo de estados administrativos DEBE ser administrable por JEFA, iniciar
  vacío, y ser independiente del catálogo de estados procesales judiciales y del de pendientes.
- **FR-020**: Un estado DEBE poder deshabilitarse para dejar de ofrecerse sin alterar los
  procedimientos que ya lo usan, y solo DEBE poder borrarse si nunca fue usado ni aparece en
  ningún historial.
- **FR-021**: El sistema DEBE reutilizar el acceso identificado, la sesión de jornada, la
  auditoría inmutable y la separación de roles de base de datos ya existentes, sin duplicarlos.
- **FR-022**: La ficha DEBE mostrar información del expediente, estado actual, plazos e
  historial. La sección de pendientes relacionados queda fuera de esta funcionalidad.
- **FR-023**: El sistema NO DEBE almacenar documentos, adjuntos ni archivos de ningún tipo.
- **FR-024**: Toda la interfaz y todos los mensajes DEBEN estar en español.

### Key Entities *(include if feature involves data)*

- **Procedimiento administrativo**: solicitud que un área de la institución dirige al área
  jurídica. Se identifica por su número de expediente, tiene un responsable, un área
  solicitante, un pedido, fechas de recepción y límite, un estado y observaciones.
- **Estado administrativo**: situación en que se encuentra un procedimiento. Catálogo propio,
  administrable, distinto de los estados procesales judiciales y de los de pendientes.
- **Entrada de historial**: registro inmutable de una modificación efectiva, con autor,
  responsable del momento, instante y valores anterior y nuevo. Se comparte con el resto del
  sistema.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Con 5.000 procedimientos registrados y cinco usuarios simultáneos, el listado,
  la ficha y el formulario se abren en un segundo o menos en el 95% de las veces, y un
  guardado se confirma en dos segundos o menos, medido sobre el equipo y la red de referencia.
- **SC-002**: Cinco usuarios de prueba registran un procedimiento, lo localizan por búsqueda y
  abren su ficha sin instrucciones escritas.
- **SC-003**: En una prueba con las tres cuentas, ningún ABOGADO consigue modificar un
  procedimiento ajeno, tampoco componiendo la petición directamente.
- **SC-004**: El 100% de las modificaciones efectivas aparece en el historial con autor y
  responsable; el 100% de las consultas y de los guardados sin cambios no genera ninguna
  entrada.
- **SC-005**: Para un conjunto de doce casos de plazo verificados a mano, el conteo de días
  hábiles coincide en el 100% de los casos.
- **SC-006**: Con el calendario sin revisar, el 100% de los procedimientos con plazo futuro
  muestran el aviso en lugar de un número, y ninguno muestra un conteo inventado.
- **SC-007**: El listado y la ficha muestran el mismo estado de plazo para el mismo
  procedimiento en el 100% de los casos comprobados.
- **SC-008**: Ningún intento de registrar un número de expediente duplicado prospera, ni
  siquiera lanzando ocho altas simultáneas.
- **SC-009**: Ningún dato inválido deja un registro parcial, y el formulario devuelve lo
  escrito en el 100% de los rechazos.
- **SC-010**: Ningún estado administrativo usado alguna vez puede borrarse; deshabilitarlo no
  altera ningún procedimiento existente.

## Assumptions

- El área solicitante se escribe como texto libre. El insumo no define un catálogo de áreas de
  la institución, y crearlo sin que el cliente lo confirme añadiría una lista que mantener sin
  saber si se corresponde con su organigrama.
- El pedido es texto libre de longitud amplia, equivalente al de observaciones: el insumo lo
  tipa como TEXT.
- El número correlativo es opcional e informativo, como en los expedientes judiciales; el
  identificador real es el número de expediente.
- Los valores iniciales del catálogo —Pendiente de atención, Pendiente de documentación,
  Atendido, Observado, Archivado— los confirmó el cliente, pero el catálogo arranca vacío y los
  carga JEFA desde la aplicación, para que el sistema no dependa de una lista quemada en código.
- Las numeraciones judicial y administrativa son independientes: un mismo número puede existir
  en ambos registros sin conflicto, porque identifican expedientes de naturaleza distinta.
- La fecha de recepción no se rellena automáticamente con la fecha del día: un procedimiento
  puede registrarse días después de haber llegado.
- Se mantiene la exclusión de documentos del §3.5 y la ausencia de correo saliente de la 001.
