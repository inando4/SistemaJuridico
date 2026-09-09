# Especificación: Calendario, actividad diaria y buscador global

**Rama**: `006-calendario-actividad-busqueda`

**Creada**: 2026-09-08

**Estado**: Borrador

**Entrada**: descripción del usuario: "Calendario, que hice hoy y buscador global segun las secciones 31, 33 y 34 del insumo del cliente"

**Origen en el insumo**: secciones 31 (calendario), 33 (funcionalidad «¿qué hice hoy?») y 34 (buscador global).

Las tres son pantallas de consulta sobre datos que el sistema ya tiene, con **una sola excepción**: la actividad manual de la sección 33, que es un registro nuevo. Esa excepción es lo que da forma al orden de las historias.

## Escenarios de usuario y pruebas *(obligatorio)*

### Historia 1 — Encontrar cualquier cosa desde un solo sitio (Prioridad: P1)

Llega una llamada preguntando por un caso. Quien atiende recuerda el apellido del demandante, o el área que pidió el informe, o una palabra de las observaciones, pero no el número de expediente. Escribe lo que recuerda en un único buscador y ve los resultados de los tres sitios donde puede estar: judiciales, administrativos y pendientes.

**Por qué esta prioridad**: es lo que más se usa y lo que menos cuesta. Hoy hay que abrir tres listados distintos y buscar en cada uno, y **ninguno de los tres busca en todos los campos que el insumo pide**: en judiciales no se busca por materia ni por observaciones, y en administrativos y pendientes tampoco por observaciones. Es una pantalla de solo lectura, sin escritura ni migración, y por sí sola resuelve una tarea diaria completa.

**Prueba independiente**: se cargan registros de los tres tipos con un término distintivo en cada uno de los campos que el insumo enumera, se busca ese término y salen los tres. Entrega valor sin que existan el calendario ni la actividad diaria.

**Escenarios de aceptación**:

1. **Dado** un expediente judicial cuya **materia** contiene «servidumbre», **cuando** se busca «servidumbre», **entonces** aparece entre los resultados judiciales.
2. **Dado** un expediente judicial cuyas **observaciones** contienen «servidumbre» pero ningún otro campo, **cuando** se busca «servidumbre», **entonces** también aparece.
3. **Dado** un procedimiento administrativo cuyas **observaciones** contienen el término, **cuando** se busca, **entonces** aparece entre los resultados administrativos.
4. **Dado** un pendiente cuyas **observaciones** contienen el término, **cuando** se busca, **entonces** aparece entre los resultados de pendientes.
5. **Dado** un término que aparece en los tres tipos de registro, **cuando** se busca, **entonces** los resultados se presentan agrupados por tipo, y cada grupo indica cuántos muestra y si hay más.
6. **Dado** un resultado cualquiera, **cuando** se pulsa sobre él, **entonces** se abre su ficha.
7. **Dado** un término que no está en ningún registro, **cuando** se busca, **entonces** el sistema lo dice con claridad y no muestra una pantalla vacía sin explicación.
8. **Dado** un término escrito con distinta capitalización que la del registro, **cuando** se busca, **entonces** el registro aparece igualmente.
9. **Dado** un término que contiene caracteres con significado especial para la búsqueda (`%`, `_`), **cuando** se busca, **entonces** se tratan como texto literal y no como comodines.
10. **Dado** un abogado cualquiera, **cuando** busca, **entonces** ve resultados de todo el área, no solo los suyos: la lectura es compartida (principio II).
11. **Dado** un registro archivado o inactivo, **cuando** se busca su término, **entonces** aparece señalado como tal y no mezclado en silencio con los activos.

---

### Historia 2 — Ver y completar lo que hice hoy (Prioridad: P2)

Al final de la jornada, un abogado necesita saber qué hizo. El sistema le muestra automáticamente las actividades que cumplió durante el día. Como no todo lo que hace pasó antes por un pendiente —una consulta que atendió, una reunión, un trámite que resolvió sobre la marcha—, puede añadir a mano lo que falte.

**Por qué esta prioridad**: es la mitad más valiosa del insumo en esta feature (deja constancia de trabajo que hoy no queda registrado en ninguna parte) y a la vez la única que **escribe**: necesita la primera migración desde la V9. Va después del buscador porque el buscador no depende de ella y ella tiene más riesgo.

**Prueba independiente**: un abogado cumple dos pendientes en el día y añade una actividad manual; la pantalla muestra las tres y solo las tres.

**Escenarios de aceptación**:

1. **Dado** un abogado que cumplió tres pendientes hoy, **cuando** abre su actividad diaria, **entonces** las tres aparecen sin que haya tenido que registrarlas.
2. **Dado** ese mismo abogado, **cuando** añade una actividad manual, **entonces** queda listada junto a las automáticas y se distingue de ellas.
3. **Dado** un abogado que registra una actividad manual **sin indicar tipo**, **cuando** la guarda, **entonces** el sistema la acepta y la lista sin tipo.
4. **Dado** un abogado que elige **«Otro»** y escribe «Reunión con Contabilidad», **cuando** la guarda, **entonces** ese texto queda como su tipo y **no** aparece después en el catálogo de tipos de pendiente.
5. **Dado** un pendiente que cumplió y luego revirtió el mismo día, **cuando** abre la pantalla, **entonces** ya no aparece: dejó de estar cumplido.
6. **Dado** un pendiente que cumplió ayer, **cuando** abre la actividad de hoy, **entonces** no aparece; **cuando** retrocede a la fecha de ayer, **entonces** sí.
7. **Dado** un día sin ninguna actividad, **cuando** abre la pantalla, **entonces** lo dice explícitamente en vez de mostrarse en blanco.
8. **Dado** un abogado, **cuando** consulta la actividad diaria, **entonces** ve la suya por omisión y puede consultar la de otra persona del área (principio II).
9. **Dado** una actividad manual recién registrada, **cuando** se consulta su historial, **entonces** consta quién la creó y cuándo (principio VII).
10. **Dado** una actividad manual con un error, **cuando** su autor la corrige o la retira, **entonces** el cambio queda en el historial y no se pierde el rastro de lo que decía antes.
11. **Dado** una actividad manual de otra persona, **cuando** alguien que no es su autor ni la jefa intenta modificarla, **entonces** el sistema lo rechaza.
12. **Dado** un intento de registrar una actividad manual en una fecha futura, **cuando** se envía, **entonces** el sistema lo rechaza: es un registro de lo ya hecho.

---

### Historia 3 — Ver el mes, la semana o el día en un calendario (Prioridad: P3)

La jefa quiere ver de un vistazo cómo está repartido el mes: qué vence, qué hay programado, qué audiencias vienen. Abre el calendario y cambia entre día, semana y mes.

**Por qué esta prioridad**: es la pantalla con más trabajo de interfaz y la que menos capacidad nueva aporta —cada fecha que muestra ya se puede consultar hoy en los listados—. Va la última porque su valor es de presentación, no de información nueva, y porque ninguna de las otras dos la necesita.

**Prueba independiente**: con pendientes programados, vencimientos, audiencias y actuaciones repartidos por un mes, las tres vistas muestran cada evento en su día.

**Escenarios de aceptación**:

1. **Dado** un pendiente programado para el 15, **cuando** se abre el mes, **entonces** aparece en el día 15.
2. **Dado** un pendiente que vence el 20 y un expediente judicial que vence el 20, **cuando** se abre el mes, **entonces** ambos aparecen en el día 20 y se distingue de qué tipo es cada uno.
3. **Dado** un pendiente de tipo «Audiencia» con fecha programada, **cuando** se abre el calendario, **entonces** aparece señalado como audiencia.
4. **Dado** un expediente judicial con fecha de última actuación dentro del mes, **cuando** se abre el mes, **entonces** esa actuación aparece en su día.
5. **Dado** un mes cualquiera, **cuando** se cambia a la vista de semana y luego a la de día, **entonces** cada vista muestra los mismos eventos que le corresponden por fecha, sin que aparezcan o desaparezcan.
6. **Dado** un evento cualquiera del calendario, **cuando** se pulsa sobre él, **entonces** se abre la ficha del registro al que pertenece.
7. **Dado** un día no laborable confirmado, **cuando** se abre el mes, **entonces** se distingue visualmente de los días laborables.
8. **Dado** un día con más eventos de los que caben en su casilla, **cuando** se abre el mes, **entonces** se indica cuántos hay y se puede llegar a todos.
9. **Dado** un abogado, **cuando** abre el calendario, **entonces** ve el suyo por omisión y puede ver el de todo el área.
10. **Dado** un mes sin ningún evento, **cuando** se abre, **entonces** la rejilla se muestra igualmente con sus días, indicando que no hay nada.

---

### Casos límite

- **Un término de búsqueda de una sola letra, o vacío**: buscar «a» devolvería casi todo. El buscador exige un mínimo y lo dice, en vez de castigar a la base de datos con una consulta inútil.
- **Un término que aparece en cientos de registros**: los resultados se limitan por grupo y se indica que hay más, sin volcar la tabla entera en la pantalla.
- **Cumplir un pendiente cerca de la medianoche**: la actividad diaria agrupa por la fecha local del área (Lima, UTC−5), no por la del servidor. Un pendiente cumplido a las 23:50 pertenece a ese día.
- **Un pendiente cumplido y revertido varias veces el mismo día**: aparece si al momento de mirar está cumplido; el vaivén queda en el historial del pendiente, no en la actividad diaria.
- **Una actividad manual el mismo día en que su autor deja de estar activo**: el registro se conserva; es historia de trabajo hecho.
- **Un mes que empieza en domingo o acaba en lunes**: la rejilla completa la semana con días de los meses vecinos y los distingue de los del mes en curso.
- **Un expediente sin fecha de vencimiento ni actuación**: no aparece en el calendario. La ausencia de fecha no es una fecha.
- **El calendario del año no está confirmado**: el calendario **sigue funcionando**. Sus eventos son fechas guardadas, no cálculos de días hábiles; lo único que se pierde es el sombreado de días no laborables, y se avisa de ello.

## Requisitos *(obligatorio)*

### Buscador global (sección 34)

- **RF-001**: El sistema DEBE ofrecer un buscador único, accesible desde la navegación, que consulte a la vez expedientes judiciales, procedimientos administrativos y pendientes.
- **RF-002**: En **judiciales** DEBE buscar en: número de expediente, demandante, demandado, **materia** y **observaciones**. Los dos últimos no están cubiertos hoy.
- **RF-003**: En **administrativos** DEBE buscar en: número de expediente, área solicitante, pedido y **observaciones**. El último no está cubierto hoy.
- **RF-004**: En **pendientes** DEBE buscar en: título, descripción y **observaciones**. El último no está cubierto hoy.
- **RF-005**: Los resultados DEBEN presentarse agrupados por tipo de registro, indicando cuántos se muestran en cada grupo y si hay más de los mostrados. No se exige el total exacto de coincidencias: contarlo cuesta una consulta más por grupo y no cambia lo que se hace con el resultado.
- **RF-006**: La búsqueda DEBE ignorar mayúsculas y minúsculas. Las tildes sí distinguen: ver Supuestos.
- **RF-007**: Los caracteres con significado especial en la búsqueda DEBEN tratarse como texto literal.
- **RF-008**: El sistema DEBE exigir un número mínimo de caracteres y explicarlo cuando no se cumpla.
- **RF-009**: Cada grupo de resultados DEBE limitarse a un número máximo por página e indicar cuándo hay más coincidencias de las mostradas.
- **RF-010**: Cada resultado DEBE identificar su registro de forma inequívoca y enlazar a su ficha.
- **RF-011**: El buscador DEBE mostrar registros de toda el área, con independencia de quién sea su responsable, y DEBE **alcanzar los archivados e inactivos, señalándolos**. Es deliberadamente más amplio que los listados, que muestran solo los activos por omisión.
- **RF-012**: Los filtros de búsqueda por texto que ya existen en cada listado DEBEN cubrir los mismos **campos** que RF-002, RF-003 y RF-004. Lo que se comparte es en qué columnas se busca; el filtro de visibilidad de cada listado sigue siendo suyo y no cambia.

### Actividad diaria (sección 33)

- **RF-013**: El sistema DEBE mostrar, sin que nadie los registre, los pendientes que constan como cumplidos en la fecha consultada.
- **RF-014**: Esa lista automática NO DEBE almacenarse: se calcula al consultarla a partir de los pendientes cumplidos (principio V).
- **RF-015**: Los usuarios DEBEN poder registrar una **actividad manual**: trabajo realizado que nunca existió como pendiente. Se le pide una **descripción** de lo hecho y la **fecha**; ambas obligatorias.
- **RF-015a**: La actividad manual DEBE admitir un **tipo, opcional**. Quien registra puede dejarlo en blanco, elegir uno del catálogo «Tipos de pendiente» (Informe legal, Oficio, Carta, Audiencia, Seguimiento, y los que la jefa añada), o elegir **«Otro»** y escribirlo con sus palabras.
- **RF-015b**: Un tipo escrito a mano se guarda tal cual, asociado solo a esa actividad. NO DEBE crear una entrada en el catálogo: el catálogo lo administra la jefa, y una pantalla de registro diario no puede ampliarlo por su cuenta.
- **RF-015c**: El sistema DEBE poder distinguir un tipo del catálogo de uno escrito a mano, para que un informe posterior no los mezcle sin advertirlo.
- **RF-016**: Una actividad manual DEBE quedar asociada a su autor y a la fecha en que se realizó el trabajo, y esa fecha NO PUEDE ser futura.
- **RF-017**: La pantalla DEBE distinguir visualmente las actividades automáticas de las manuales.
- **RF-018**: La pantalla DEBE mostrar por omisión la fecha de hoy y permitir consultar fechas anteriores.
- **RF-019**: Los usuarios DEBEN poder consultar la actividad de cualquier persona activa del área, con la propia como valor por omisión.
- **RF-020**: Solo el autor de una actividad manual, o la jefa, DEBEN poder modificarla o retirarla.
- **RF-021**: El alta, la modificación y la retirada de una actividad manual DEBEN quedar en el historial con autor y fecha, conservando lo que decía antes (principio VII).
- **RF-022**: Retirar una actividad manual NO DEBE borrarla del historial.

### Calendario (sección 31)

- **RF-023**: El sistema DEBE ofrecer un calendario con vistas de **día**, **semana** y **mes**.
- **RF-024**: El calendario DEBE mostrar, en el día que les corresponde: pendientes con fecha programada, vencimientos de pendientes y de expedientes (judiciales y administrativos), pendientes de tipo audiencia y actuaciones judiciales con fecha.
- **RF-025**: Cada evento DEBE indicar de qué tipo es y a qué registro pertenece, y DEBE enlazar a su ficha.
- **RF-026**: Las tres vistas DEBEN mostrar el mismo conjunto de eventos para un mismo rango de fechas: cambiar de vista no cambia lo que hay.
- **RF-027**: El calendario DEBE distinguir los días no laborables confirmados de los laborables, y DEBE seguir funcionando cuando el año no esté confirmado, avisando de que la distinción no está disponible.
- **RF-028**: Un día con más eventos de los que caben DEBE indicar cuántos hay y permitir llegar a todos.
- **RF-029**: El calendario DEBE permitir ver los eventos propios (valor por omisión) o los de toda el área.
- **RF-030**: Los eventos del calendario NO DEBEN almacenarse como registros propios: se leen de los registros que ya los tienen (principio V).

### Entidades clave

- **Actividad manual**: trabajo realizado que no pasó por un pendiente. Tiene autor, fecha de realización, una descripción de lo hecho, un tipo **opcional** —del catálogo o escrito a mano— y el rastro de quién la creó o modificó y cuándo. Es **la única entidad nueva de esta feature** y la única que se persiste.
- **Actividad del día** (sin tabla): la unión de los pendientes cumplidos en una fecha y las actividades manuales de esa fecha. Se construye al consultar y se descarta; nunca se guarda como resumen.
- **Evento de calendario** (sin tabla): una fecha con significado tomada de un registro existente —programación, vencimiento, audiencia o actuación—. Nunca se guarda.
- **Resultado de búsqueda** (sin tabla): la referencia mínima a un registro que coincide, con lo justo para identificarlo y abrirlo.

## Criterios de éxito *(obligatorio)*

- **CE-001**: Quien atiende una consulta encuentra el expediente escribiendo un dato parcial que recuerde, sin saber en cuál de los tres listados está y sin conocer el número de expediente.
- **CE-002**: Una búsqueda de un término presente en los tres tipos de registro devuelve resultados de los tres, incluidos los que solo coinciden por materia u observaciones.
- **CE-003**: Buscar la misma palabra desde el buscador global y desde el filtro de texto de un listado devuelve **los mismos registros activos** de ese tipo. El buscador alcanza además los archivados, señalados (RF-011); el listado los oculta salvo que se pidan. La diferencia es de visibilidad, nunca de campos.
- **CE-004**: Un abogado obtiene la lista de lo que hizo en el día sin registrar nada, y completa lo que falte en menos de un minuto por actividad.
- **CE-005**: La actividad de un día refleja siempre el estado actual de los registros, no una foto tomada ese día: un pendiente revertido deja de aparecer, y una actividad añadida después con fecha anterior aparece. Nunca se guarda un resumen del día.
- **CE-006**: La jefa localiza en el calendario todo lo que ocurre en una semana sin abrir ningún listado.
- **CE-007**: El número de consultas que cuesta pintar el calendario **no depende del número de días de la vista**: el mes cuesta lo mismo que el día. Es la comprobación que distingue una consulta por rango de una consulta por día repetida treinta y una veces (principio IV).
- **CE-008**: El número de consultas del buscador **no depende del número de resultados**: buscar un término con 300 coincidencias cuesta lo mismo que uno con 3.
- **CE-009**: Cada una de las tres pantallas responde dentro del presupuesto de tiempo fijado en el plan, medida sobre el volumen de prueba del proyecto (5.000 pendientes).
- **CE-010**: Ninguna de las tres pantallas escribe en la base de datos al consultarse, salvo el alta explícita de una actividad manual.

## Supuestos

- **Los recordatorios de la sección 31 quedan fuera del alcance.** La sección los enumera, pero el insumo no los define en ninguna otra parte y sí los sitúa expresamente como «Recordatorios automáticos» dentro de la **FASE 3**. No existen hoy como registro ni como fecha guardada: no hay nada que mostrar en un calendario. Se implementarán cuando el insumo los describa.
- **Las audiencias no son una entidad nueva.** «Audiencia» es uno de los tipos de pendiente que el catálogo ya trae. Un pendiente de ese tipo con fecha programada **es** la audiencia; el calendario solo la señala como tal.
- **Las actuaciones se toman de la fecha de última actuación** del expediente judicial, que es el único dato de fecha que el sistema guarda sobre ellas. Son hechos pasados, y como tales aparecen en el calendario.
- **La actividad diaria y el historial de cumplidas (sección 32, ya implementada) son pantallas distintas y ambas se conservan.** El historial responde «qué se ha cumplido» a lo largo del tiempo, con sus filtros; la actividad diaria responde «qué hice ese día», e incluye lo que nunca fue un pendiente. Sin las actividades manuales serían la misma pantalla con distinto filtro; con ellas, no.
- **La mitad automática de la actividad diaria es derivada y la manual es almacenada.** Es la tensión de esta feature con el principio V, y se resuelve así: no se guarda ningún resumen del día ni ninguna copia de los pendientes cumplidos; lo único que se escribe es la actividad que no tiene otro origen.
- **El tipo libre se acepta a sabiendas de que fragmenta.** Dos personas escribirán «Reunión» y «reunión con contabilidad» y contarán como cosas distintas. Se prefiere así porque un desplegable cerrado obligaría a forzar la actividad dentro de un tipo que no le corresponde, o a no registrarla. Los tipos escritos a mano que se repitan son además la mejor señal de qué falta en el catálogo: la jefa puede convertirlos en tipos de verdad más adelante, y esa corrección es trabajo posterior, no de esta feature.
- **Las fechas se agrupan por el día local del área** (Lima, UTC−5), el mismo criterio que ya usa el resto del sistema para vencimientos y alertas.
- **El calendario no depende de los días no laborables para funcionar.** Sus eventos son fechas guardadas, no resultados de contar días hábiles. Solo el sombreado de días no laborables requiere el año confirmado, y su ausencia se avisa (principio VI).
- **Ninguna de las tres pantallas introduce restricciones de visibilidad nuevas.** Todo el equipo lee todo; lo que cambia por responsable es la escritura (principio II). El filtro «solo lo mío» de la actividad diaria y del calendario es una comodidad, no un permiso.
- **El buscador llega más lejos que los listados, a propósito.** Un listado es una lista de trabajo y muestra lo activo; el buscador responde a «llamaron preguntando por un caso» y ese caso puede estar archivado. Si el buscador ocultara los archivados no serviría para lo único que se le pide. Por eso CE-003 compara **los activos**: es la parte en la que los dos deben coincidir, y la que se rompería si se ampliaran los campos en un sitio y no en el otro.
- **El buscador distingue las tildes.** «Perez» no encontrará «Peréz». El insumo no pide lo contrario, y hacerlo obligaría a una extensión de PostgreSQL (`unaccent`) que en Supabase se instala fuera del rol de la aplicación, más una función envoltorio inmutable y un índice funcional por cada campo, porque `unaccent()` no es indexable tal cual. Es una decisión de coste, no un olvido: si la jefa lo pide, entra como trabajo propio con su índice delante, no como una cláusula suelta.
- **RF-012 modifica tres pantallas que la sección 34 no menciona.** Ampliar los campos de búsqueda alcanza también a los filtros de texto de `/judiciales`, `/administrativos` y `/pendientes`, que se usan a diario y ya tienen pruebas. Se hace a propósito: si el buscador global encuentra por observaciones y el listado no, la misma palabra da dos resultados distintos según dónde se escriba, y eso se lee como un fallo. Quien planifique debe contar esas pruebas existentes dentro del alcance.
- **El buscador no ordena por relevancia.** El insumo no la pide, y una fórmula de relevancia inventada haría el orden imprevisible. Los resultados se ordenan dentro de cada grupo por un criterio explicable (el más reciente primero).
- **La feature requiere una migración** (la primera desde la V9) para la tabla de actividades manuales. Aprovechará el viaje para el `REVOKE` pendiente sobre `flyway_schema_history`, anotado en la investigación de la 005.
