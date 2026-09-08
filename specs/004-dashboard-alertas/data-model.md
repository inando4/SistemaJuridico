# Modelo de datos — Funcionalidad 004

**No hay tablas nuevas, ni columnas nuevas, ni migración.** Es la consecuencia directa del
principio V: las seis cifras del dashboard son valores derivados y no pueden almacenarse. Se
calculan al abrir la pantalla, cada vez.

Este documento describe qué se lee, cómo se clasifica y qué tipos aparecen en el código.

## Lo que se lee

| Tabla | Columnas usadas | Para qué |
|---|---|---|
| `pending_task` | `owner_id`, `deadline`, `scheduled_for`, `received_at`, `completed_at`, `active` | Las seis cuentas y los cinco niveles |
| `non_working_day` | `day` | Decidir qué días son hábiles |
| `calendar_year` + `calendar_review` | `year`, `revision`, `reviewed_revision` | Decidir si un año tiene cobertura confirmada |
| `app_user` | `id` | Saber de quién son los pendientes que se cuentan |

Nada se escribe. Ni siquiera historial: consultar no es cambiar (FR-019).

## Las fechas frontera

El controlador las resuelve **una vez por petición** con `DeadlineEvaluator` y se las pasa
tanto a las tarjetas como al listado al que enlazan, para que no puedan discrepar.

| Frontera | Cómo se obtiene | Para qué |
|---|---|---|
| `hoy` | Reloj de la aplicación | Vencido, vence hoy, programado para hoy |
| `frontera3` | `sumarDiasHabiles(hoy, 3)` | Próximos vencimientos |
| `hace15` | `restarDiasHabiles(hoy, 15)` | Sin plazo con más de 15 días hábiles |
| `inicioDeMes` | Primer día del mes en curso | Cumplidos este mes |

`frontera3` y `hace15` son `Optional`: vacíos cuando falta cobertura de calendario. Cuando
alguno lo está, su tarjeta muestra el aviso en lugar de una cifra, y las demás siguen
funcionando —vencido, vence hoy y activos no dependen del calendario, solo comparan fechas.

## Las seis cuentas

Todas se resuelven en **una sola consulta** con agregación condicional, filtrando siempre por
el responsable de la sesión.

| Tarjeta | Condición |
|---|---|
| Urgentes hoy | activo **y** (`deadline = hoy` **o** `scheduled_for = hoy`) |
| Vencidos | activo **y** `deadline < hoy` |
| Próximos vencimientos | activo **y** `deadline` entre mañana y `frontera3` |
| Sin plazo +15 días | activo **y** `deadline IS NULL` **y** `received_at <= hace15` |
| Pendientes activos | activo |
| Cumplidos este mes | `completed_at >= inicioDeMes` |

Donde **activo** significa `active = true AND completed_at IS NULL`, igual que en el listado
de pendientes. Ver decisión 4 de [research.md](research.md).

Las seis condiciones no son excluyentes: un pendiente vencido y programado para hoy entra en
dos tarjetas. **La suma de las seis no tiene por qué coincidir con ningún total**, y así debe
entenderse la pantalla.

## Los cinco niveles de alerta

A diferencia de las tarjetas, aquí cada pendiente aparece **una sola vez**. El nivel se asigna
con una expresión condicional que se evalúa en orden y se queda con la primera coincidencia,
que es la más urgente (FR-013).

| Nivel | Nombre en pantalla | Condición |
|---|---|---|
| 1 | Vencido | `deadline < hoy` |
| 2 | Urgente | `deadline = hoy` |
| 3 | Urgente (programado) | `scheduled_for = hoy` |
| 4 | Próximo vencimiento | `deadline` entre mañana y `frontera3` |
| 5 | Pendiente antiguo | `deadline IS NULL` y `received_at <= hace15` |

Un pendiente que no encaje en ninguno no aparece. Los cumplidos tampoco, con independencia de
sus fechas (FR-014).

El orden de la pantalla es el de los niveles; dentro de cada nivel, por fecha y luego por
identificador, para que dos aperturas seguidas den el mismo orden.

## Tipos nuevos en el código

Ninguno es una entidad persistida: son formas de devolver lo calculado.

**`ResumenDelDia`** — las seis cifras de una petición. Cada una puede ser un número o la
ausencia de número por falta de calendario; el tipo debe distinguir «cero» de «no se puede
saber», porque en pantalla son cosas muy distintas.

**`NivelDeAlerta`** — los cinco niveles, con su orden y su nombre en español. Enumeración, no
tabla: los fija el insumo y no son administrables. Es la diferencia con los catálogos de la
003, que sí lo son porque el área los cambia.

**`PendienteConAlerta`** — un pendiente ya clasificado, para la lista. Reutiliza lo que ya
devuelve el repositorio de pendientes y le añade su nivel.

## Lo que deliberadamente no existe

- **Ninguna tabla de alertas.** Una alerta no es un registro, es una lectura del estado
  actual. Guardarla obligaría a mantenerla al día con cada cambio de fecha y crearía una
  segunda verdad sobre la urgencia.
- **Ninguna columna calculada** en `pending_task`: ni `dias_restantes`, ni `nivel_alerta`, ni
  `es_urgente`. Persistir un derivado va contra el principio V, y además quedaría obsoleto
  cada medianoche sin que nadie tocara la fila.
- **Ningún caché de las cuentas.** Con una sola consulta acotada no hace falta, y un caché
  introduciría la pregunta de cuándo invalidarlo.
