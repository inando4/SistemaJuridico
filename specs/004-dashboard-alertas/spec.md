# Feature Specification: Dashboard y sistema de alertas

**Feature Branch**: `004-dashboard-alertas`

**Created**: 2026-09-07

**Status**: Draft

**Input**: User description: "Dashboard y sistema de alertas segun las secciones 23, 24 y 35 del insumo del cliente"

## User Scenarios & Testing *(mandatory)*

Hoy, quien entra al sistema aterriza en la lista de expedientes judiciales y tiene que
deducir por sí mismo qué es lo urgente: abrir pendientes, mirar fechas, comparar con el
calendario. El insumo pide lo contrario —una pantalla que responda «¿qué tengo que hacer
hoy?» antes de que nadie busque nada— y una lista de alertas donde eso mismo aparezca
detallado, no solo contado.

Las seis tarjetas cuentan **los pendientes de quien mira**, no los del área. Es coherente
con que el insumo llame al proyecto «sistema web personal» y con que el responsable de un
pendiente sea fijo. La carga del equipo es otra pregunta y tiene su propia pantalla en la
sección 5.2 del insumo, fuera de esta funcionalidad.

### User Story 1 - Ver de un vistazo qué exige atención hoy (Priority: P1)

Como integrante del área, al entrar al sistema veo seis tarjetas con la cuenta de lo
vencido, lo de hoy, lo que viene, lo que lleva demasiado tiempo parado, lo activo y lo
cumplido este mes; y puedo pulsar cualquiera para ver esos pendientes.

**Why this priority**: es lo primero que ve cualquiera al entrar y el motivo por el que el
cliente pidió el sistema. Sin esta pantalla, saber qué es urgente exige abrir pendientes de
uno en uno y comparar fechas a mano, que es exactamente el trabajo que hoy hace el Excel.

**Independent Test**: registrar pendientes en cada una de las seis situaciones, abrir el
dashboard y comprobar que cada tarjeta cuenta los suyos y que al pulsarla se llega a esos
mismos pendientes.

**Acceptance Scenarios**:

1. **Dado** un pendiente con plazo anterior a hoy y sin cumplir, **cuando** se abre el
   dashboard, **entonces** la tarjeta «Vencidos» lo cuenta.
2. **Dado** un pendiente con plazo o programación para hoy, **cuando** se abre el dashboard,
   **entonces** la tarjeta «Urgentes hoy» lo cuenta.
3. **Dado** un pendiente con plazo dentro de los próximos 3 días hábiles, **cuando** se abre
   el dashboard, **entonces** la tarjeta «Próximos vencimientos» lo cuenta.
4. **Dado** un pendiente sin plazo recibido hace más de 15 días hábiles y sin cumplir,
   **cuando** se abre el dashboard, **entonces** la tarjeta «Sin plazo +15 días» lo cuenta.
5. **Dado** un pendiente cumplido dentro del mes actual, **cuando** se abre el dashboard,
   **entonces** la tarjeta «Cumplidos este mes» lo cuenta y las tarjetas de urgencia no.
6. **Dado** un pendiente de otro integrante del área, **cuando** se abre el dashboard,
   **entonces** ninguna tarjeta lo cuenta.
7. **Dado** que se pulsa una tarjeta, **cuando** se abre el listado al que lleva,
   **entonces** contiene exactamente los pendientes que esa tarjeta contaba.

---

### User Story 2 - Recorrer las alertas en orden de urgencia (Priority: P1)

Como integrante del área, abro la pantalla de alertas y veo mis pendientes que requieren
atención, cada uno con el tipo de alerta que le corresponde, ordenados de lo más urgente a
lo menos.

**Why this priority**: la tarjeta dice cuántos; la alerta dice cuáles. Sin esta pantalla el
dashboard obliga a entrar y salir de seis listados distintos para reconstruir el orden en el
que hay que trabajar, que es justo lo que el insumo fija en la sección 24.

**Independent Test**: crear un pendiente de cada tipo de alerta, abrir la pantalla y
comprobar que aparecen todos, cada uno con su etiqueta, y en el orden de los cinco niveles.

**Acceptance Scenarios**:

1. **Dado** un pendiente con plazo superado, **cuando** se abre la pantalla de alertas,
   **entonces** aparece con la alerta «Vencido».
2. **Dado** un pendiente que vence hoy, **cuando** se abre la pantalla, **entonces** aparece
   con la alerta «Urgente».
3. **Dado** un pendiente que vence dentro de los próximos 3 días hábiles, **cuando** se abre
   la pantalla, **entonces** aparece con la alerta «Próximo vencimiento».
4. **Dado** un pendiente sin plazo con más de 15 días hábiles desde su recepción, **cuando**
   se abre la pantalla, **entonces** aparece con la alerta «Pendiente antiguo».
5. **Dado** pendientes de varios tipos a la vez, **cuando** se abre la pantalla, **entonces**
   salen en este orden: vencidos, los que vencen hoy, los programados para hoy, los próximos
   y los antiguos sin plazo.
6. **Dado** un pendiente cumplido, **cuando** se abre la pantalla, **entonces** no aparece,
   aunque su fecha ya hubiera pasado.
7. **Dado** un pendiente que cumple dos condiciones a la vez, **cuando** se abre la pantalla,
   **entonces** aparece **una sola vez**, con la alerta más urgente de las dos.

---

### User Story 3 - Entrar directamente al dashboard (Priority: P2)

Como integrante del área, al iniciar sesión llego al dashboard, no a la lista de
expedientes; y puedo volver a él desde cualquier pantalla.

**Why this priority**: sin esto el dashboard existe pero nadie lo ve, porque la sesión
seguiría aterrizando en expedientes judiciales. Va en P2 porque las dos pantallas anteriores
tienen valor aunque haya que navegar a ellas a mano.

**Independent Test**: iniciar sesión y comprobar dónde se aterriza; después, desde tres
pantallas distintas, volver al dashboard por el mismo enlace.

**Acceptance Scenarios**:

1. **Dado** un usuario con credenciales válidas, **cuando** inicia sesión, **entonces**
   aterriza en el dashboard.
2. **Dado** un usuario dentro de cualquier pantalla, **cuando** usa la navegación principal,
   **entonces** puede volver al dashboard.
3. **Dado** un usuario sin sesión, **cuando** pide el dashboard, **entonces** se le lleva a
   iniciar sesión y, tras hacerlo, al dashboard.

---

### Edge Cases

- **Sin cobertura de calendario confirmada**: las tarjetas y alertas que dependen de contar
  días hábiles («Próximos vencimientos», «Sin plazo +15 días») no pueden dar un número
  fiable. Deben avisar de que falta revisar el calendario en vez de mostrar un número
  inventado, igual que ya hace la ficha de un pendiente. Lo vencido y lo de hoy sí se
  calculan, porque son una comparación de fechas y no dependen del calendario.
- **Un pendiente en dos categorías**: vence hoy y además lleva más de 15 días sin plazo no
  puede ocurrir (o tiene plazo o no lo tiene), pero vencido y programado para hoy sí. En las
  alertas aparece una sola vez con la más urgente; en las tarjetas, cada una cuenta según su
  propio criterio y la suma de las seis no tiene por qué coincidir con el total.
- **Dashboard vacío**: quien no tiene ningún pendiente ve las tarjetas en cero y un texto que
  lo diga, no una pantalla en blanco que parezca un error.
- **Mes recién empezado**: el día 1, «Cumplidos este mes» vale cero aunque el día anterior se
  cerraran veinte. Es correcto y debe quedar claro que el corte es el mes en curso.
- **Pendiente sin fecha programada ni plazo**: solo puede entrar en «Sin plazo +15 días» y en
  «Pendientes activos»; nunca en las de urgencia.
- **Volumen**: quien acumule miles de pendientes debe ver el dashboard igual de rápido; las
  cuentas no pueden recorrer todas las filas de una en una.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE ofrecer un dashboard con seis tarjetas de resumen: «Urgentes
  hoy», «Vencidos», «Próximos vencimientos», «Sin plazo +15 días», «Pendientes activos» y
  «Cumplidos este mes».
- **FR-002**: Cada tarjeta DEBE contar únicamente pendientes cuyo responsable sea quien está
  mirando la pantalla.
- **FR-003**: «Urgentes hoy» DEBE contar los pendientes activos cuyo plazo **o** cuya fecha
  programada sea hoy.
- **FR-004**: «Vencidos» DEBE contar los pendientes activos cuyo plazo sea anterior a hoy.
- **FR-005**: «Próximos vencimientos» DEBE contar los pendientes activos cuyo plazo caiga
  dentro de los **3 días hábiles** siguientes a hoy, sin incluir los de hoy.
- **FR-006**: «Sin plazo +15 días» DEBE contar los pendientes activos sin plazo cuya fecha de
  recepción esté a más de 15 días hábiles de hoy.
- **FR-007**: «Pendientes activos» DEBE contar todos los pendientes activos sin cumplir.
- **FR-008**: «Cumplidos este mes» DEBE contar los pendientes cumplidos cuya fecha de
  cumplimiento caiga dentro del mes calendario en curso.
- **FR-009**: Cada tarjeta DEBE llevar al listado de los pendientes que cuenta, y ese listado
  DEBE contener exactamente los mismos.
- **FR-010**: El sistema DEBE ofrecer una pantalla de alertas en `/alertas` con los
  pendientes del usuario que requieren atención.
- **FR-011**: Cada alerta DEBE mostrarse con su tipo: «Urgente» (vence hoy), «Vencido» (plazo
  superado), «Próximo vencimiento» (dentro de los 3 días hábiles siguientes) o «Pendiente
  antiguo» (sin plazo, más de 15 días hábiles).
- **FR-012**: Los pendientes de la pantalla de alertas DEBEN ordenarse en cinco niveles:
  vencidos, los que vencen hoy, los programados para hoy, los de vencimiento próximo y los
  antiguos sin plazo.
- **FR-013**: Un pendiente que cumpla más de una condición DEBE aparecer una sola vez en la
  pantalla de alertas, con la alerta correspondiente al nivel más urgente.
- **FR-014**: Los pendientes cumplidos NO DEBEN aparecer en las alertas ni contarse en las
  tarjetas de urgencia, con independencia de sus fechas.
- **FR-015**: Cuando falte cobertura de calendario confirmada para algún año que haya que
  atravesar, las cuentas y alertas que dependan de días hábiles DEBEN avisar de esa falta en
  lugar de mostrar un número.
- **FR-016**: Al iniciar sesión, el sistema DEBE llevar al usuario al dashboard.
- **FR-017**: El dashboard DEBE ser alcanzable desde la navegación principal de cualquier
  pantalla.
- **FR-018**: El dashboard NO DEBE persistir ninguna de las cuentas que muestra: se calculan
  al abrirlo a partir de los pendientes y del calendario.
- **FR-019**: Consultar el dashboard o las alertas NO DEBE escribir en el historial: son
  lecturas, no cambios.
- **FR-020**: La interfaz DEBE estar íntegramente en español, incluidos los nombres de las
  tarjetas y de los tipos de alerta.

### Key Entities

Esta funcionalidad **no introduce ninguna entidad nueva ni ninguna tabla**. Lee lo que ya
existe:

- **Pendiente**: aporta responsable, plazo, fecha programada, fecha de recepción, fecha de
  cumplimiento y si está activo. De ahí sale cada una de las seis cuentas.
- **Calendario de días no laborables**: decide qué es un día hábil, y por tanto qué entra en
  «próximos 3 días hábiles» y en «más de 15 días hábiles». Su cobertura confirmada decide
  también cuándo hay que avisar en vez de contar.
- **Usuario**: identifica de quién son los pendientes que se cuentan.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Quien entra al sistema identifica qué pendiente atender primero en **menos de
  10 segundos**, sin abrir ningún otro listado.
- **SC-002**: El dashboard abre en **menos de 2 segundos** con 5.000 pendientes registrados.
- **SC-003**: Las seis cuentas se resuelven **sin recorrer las filas de una en una**: el
  coste de abrir el dashboard no crece con el número de pendientes.
- **SC-004**: El **100%** de los pendientes que una tarjeta cuenta aparece en el listado al
  que esa tarjeta lleva, y ninguno más.
- **SC-005**: Ningún pendiente aparece dos veces en la pantalla de alertas.
- **SC-006**: Con el calendario sin confirmar, **ninguna** cifra que dependa de días hábiles
  se muestra como número: se muestra el aviso.
- **SC-007**: Un pendiente ajeno **nunca** entra en las cuentas ni en las alertas de quien
  mira.
- **SC-008**: Abrir el dashboard y las alertas no añade **ninguna** entrada al historial.
- **SC-009**: Ambas pantallas se pueden recorrer y usar **solo con el teclado**, y siguen
  siendo legibles si el navegador no carga JavaScript.
- **SC-010**: La pantalla de alertas ordena correctamente los cinco niveles en el **100%** de
  los casos comprobados.

## Assumptions

- **«Próximos días hábiles» son 3.** El insumo lo deja sin número en las secciones 23 y 35.
  Se elige un margen corto para que la tarjeta siga siendo accionable y no se convierta en una
  segunda lista de pendientes. Decisión tomada con el usuario el 2026-09-07.
- **Las tarjetas cuentan lo propio, no lo del área.** El insumo llama al proyecto «sistema
  web personal» y la sección 5.1 fija que todos pueden ver todo, pero ver no es lo mismo que
  contar: un dashboard que sumara los cinco no diría a nadie qué hacer con su día. La visión
  de conjunto es la sección 5.2, que va en otra funcionalidad. Decisión tomada con el usuario
  el 2026-09-07.
- **«Urgentes hoy» incluye plazo y programación.** La sección 23 lo dice literalmente
  («vencimiento o programación para hoy»), mientras que la 35 llama «Urgente» solo a lo que
  vence hoy. Se interpreta que la tarjeta agrupa ambos casos y que la pantalla de alertas los
  distingue en dos niveles, como pide la sección 24. Así las tres secciones se sostienen a la
  vez.
- **El dashboard sustituye a expedientes judiciales como pantalla de entrada.** El insumo no
  fija ruta para la sección 23; se toma la raíz del sitio. Un dashboard al que hubiera que
  navegar no cumpliría su función.
- **«Este mes» es el mes calendario en curso**, no los últimos 30 días.
- **El calendario real de feriados sigue sin entregarse.** Las pantallas se construyen y se
  prueban con días no laborables inventados. Hasta que el cliente entregue el calendario
  oficial, las cuentas que dependen de días hábiles mostrarán el aviso en producción; es el
  comportamiento correcto, pero no el definitivo.

## Out of Scope

- **Vista de equipo y carga por abogado** (sección 5.2 del insumo): pantalla propia, otra
  funcionalidad.
- **Asignación y reasignación de expedientes** (sección 5.3).
- **Calendario** (sección 31), **¿qué hice hoy?** (33), **buscador global** (34),
  **configuración** (36) y **exportación** (38).
- **Notificaciones por correo**: el sistema no envía correo, y el insumo tampoco lo pide para
  las alertas. Las alertas se ven al entrar.
- **Alertas configurables por el usuario**: los cuatro tipos y sus umbrales los fija el
  insumo.
- **Estadísticas** (sección 39) y **migración del Excel** (37), fuera de alcance por decisión
  previa y por el propio insumo.
