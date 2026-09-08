# Contrato de pantallas — Funcionalidad 004

Dos pantallas de solo lectura. La ruta de alertas la fija el insumo (§35); la del dashboard se
acordó con el usuario el 2026-09-07 y quedó recogida en el propio insumo (§23). Ambas exigen
sesión. Ambas devuelven HTML; no hay API de datos.

**Ninguna de las dos escribe nada**, ni siquiera historial. Es comprobable y hay un criterio
de aceptación que lo comprueba (SC-008).

---

## Dashboard

`GET /`

Pantalla de entrada: al iniciar sesión se aterriza aquí. Sin parámetros.

Muestra las seis tarjetas de la §23, contando **solo los pendientes de quien mira**:

| Tarjeta | Qué cuenta |
|---|---|
| Urgentes hoy | Plazo **o** fecha programada igual a hoy |
| Vencidos | Plazo anterior a hoy |
| Próximos vencimientos | Plazo dentro de los 3 días hábiles siguientes, sin contar hoy |
| Sin plazo +15 días | Sin plazo, recibido hace más de 15 días hábiles |
| Pendientes activos | Todos los activos sin cumplir |
| Cumplidos este mes | Cumplidos dentro del mes calendario en curso |

Cada tarjeta **enlaza al listado de los pendientes que cuenta**, y ese listado contiene
exactamente los mismos: ni uno más, ni uno menos (SC-004). Las fronteras de días hábiles se
resuelven una sola vez por petición y se pasan a ambos, para que no puedan discrepar.

**Sin cobertura de calendario**: «Próximos vencimientos» y «Sin plazo +15 días» muestran el
aviso en texto en lugar de una cifra. Las otras cuatro siguen mostrando su número, porque solo
comparan fechas. Un número inventado es peor que ninguno: parece fiable.

**Sin pendientes**: las tarjetas muestran cero y un texto que lo dice. No una pantalla en
blanco que parezca un error.

La suma de las seis tarjetas **no cuadra con ningún total** y no debe presentarse como si lo
hiciera: un pendiente vencido y programado para hoy entra en dos.

Presupuesto: **≤ 4 consultas**, p95 **300 ms** con 5.000 pendientes.

---

## Alertas

`GET /alertas`

Ruta fijada por el insumo §35. Sin parámetros salvo `page`.

Lista los pendientes de quien mira que requieren atención, cada uno con su tipo de alerta,
ordenados en los cinco niveles de la §24:

| Nivel | Etiqueta | Cuándo |
|---|---|---|
| 1 | Vencido | Plazo superado |
| 2 | Urgente | Vence hoy |
| 3 | Urgente | Programado para hoy |
| 4 | Próximo vencimiento | Vence dentro de los 3 días hábiles siguientes |
| 5 | Pendiente antiguo | Sin plazo, más de 15 días hábiles |

**Cada pendiente aparece una sola vez**, con el nivel más urgente que le corresponda
(FR-013). Un vencido que además está programado para hoy sale como vencido, no dos veces.

Los cumplidos **no aparecen**, aunque su fecha ya hubiera pasado.

Dentro de cada nivel el orden es por fecha y luego por identificador, de modo que dos aperturas
seguidas devuelvan lo mismo.

Cada fila enlaza a la ficha del pendiente.

**Sin cobertura de calendario**: los niveles 4 y 5 no pueden determinarse y la pantalla lo
advierte; los niveles 1, 2 y 3 se siguen mostrando.

Página de 25, igual que el resto de listados. Fuera de rango devuelve vacío recuperable.

Presupuesto: **≤ 5 consultas**, p95 **400 ms** con 5.000 pendientes.

---

## Filtro nuevo en el listado de pendientes

`GET /pendientes?alerta=<nivel>`

Para que las tarjetas puedan enlazar a «exactamente estos pendientes» sin crear seis pantallas
nuevas, el listado existente acepta un filtro más, contra **lista cerrada**:

`vencidos` | `hoy` | `proximos` | `sin-plazo-antiguos` | `activos` | `cumplidos-del-mes`

Valor fuera de la lista devuelve **422**, igual que los demás filtros del listado. El filtro
se combina con los ya existentes sin conflicto.

---

## Rutas que dejan de estar reservadas

`RutasSegunInsumoTest` afirma hoy que `/alertas` está reservada y debe dar 404. Esta
funcionalidad la ocupa, y la prueba debe pasar a exigir lo contrario. Siguen reservadas
`/calendario`, `/actividad-diaria` y `/configuracion`.

## Cambio en el destino tras iniciar sesión

`SesionIniciada` redirige hoy a `/judiciales`. Pasa a redirigir a `/`. Dos pruebas con
navegador esperan el destino antiguo y hay que actualizarlas: `AccessibilityAcceptanceTest` y
`RecorridoQuickstartTest`.
