# Especificación de funcionalidad: Control de pendientes

**Rama Git actual**: `003-control-pendientes`, coincidente con la funcionalidad resuelta por `.specify/feature.json`.

**Creada**: 2026-09-06

**Estado**: Borrador validado para planificación

**Entrada**: Tercera funcionalidad: registrar, programar, cumplir, revertir y reprogramar los
pendientes del área, con sus tres catálogos propios y su historial. Fuente:
[insumo del cliente](../../docs/insumo-cliente.md), secciones 8 a 13, 19 a 22, 25, 26, 32 y 44.
Rige la [constitución 4.0.1](../../.specify/memory/constitution.md). Se construye sobre las
funcionalidades 001 y 002, ambas desplegadas.

## Contexto

El insumo llama a esta «la tabla más importante del sistema» (§8), y es exacto: los
expedientes de las funcionalidades anteriores son el índice, y esto es el trabajo. La §44
describe el flujo diario que el área espera —abrir, ver qué es urgente, registrar lo que
llegó, ir marcando, reprogramar lo que no salió— y todo él pasa por aquí.

**Lo que hace distinta a esta funcionalidad**: las dos anteriores registran y consultan. Esta
tiene **acciones con reglas y con vuelta atrás**. Marcar cumplido, deshacerlo, declarar no
cumplido y que el sistema calcule solo el siguiente día hábil. Ahí es donde se concentran los
errores que no dan aviso.

**Lo que no vuelve a construirse**: acceso, sesión de jornada, calendario compartido de días
hábiles, evaluador de plazos, auditoría inmutable, permisos, paginación y separación de roles
en base de datos. Ya existen y se reutilizan.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar un pendiente y verlo en su lista (Priority: P1)

Como integrante del área, registro lo que tengo que hacer —con o sin plazo, colgando o no de
un expediente— y lo veo en mi lista de trabajo.

**Por qué esta prioridad**: sin registro y lista no hay funcionalidad. Es lo que sustituye la
hoja de pendientes del Excel.

**Prueba independiente**: registrar un pendiente indicando solo su título, y encontrarlo en el
listado.

**Escenarios de aceptación**:

1. **Dado** un usuario con sesión iniciada, **cuando** registra un pendiente indicando
   únicamente el título, **entonces** se guarda y aparece en el listado.
2. **Dado** un pendiente, **cuando** se vincula a un proceso judicial, **entonces** el listado
   y la ficha muestran de qué expediente cuelga.
3. **Dado** un pendiente, **cuando** se vincula a un procedimiento administrativo, **entonces**
   ocurre lo mismo con ese expediente.
4. **Dado** un pendiente sin vínculo —«comprar tóner para impresora»—, **cuando** se guarda,
   **entonces** es válido y se muestra sin expediente relacionado.
5. **Dado** un pendiente, **cuando** se intenta vincularlo a un expediente judicial **y** a uno
   administrativo a la vez, **entonces** el sistema lo rechaza: cuelga de uno o de ninguno.
6. **Dado** el listado con más de veinticinco pendientes, **cuando** se pagina, **entonces**
   los filtros se conservan.

---

### User Story 2 - Marcar cumplido y poder deshacerlo (Priority: P1)

Como integrante del área, marco un pendiente como cumplido cuando lo termino; y si me
equivoco, puedo revertirlo explicando por qué.

**Por qué esta prioridad**: marcar cumplido es la acción más frecuente del día. Y sin revertir,
un error saca el pendiente de la lista activa sin salida —el insumo lo señala expresamente
como un problema que hoy no tiene solución (§20.1)—.

**Prueba independiente**: cumplir un pendiente, comprobar que sale de la lista activa y aparece
en cumplidos, revertirlo con motivo y comprobar que vuelve.

**Escenarios de aceptación**:

1. **Dado** un pendiente activo, **cuando** se marca como cumplido, **entonces** su estado pasa
   a «Cumplido», se registra la fecha y hora reales, sale de la lista activa y aparece en
   tareas cumplidas.
2. **Dado** un pendiente cumplido, **cuando** se revierte indicando un motivo, **entonces**
   vuelve al estado «Pendiente», se limpia la fecha de cumplimiento, regresa a la lista activa
   y sale de cumplidos.
3. **Dado** una reversión, **cuando** se intenta sin motivo, **entonces** el sistema la rechaza:
   es la única acción donde el motivo es obligatorio.
4. **Dado** un pendiente revertido, **cuando** se consulta su historial, **entonces** aparecen
   ambas entradas —el cumplimiento y la reversión—; la original no se borra ni se edita.
5. **Dado** un pendiente cumplido, **cuando** se revierte, **entonces** conserva la fecha
   programada que tenía antes, sin recalcularla. Si esa fecha ya pasó, reaparece como vencido.
6. **Dado** un pendiente que nunca se cumplió, **cuando** se intenta revertir, **entonces** el
   sistema lo rechaza.

---

### User Story 3 - Declarar no cumplido y reprogramar (Priority: P1)

Como integrante del área, declaro que no pude con algo y el sistema lo lleva al siguiente día
hábil; o elijo yo la fecha.

**Por qué esta prioridad**: es la otra mitad del día a día. Sin ella, lo que no salió se queda
con fecha vencida y la lista deja de reflejar el plan real.

**Prueba independiente**: declarar no cumplido un pendiente programado para un viernes y
comprobar que pasa al lunes, o al siguiente hábil si el lunes es feriado.

**Escenarios de aceptación**:

1. **Dado** un pendiente programado para un viernes, **cuando** se declara no cumplido,
   **entonces** su fecha programada pasa al lunes siguiente, su estado a «Reprogramado», y
   sigue activo.
2. **Dado** que el siguiente día hábil es feriado registrado, **cuando** se declara no
   cumplido, **entonces** se salta hasta el primer día realmente hábil.
3. **Dado** un año sin cobertura de calendario confirmada, **cuando** se declara no cumplido,
   **entonces** el sistema avisa de que no puede calcular el siguiente día hábil en lugar de
   elegir uno.
4. **Dado** un pendiente, **cuando** se reprograma manualmente a otra fecha, **entonces** el
   historial guarda la fecha anterior, la nueva y el instante del cambio.
5. **Dado** cualquier reprogramación, **cuando** se consulta el historial, **entonces** se lee
   como «Reprogramado del X al Y».
6. **Dado** una reprogramación manual, **cuando** se indica un motivo, **entonces** se guarda;
   **y cuando** no se indica, la acción se completa igual.

---

### User Story 4 - Vigilar los pendientes sin plazo (Priority: P1)

Como integrante del área, veo cuánto tiempo lleva esperando algo que no tiene fecha límite, y
el sistema me avisa cuando se pasa.

**Por qué esta prioridad**: es la trampa que el insumo señala en la §44 —«no basta con guardar
un pendiente»—. Sin fecha límite, un encargo puede quedarse meses sin que nada lo señale.

**Prueba independiente**: registrar un pendiente sin fecha límite con fecha de recepción
antigua y comprobar que aparece el aviso.

**Escenarios de aceptación**:

1. **Dado** un pendiente sin fecha límite, **cuando** se consulta, **entonces** se muestran los
   días hábiles transcurridos desde su fecha de recepción.
2. **Dado** un pendiente sin fecha límite con más de quince días hábiles transcurridos y estado
   distinto de «Cumplido», **cuando** se consulta, **entonces** se muestra el aviso de pendiente
   sin plazo, en texto y no solo por color.
3. **Dado** ese mismo pendiente, **cuando** pasa a «Cumplido», **entonces** el aviso desaparece.
4. **Dado** un pendiente sin fecha límite ni fecha de recepción, **cuando** se consulta,
   **entonces** no se inventa una antigüedad: se indica que no se puede calcular.
5. **Dado** un año sin cobertura de calendario, **cuando** se consulta la antigüedad,
   **entonces** se avisa en lugar de mostrar un número.

---

### User Story 5 - Trabajar el día de hoy y revisar lo hecho (Priority: P2)

Como integrante del área, abro una pantalla con lo programado para hoy, y otra con lo que ya
completé.

**Por qué esta prioridad**: son vistas del mismo dato, valiosas pero construibles después de
que las acciones funcionen.

**Prueba independiente**: con pendientes programados para hoy, ayer y mañana, comprobar qué
muestra cada pantalla.

**Escenarios de aceptación**:

1. **Dado** pendientes programados para varias fechas, **cuando** se abre «pendientes de hoy»,
   **entonces** se muestran los de hoy y los vencidos de días anteriores que siguen activos.
2. **Dado** un pendiente cumplido hoy, **cuando** se abre «pendientes de hoy», **entonces** ya
   no aparece.
3. **Dado** pendientes cumplidos, **cuando** se abre el historial de tareas cumplidas,
   **entonces** se muestran con su fecha de cumplimiento, el tiempo que tomaron y cuántas veces
   se reprogramaron.
4. **Dado** un pendiente revertido, **cuando** se abre el historial de cumplidas, **entonces**
   ya no aparece.

---

### User Story 6 - Administrar los tres catálogos (Priority: P2)

Como JEFA, mantengo las listas de tipos, prioridades y estados que usa el área.

**Por qué esta prioridad**: los pendientes se registran sin tipo ni prioridad asignados, así
que no bloquea; pero el listado filtra por los tres.

**Prueba independiente**: con los catálogos vacíos, crear valores, asignarlos, deshabilitar uno
e intentar borrar uno en uso.

**Escenarios de aceptación**:

1. **Dado** los catálogos vacíos, **cuando** JEFA crea tipos, prioridades y estados,
   **entonces** quedan disponibles para asignar.
2. **Dado** un valor en uso, **cuando** se intenta borrar, **entonces** el sistema lo impide y
   ofrece deshabilitarlo.
3. **Dado** un valor deshabilitado, **cuando** se registra un pendiente nuevo, **entonces** no
   se ofrece; y los pendientes que ya lo tenían lo conservan.
4. **Dado** un ABOGADO, **cuando** intenta modificar cualquiera de los tres catálogos,
   **entonces** el sistema lo rechaza; consultarlos sí está permitido.
5. **Dado** los tres catálogos, **cuando** se crea el mismo nombre en dos de ellos, **entonces**
   ambos se aceptan: son listas independientes.

---

### Edge Cases

- Un pendiente cuya fecha límite es anterior a su fecha de recepción: se acepta y se advierte,
  sin corregir ninguna de las dos.
- Un pendiente cumplido cuyo expediente vinculado se oculta del listado: el pendiente sigue
  legible y su vínculo también.
- Dos personas actúan a la vez sobre el mismo pendiente —una lo cumple y otra lo reprograma—:
  solo prospera una, y la otra recibe aviso de conflicto sin sobrescribir.
- Revertir un cumplido dos veces seguidas: la segunda se rechaza, porque ya no está cumplido.
- Un pendiente reprogramado muchas veces: el historial las conserva todas, y el contador de
  reprogramaciones se calcula al consultar.
- El responsable de un pendiente queda desactivado: el pendiente sigue legible y editable por
  JEFA, y conserva su responsable.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir registrar un pendiente indicando como mínimo su título.
- **FR-002**: El sistema DEBE registrar de cada pendiente: título, descripción, tipo,
  prioridad, estado, fecha de recepción, fecha de registro, fecha programada, fecha límite,
  fecha de cumplimiento, expediente vinculado, observaciones y si está activo.
- **FR-003**: Un pendiente DEBE poder vincularse a un proceso judicial, a un procedimiento
  administrativo, o a ninguno. NO DEBE poder vincularse a los dos a la vez.
- **FR-004**: El responsable de un pendiente DEBE ser el usuario que lo registra, y DEBE
  quedar fijo. De él se deriva quién puede actuar sobre el pendiente (FR-024).
- **FR-005**: El sistema DEBE permitir marcar un pendiente como cumplido, registrando el
  instante real, cambiando su estado, retirándolo de la lista activa y mostrándolo en tareas
  cumplidas.
- **FR-006**: Marcar como cumplido NO DEBE modificar la fecha programada.
- **FR-007**: El sistema DEBE permitir revertir un cumplimiento, devolviendo el estado a
  pendiente, limpiando la fecha de cumplimiento y restituyéndolo a la lista activa.
- **FR-008**: La reversión de un cumplimiento DEBE exigir un motivo. Es la única acción del
  sistema donde el motivo es obligatorio.
- **FR-009**: La reversión DEBE registrarse como una entrada nueva del historial. La entrada
  del cumplimiento original NO DEBE borrarse ni editarse.
- **FR-010**: El sistema DEBE permitir declarar un pendiente como no cumplido, llevando su
  fecha programada al siguiente día hábil, cambiando su estado a reprogramado y manteniéndolo
  activo.
- **FR-011**: El cálculo del siguiente día hábil DEBE usar el mismo calendario compartido que
  los plazos de expedientes. Sin cobertura confirmada, el sistema DEBE avisar en lugar de
  elegir una fecha.
- **FR-012**: El sistema DEBE permitir reprogramar manualmente a una fecha elegida, guardando
  en el historial la fecha anterior, la nueva y el instante del cambio.
- **FR-013**: El sistema DEBE permitir registrar un motivo opcional en las acciones que cambian
  estado o fechas, salvo en la reversión, donde es obligatorio.
- **FR-014**: Para un pendiente sin fecha límite, el sistema DEBE calcular los días hábiles
  transcurridos desde su fecha de recepción.
- **FR-015**: El sistema DEBE avisar cuando un pendiente sin fecha límite supere los quince
  días hábiles transcurridos y no esté cumplido. El aviso DEBE ser texto, no solo color.
- **FR-016**: Los días transcurridos, los días restantes, la antigüedad, el tiempo de atención
  y el número de reprogramaciones NO DEBEN persistirse: se calculan al consultar.
- **FR-017**: El historial de cada pendiente DEBE registrar acción, descripción, fecha anterior,
  fecha nueva, autor y motivo, y DEBE ser inmutable.
- **FR-018**: El historial DEBE distinguir quién ejecutó el cambio de quién es el responsable
  del pendiente, que pueden ser personas distintas.
- **FR-019**: El listado DEBE mostrar título, expediente relacionado, tipo, prioridad, estado,
  fecha programada, fecha límite, días hábiles restantes y antigüedad.
- **FR-020**: El sistema DEBE ofrecer una pantalla con los pendientes programados para hoy,
  incluyendo los vencidos de días anteriores que sigan activos.
- **FR-021**: El sistema DEBE ofrecer un historial de tareas cumplidas con fecha de
  cumplimiento, tiempo de atención y número de reprogramaciones.
- **FR-022**: El sistema DEBE ofrecer tres catálogos administrables e independientes entre sí y
  de los ya existentes: tipos de pendientes, prioridades y estados de pendientes.
- **FR-023**: Cada pendiente DEBE poder registrar el documento de salida generado —su tipo y su
  número—. Es un dato, NO un archivo: el sistema no almacena documentos.
- **FR-024**: ABOGADO DEBE actuar únicamente sobre los pendientes de los que es responsable;
  JEFA DEBE poder actuar sobre cualquiera, quedando registrado como intervención.
- **FR-025**: El sistema DEBE detectar que otra persona actuó sobre el pendiente mientras el
  formulario estaba abierto y rechazar el guardado sin sobrescribir.
- **FR-026**: Consultar cualquier pantalla NO DEBE generar entradas de historial.
- **FR-027**: Toda la interfaz y todos los mensajes DEBEN estar en español, y las rutas visibles
  DEBEN ser las que fija el insumo.

### Key Entities *(include if feature involves data)*

- **Pendiente**: tarea del día a día. Puede colgar de un expediente judicial, de uno
  administrativo o de ninguno. Tiene tipo, prioridad, estado, fechas de recepción, programada,
  límite y cumplimiento, y el documento de salida que generó.
- **Entrada de historial del pendiente**: registro inmutable de una acción, con su fecha
  anterior y nueva, el autor y el motivo. Es lo que permite reconstruir por qué algo se
  reprogramó tres veces.
- **Tipo de pendiente**, **Prioridad**, **Estado de pendiente**: tres catálogos administrables,
  independientes entre sí y de los catálogos de expedientes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Con 5.000 pendientes y cinco usuarios simultáneos, el listado, la pantalla de hoy
  y la ficha se abren en un segundo o menos en el 95% de las veces, y una acción se confirma en
  dos segundos o menos, medido sobre el equipo y la red de referencia.
- **SC-002**: Cinco usuarios de prueba registran un pendiente, lo cumplen y lo revierten sin
  instrucciones escritas.
- **SC-003**: El 100% de las reversiones exige motivo, y ninguna borra ni edita la entrada
  original del cumplimiento.
- **SC-004**: Para veinte casos de «no cumplido» verificados a mano, el siguiente día hábil
  calculado coincide en el 100%, incluidos los que caen en fin de semana o feriado.
- **SC-005**: Sin cobertura de calendario confirmada, el 100% de los cálculos de día hábil y de
  antigüedad muestran aviso en lugar de un número.
- **SC-006**: El 100% de las acciones que cambian estado o fechas aparece en el historial con
  autor, fechas anterior y nueva; el 100% de las consultas no genera ninguna entrada.
- **SC-007**: Ningún ABOGADO consigue actuar sobre un pendiente ajeno, tampoco componiendo la
  petición directamente.
- **SC-008**: Ningún pendiente puede quedar vinculado a un expediente judicial y a uno
  administrativo a la vez.
- **SC-009**: Los pendientes sin plazo con más de quince días hábiles muestran el aviso en el
  100% de los casos, y ninguno cumplido lo muestra.
- **SC-010**: Ningún valor derivado —antigüedad, días restantes, reprogramaciones, tiempo de
  atención— existe como columna en la base de datos.
- **SC-011**: Dos acciones simultáneas sobre el mismo pendiente no producen nunca un estado
  inconsistente: prospera una y la otra recibe conflicto.

## Assumptions

- **Confirmado el 2026-09-06**: el responsable de un pendiente es quien lo registra y queda
  fijo, igual que en expedientes judiciales y administrativos. La tabla original de la §8 no
  incluía el campo mientras la §13 afirmaba que «el responsable vive en el pendiente»; la
  contradicción se resolvió a favor de la §13 y el insumo quedó actualizado.

- Los valores de los tres catálogos son los que el cliente enumera en el insumo: trece tipos
  (§10), las prioridades de la §11 y los seis estados de la §12. Aun así los catálogos arrancan
  vacíos y los carga JEFA desde la aplicación, para que el sistema no dependa de una lista
  quemada en código.
- La fecha de registro se rellena con el instante del alta; la de recepción la escribe la
  persona, porque un encargo puede registrarse días después de llegar.
- El umbral de aviso es «más de quince días hábiles», tal como lo escribe la §19; es decir, el
  aviso aparece a partir del decimosexto.
- El documento de salida generado se guarda como tipo y número en texto. Un adjunto quedaría
  fuera por la §3.5.
- El «tiempo de atención» de la §32 se calcula en días hábiles entre recepción y cumplimiento,
  por coherencia con el resto del sistema.
- Un pendiente cancelado se comporta como no activo a efectos de las listas de trabajo, pero
  conserva su historial.
