# Especificación: Configuración — el acceso a la administración del sistema

**Rama**: `007-configuracion`

**Creada**: 2026-09-08

**Estado**: Borrador

**Entrada**: descripción del usuario: "empieza con la fase 2"

**Origen en el insumo**: sección 36 (configuración). Es lo que queda de la **FASE 2** junto con la exportación (§38) y las estadísticas (§39), que esperan respuesta del cliente.

## El hallazgo que cambia lo que es esta feature

La sección 36 pide una pantalla `/configuracion` que permita administrar feriados, días no laborables, tipos de pendientes, estados y prioridades. Leído sin más, parece una página de enlaces a cosas que ya funcionan.

Al comprobarlo contra el código, es otra cosa: **cinco de esas pantallas no tienen ningún enlace entrante en todo el sistema**. Se comprobó plantilla por plantilla:

| Pantalla | Cómo se llega hoy |
|---|---|
| `/tipos-de-pendiente` | **Solo escribiendo la URL** |
| `/prioridades` | **Solo escribiendo la URL** |
| `/estados-de-pendiente` | **Solo escribiendo la URL** |
| `/estados-procesales` | **Solo escribiendo la URL** |
| `/estados-administrativos` | **Solo escribiendo la URL** |
| `/usuarios` | Solo desde sus propias subpáginas: quien no esté ya dentro no llega |
| `/dias-no-laborables` | Desde el panel y las alertas |

Es decir: la jefa no puede añadir un tipo de pendiente ni dar de alta a nadie sin que alguien le dicte una dirección. Esta feature no añade capacidades nuevas —las seis pantallas funcionan— sino que las hace **alcanzables**.

## Escenarios de usuario y pruebas *(obligatorio)*

### Historia 1 — Llegar a la administración desde la navegación (Prioridad: P1)

La jefa necesita añadir un tipo de pendiente nuevo, o dar de alta a una abogada que se incorpora. Abre **Configuración** en la navegación y desde ahí llega a la pantalla que necesita.

**Por qué esta prioridad**: es la feature entera. No hay una segunda historia porque no hay una segunda capacidad.

**Prueba independiente**: desde cualquier pantalla, se llega a las siete pantallas de administración sin escribir ninguna URL.

**Escenarios de aceptación**:

1. **Dado** cualquier usuario con sesión, **cuando** abre la navegación, **entonces** ve un enlace a **Configuración**.
2. **Dado** que abre Configuración, **cuando** mira la página, **entonces** encuentra enlaces a días no laborables, tipos de pendiente, prioridades, estados de pendiente, estados procesales y estados de procedimientos administrativos.
3. **Dado** un enlace cualquiera de esa página, **cuando** lo pulsa, **entonces** llega a la pantalla correspondiente, que ya existe y funciona.
4. **Dado** que quien mira es **la jefa**, **cuando** abre Configuración, **entonces** ve además el enlace a la administración de **cuentas de usuario**.
5. **Dado** que quien mira es **un abogado**, **cuando** abre Configuración, **entonces** **no** ve el enlace a cuentas: seguirlo solo le daría un rechazo.
6. **Dado** un abogado, **cuando** solicita `/usuarios` directamente, **entonces** el servidor lo rechaza igual que hoy: ocultar el enlace no es la autorización.
7. **Dado** un abogado, **cuando** abre Configuración y entra en un catálogo, **entonces** puede **consultarlo** pero no ve el formulario para añadir valores, que es el comportamiento que ya tienen esas pantallas.
8. **Dado** cualquier usuario, **cuando** está en Configuración, **entonces** la navegación marca esa sección como la actual.

### Casos límite

- **Un abogado que llega a `/configuracion` escribiendo la URL**: la ve, como cualquier otra pantalla de lectura. Lo que no ve es el enlace a cuentas.
- **Sin sesión**: se redirige al acceso, igual que el resto del sistema.
- **Una pantalla de destino que algún día cambie de ruta**: el enlace roto se vería de inmediato, porque hay una prueba que comprueba que cada destino de esta página existe como ruta servida.

## Requisitos *(obligatorio)*

- **RF-001**: El sistema DEBE ofrecer una pantalla en `/configuracion`, alcanzable desde la navegación principal.
- **RF-002**: La pantalla DEBE enlazar a la administración de **días no laborables y feriados** (§36 los enumera por separado; el sistema los administra en una sola pantalla, y así se explica).
- **RF-003**: DEBE enlazar a los cinco catálogos: tipos de pendiente, prioridades, estados de pendiente, estados procesales y estados de procedimientos administrativos.
- **RF-004**: DEBE enlazar a la administración de **cuentas de usuario**, y ese enlace **solo se muestra a la jefatura**.
- **RF-005**: Ocultar el enlace NO sustituye a la comprobación del servidor: `/usuarios` DEBE seguir rechazando a quien no sea jefa, exactamente como hoy.
- **RF-006**: Cada enlace DEBE ir acompañado de una línea que diga para qué sirve esa pantalla, para que quien entra por primera vez no tenga que abrirlas todas.
- **RF-007**: La pantalla NO DEBE duplicar la administración: no crea, edita ni borra nada. Todo eso sigue ocurriendo en las pantallas de destino, que ya funcionan y ya tienen sus pruebas.
- **RF-008**: La pantalla NO DEBE consultar la base de datos. Es una página de enlaces.

### Entidades clave

Ninguna. **Sin migración, sin tablas, sin columnas.** No hay estado nuevo en el sistema.

## Criterios de éxito *(obligatorio)*

- **CE-001**: Desde cualquier pantalla, se llega a las siete pantallas de administración sin escribir ninguna URL ni consultar documentación.
- **CE-002**: La jefa da de alta un tipo de pendiente nuevo empezando desde el panel, sin ayuda.
- **CE-003**: Un abogado no ve ninguna opción que, al pulsarla, le devuelva un rechazo.
- **CE-004**: `GET /configuracion` cuesta **cero consultas propias**: solo las de sesión y plantilla común. Es la única pantalla del sistema donde un número distinto de cero significaría que algo se coló.
- **CE-005**: Todos los destinos que la pantalla enlaza existen como rutas servidas. Un enlace roto se detecta en las pruebas, no al pulsarlo.

## Supuestos

- **Es una página de enlaces, no de pestañas.** Reimplementar dentro de `/configuracion` las cinco pantallas de catálogo que ya funcionan duplicaría formularios, permisos y pruebas para no ganar nada. El insumo pide «permitir administrar», y llevar a la pantalla que administra lo cumple.
- **Se incluye el enlace a cuentas de usuario, que §36 no enumera.** La sección lista feriados, días no laborables, tipos, estados y prioridades; las cuentas son la sección 5. Se añade a propósito y se marca como tal: `/usuarios` está hoy tan inalcanzable como los catálogos, y construir la pantalla cuyo único cometido es la accesibilidad dejando fuera una pantalla inaccesible sería entregar el arreglo y el defecto en el mismo commit. Si la jefa prefiere separarlas, es quitar un bloque.
- **El enlace a cuentas se oculta a quien no es jefa; los demás no.** Es una asimetría real del sistema, comprobada: los catálogos dejan **leer** a cualquiera y solo restringen la escritura, mientras que `/usuarios` exige jefatura ya en la lectura. Un enlace visible que siempre devuelve un rechazo es peor que ningún enlace. Ocultarlo es cortesía; la autorización sigue en el servidor (principio II).
- **Ninguna pantalla de destino se modifica.** Esta feature solo añade una página y un enlace de navegación.
- **La ruta `/configuracion` estaba reservada** en `RutasSegunInsumoTest` desde la 003, y era la última que quedaba. Ocuparla vacía esa comprobación, que hay que retirar o invertir en vez de dejarla recorriendo una lista vacía —un bucle sin elementos no afirma nada y pasa en silencio—.
