# Investigación — Funcionalidad 004

Cuatro decisiones. La primera sostiene el diseño entero; la segunda es un fallo que esta
investigación destapó y que ya está en producción.

---

## Decisión 1 — Las fechas frontera se calculan en Java; el SQL solo compara

**Decisión**: `DeadlineEvaluator` gana dos métodos que devuelven **fechas**, no cuentas:

- `sumarDiasHabiles(desde, n)` → la fecha del n-ésimo día hábil posterior.
- `restarDiasHabiles(hasta, n)` → la fecha desde la cual han pasado n días hábiles.

Ambos devuelven `Optional.empty()` si falta cobertura de calendario, igual que los métodos
que ya existen. El controlador los llama una vez, obtiene dos fechas, y el repositorio cuenta
con `COUNT(*) FILTER (WHERE ...)` comparando fechas normales.

**Rationale**: el principio VI exige una sola función que decida qué es un día hábil, y el IV
prohíbe que una pantalla escale con las filas. Las dos alternativas obvias incumplen una u
otra:

- **Contar días hábiles en SQL** duplicaría la lógica de feriados en el motor de base de
  datos. Serían dos verdades sobre lo mismo, que es exactamente lo que el principio VI
  prohíbe; y la primera vez que alguien corrigiera una, la otra quedaría atrás en silencio.
- **Traer los pendientes y clasificarlos en Java** mantiene la función única pero arrastra
  hasta 5.000 filas por la red para mostrar seis números. Con la base en otra región, eso es
  justo la lentitud que motivó sustituir el Excel.

Resolver las fronteras primero deja lo mejor de ambas: la aritmética de días hábiles ocurre
una sola vez, en un solo sitio, sobre un puñado de fechas; y la base hace lo que sabe hacer,
que es contar filas que cumplen una comparación.

**Alternatives considered**: una tabla materializada de días hábiles con índice, consultable
desde SQL. Se descartó: es persistir un derivado (principio V), y obligaría a regenerarla cada
vez que alguien toque el calendario.

**Consecuencia de diseño**: las fronteras se resuelven **una sola vez por petición**, en el
controlador, y se pasan tanto a la consulta de las tarjetas como al listado al que enlazan. Si
cada uno las recalculara podrían discrepar —por un `<=` frente a un `<`, o por una petición
que cruza la medianoche— y SC-004 exige que coincidan al 100%.

---

## Decisión 2 — El calendario debe mirar también al año anterior

**Decisión**: añadir a `CalendarRepository` un snapshot que abarque desde el año pasado, y
usarlo allí donde se mide antigüedad hacia atrás.

**Rationale**: esto no es una elección de diseño sino la corrección de un fallo. Todo el
código llama hoy a `paraListado(hoy)`, que devuelve `instantanea(hoy.getYear(), hoy.getYear()
+ 5)`: **solo mira hacia adelante**. Pero el aviso de «sin plazo con más de 15 días hábiles»
cuenta hacia atrás desde la fecha de recepción.

Un pendiente recibido en diciembre y consultado en enero atraviesa un año que el snapshot no
incluye. `diasHabilesTranscurridos` recorre los años del intervalo, no encuentra el anterior
entre los cubiertos y devuelve vacío.

Comprobado el 2026-09-07 con `DeadlineEvaluator` y un snapshot equivalente al de
`paraListado`, para una recepción del 2026-12-15 consultada el 2027-01-12:

```text
>>> Con snapshot hacia adelante: Optional.empty
```

El resultado es una **falsa alarma**: la pantalla dice que falta revisar el calendario cuando
el calendario está perfectamente cubierto. Es el fallo benigno de los dos posibles —avisa en
vez de inventar un número— pero sigue siendo incorrecto, y en enero afectaría a todo pendiente
recibido el año anterior.

**Alcance**: el fallo **ya está en producción**, en la ficha de pendientes de la
funcionalidad 003 (`PendingTaskController`, donde se resuelve el aviso de pendiente sin
plazo). No lo introduce el dashboard; el dashboard lo hereda porque la tarjeta «Sin plazo +15
días» hace el mismo cálculo. Se corrige aquí porque es el mismo arreglo y porque dejarlo
obligaría a escribir la tarjeta sabiéndola rota.

**Por qué no se detectó antes**: las pruebas se escribieron en septiembre y usan fechas
relativas a «hoy». El cruce de año no aparece hasta enero. La prueba nueva
(`CoberturaAnualTest`) fija fechas absolutas a ambos lados del cambio de año.

**Alternatives considered**: ampliar `paraListado` para incluir siempre el año anterior. Se
descartó por ahora: traería feriados que la mayoría de pantallas no necesita. Se prefiere un
método explícito cuyo nombre diga que mira hacia atrás, y que lo usen solo las pantallas que
miden antigüedad.

---

## Decisión 3 — Los cinco niveles de alerta son un `CASE`, no cinco consultas

**Decisión**: una sola consulta asigna a cada pendiente su nivel con una expresión
condicional, y ordena por ese nivel:

```text
CASE
  WHEN deadline < :hoy                            THEN 1   -- vencido
  WHEN deadline = :hoy                            THEN 2   -- vence hoy
  WHEN scheduled_for = :hoy                       THEN 3   -- programado para hoy
  WHEN deadline BETWEEN :manana AND :frontera3    THEN 4   -- próximo vencimiento
  WHEN deadline IS NULL AND received_at <= :hace15 THEN 5  -- antiguo sin plazo
END
```

**Rationale**: FR-013 y SC-005 exigen que un pendiente aparezca **una sola vez**, con su nivel
más urgente. Cinco consultas unidas darían duplicados en cuanto un pendiente esté vencido y
además programado para hoy, que es un caso corriente. Un `CASE` evalúa en orden y se queda con
la primera coincidencia, que es precisamente la semántica de «el más urgente».

Además, cinco consultas serían cinco viajes para pintar una pantalla, contra el principio IV.

**Alternatives considered**: clasificar en Java tras traer los candidatos. Mismo problema de
volumen que en la decisión 1, y además el orden tendría que rehacerse en memoria, perdiendo la
paginación.

---

## Decisión 4 — «Activo» significa lo mismo aquí que en el listado

**Decisión**: toda cuenta de pendientes activos usa `active = true AND completed_at IS NULL`.

**Rationale**: los requisitos FR-003 a FR-007 hablan de «pendientes activos». En la
funcionalidad 003 se corrigió precisamente este punto: `active` es la columna de archivado, y
por sí sola no dice si algo está hecho. Un pendiente cumplido sigue teniendo `active = true`.

Si las tarjetas contaran solo por `active`, discreparían del listado al que enlazan —el mismo
defecto que se acaba de arreglar, una capa más arriba— y romperían SC-004.

**Alternatives considered**: ninguna. Es coherencia obligada con lo ya corregido.

---

## Lo que esta funcionalidad rompe al ocupar `/` y `/alertas`

No es una decisión, es inventario. Cuatro sitios asumen hoy que la entrada es `/judiciales` o
que `/alertas` no existe, y cada uno es una tarea, no una sorpresa:

| Dónde | Qué asume |
|---|---|
| `SesionIniciada` | Redirige a `/judiciales` tras iniciar sesión |
| `RutasSegunInsumoTest` | Afirma que `/alertas` está **reservada** y debe dar 404 |
| `AccessibilityAcceptanceTest.entrarConTeclado()` | Espera `waitForURL("**/judiciales**")` |
| `RecorridoQuickstartTest.entrar()` | Espera `waitForURL("**/judiciales**")` |

---

## Dependencia externa sin resolver

El cliente **aún no ha entregado el calendario oficial de feriados**. No bloquea la
implementación: el comportamiento sin cobertura está definido (FR-015), probado y ahora
además corregido para el cruce de años. Pero mientras no llegue, dos de las seis tarjetas
mostrarán el aviso en lugar de una cifra en producción.
