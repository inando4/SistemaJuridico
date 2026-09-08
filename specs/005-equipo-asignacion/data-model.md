# Fase 1 — Modelo de datos

**Ninguna tabla nueva, ninguna columna nueva, ninguna migración.** Ver `research.md`, decisión 1.

---

## Entidades existentes que esta feature usa

| Tabla | Qué cambia | Qué no cambia |
|---|---|---|
| `judicial_case` | `owner_id`, `updated_at`, `version` al reasignar | Todo lo demás |
| `administrative_procedure` | Igual | Todo lo demás |
| `pending_task` | Igual, en bloque al reasignar su expediente | Fechas, estado, `completed_at`, vínculos |
| `app_user` | Nada. Se lee para la vista y para el desplegable de destinos | — |
| `audit_event` | Filas nuevas con `action = 'REASSIGN'` | Estructura |

La reasignación **no toca fechas, estados ni contenido**. Solo cambia de quién es un registro.

---

## Entidad sin tabla: la carga de un abogado

```java
public record CargaDeAbogado(
        UUID id, String nombre, boolean esJefa,
        int vencidos,          // deadline < hoy
        int estaSemana,        // deadline o scheduled_for dentro de la semana
        Integer sinPlazoAntiguos,  // null si el calendario no está confirmado
        int activos) {         // todos los activos no cumplidos
    public int cargaDeLaSemana() { return vencidos + estaSemana; }
}
```

`sinPlazoAntiguos` es `Integer` y no `int` **a propósito**: `null` significa «no se puede calcular porque el año no está revisado», que no es lo mismo que cero. Es el mismo criterio que la 004 usó para las fronteras ausentes. Un cero afirmaría que nadie lleva mucho esperando, y sería mentira.

Este registro **no se persiste jamás** (principio V). Se construye al leer y se descarta.

---

## Consulta de la vista de equipo — una sola

```sql
SELECT u.id, u.name, u.role,
       count(t.id) FILTER (WHERE ACTIVO AND t.deadline < :hoy)                    AS vencidos,
       count(t.id) FILTER (WHERE ACTIVO AND (
             (t.deadline      BETWEEN :lunes AND :domingo) OR
             (t.scheduled_for BETWEEN :lunes AND :domingo)))                      AS esta_semana,
       count(t.id) FILTER (WHERE ACTIVO AND t.deadline IS NULL
                             AND t.received_at < CAST(:hace15 AS date))           AS sin_plazo_antiguos,
       count(t.id) FILTER (WHERE ACTIVO)                                          AS activos
FROM app_user u
LEFT JOIN pending_task t ON t.owner_id = u.id
WHERE u.status = 'ACTIVE'
GROUP BY u.id, u.name, u.role
ORDER BY (vencidos + esta_semana) DESC, activos DESC, lower(btrim(u.name)), u.id
```

Donde `ACTIVO` es `(t.active AND t.completed_at IS NULL)`, la misma expresión que `DashboardRepository`.

Cuatro detalles que sostienen la consulta:

- **`LEFT JOIN`, no `JOIN`**: quien no tiene ningún pendiente tiene que salir con ceros (RF-018). Es precisamente la persona a la que se le puede asignar trabajo.
- **`count(t.id)`, no `count(*)`**: con `LEFT JOIN`, `count(*)` cuenta la fila nula del abogado sin pendientes y le da 1 donde debe ir 0.
- **`WHERE u.status = 'ACTIVE'` y nada de rol**: la jefa lleva expedientes y tiene que aparecer (`research.md`, decisión 6). Filtrar por `role = 'LAWYER'` la borraría de la vista.
- **`CAST(:hace15 AS date)`**: sin el `CAST`, PostgreSQL no infiere el tipo de un parámetro nulo. Es el mismo tropiezo que la 004 documentó. Cuando el calendario no está confirmado, `:hace15` llega nulo, el `FILTER` no cuenta nada y la capa Java convierte ese cero en `null`.

El desempate por `lower(btrim(u.name))` y luego `u.id` hace el orden **estable**: dos personas con la misma carga salen siempre igual entre dos aperturas.

**Presupuesto**: 1 consulta para la vista, más las que ya gasta la plantilla común. Total ≤ 4, y el mismo número con 5 personas que con 15.

---

## Fronteras que se calculan en Java

Se resuelven una vez por petición y llegan a SQL como fechas ya hechas — igual que la 004, para que el cálculo de días hábiles siga en una sola función (principio VI):

| Frontera | Cómo se calcula | Si no hay calendario |
|---|---|---|
| `lunes` | `hoy.with(DayOfWeek.MONDAY)` | No aplica: es aritmética de calendario |
| `domingo` | `lunes.plusDays(6)` | No aplica |
| `hace15` | `DeadlineEvaluator.restarDiasHabiles(hoy, 15, calendario)` | `null` → el recuento se muestra como aviso |

El calendario se toma con `CalendarRepository.paraAntiguedad(hoy)`, que abarca desde el año anterior: la antigüedad mira hacia atrás y en enero cruza el cambio de año. Usar `paraListado` fue el fallo que la 003 dejó en producción.

---

## Reasignación — las tres sentencias

**Paso 1, la foto previa.** PostgreSQL 17 no tiene `RETURNING OLD.*`, y hace falta el responsable **real** de cada pendiente, que puede no ser el del expediente (`research.md`, decisión 5):

```sql
SELECT id, owner_id FROM pending_task
WHERE judicial_case_id = :expediente AND owner_id <> :nuevo
```

**Paso 2, los cambios.** El expediente con comprobación de versión; los pendientes en bloque:

```sql
UPDATE judicial_case SET owner_id = :nuevo, updated_at = :ahora, version = version + 1
WHERE id = :expediente AND version = :version AND owner_id <> :nuevo

UPDATE pending_task SET owner_id = :nuevo, updated_at = :ahora, version = version + 1
WHERE judicial_case_id = :expediente AND owner_id <> :nuevo
```

El `AND owner_id <> :nuevo` es lo que evita historial sin cambios, **registro a registro** (`research.md`, decisión 8).

**Paso 3, el historial en bloque.** Una fila por registro movido:

| Columna | Valor |
|---|---|
| `entity_type` | `JUDICIAL_CASE`, `ADMINISTRATIVE_PROCEDURE` o `PENDING_TASK` |
| `action` | `REASSIGN` (texto libre: no hay `CHECK` sobre `action`) |
| `actor_id` | La jefa que lo ejecuta |
| `owner_id` | El responsable **anterior**, tomado del paso 1 |
| `before_values` | `{"ownerId": "<anterior>"}` |
| `after_values` | `{"ownerId": "<nuevo>"}` |
| `reason` | Para los pendientes, de qué expediente venía el traspaso |

En una asignación de alta (RF-014) no hay responsable anterior: `before_values` queda **nulo**, que es lo que distingue asignar de reasignar y lo que el principio VII pide conservar.

**Presupuesto**: 3 sentencias más las de lectura previa de la ficha; ≤ 6 en total, y el mismo número con 5 pendientes que con 50.

---

## Estados y transiciones

La única transición nueva es el cambio de responsable, y es libre entre cuentas activas:

```
responsable A ──(la jefa reasigna)──> responsable B
```

Con dos guardas:

- **Destino activo**: se rechaza reasignar a una cuenta inactiva (RF-010). Un registro cuyo responsable no puede entrar queda sin nadie que lo trabaje.
- **Destino distinto**: si el destino ya es el responsable, no hay transición ni historial (RF-011).

No hay estados intermedios ni reversión especial: deshacer una reasignación es otra reasignación, y ambas quedan en el historial.

---

## Autorización

`CaseAuthorization` y `PendingTaskAuthorization` ya deciden quién puede escribir a partir de `owner_id`. Al cambiar el responsable, esos permisos cambian solos:

- El nuevo responsable puede modificar, reprogramar, cancelar y **revertir un cumplido** que marcó el anterior (RF-008). Sale gratis del modelo existente, pero necesita prueba propia: es una decisión explícita del insumo, no un efecto que se pueda dar por supuesto.
- El anterior conserva la lectura y pierde la escritura (RF-009).

La comprobación de que solo la jefa reasigna se hace **en el servidor**, en `ReassignmentService`, no ocultando el botón en la plantilla (principio II).
