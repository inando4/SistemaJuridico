# Fase 1 — Modelo de datos

**Una tabla nueva y una migración: la V10, primera desde la V9.** Todo lo demás se lee de lo que ya existe.

---

## La única entidad nueva: `manual_activity`

```sql
CREATE TABLE manual_activity (
    id                   uuid PRIMARY KEY,
    owner_id             uuid        NOT NULL REFERENCES app_user (id),
    -- El dia en que se hizo el trabajo, NO el dia en que se registro.
    performed_on         date        NOT NULL,
    description          text        NOT NULL,
    -- El tipo es opcional y tiene dos formas excluyentes (RF-015a, RF-015c).
    pending_task_type_id uuid        REFERENCES pending_task_type (id) ON DELETE RESTRICT,
    other_type           text,
    active               boolean     NOT NULL DEFAULT true,
    created_at           timestamptz NOT NULL,
    updated_at           timestamptz NOT NULL,
    version              bigint      NOT NULL DEFAULT 1,

    CONSTRAINT manual_activity_texto_largo CHECK (
        char_length(description)                <= 10000 AND
        coalesce(char_length(other_type), 0)    <= 150),

    -- Con las dos columnas sueltas, nada impediria rellenar ambas y quedaria un
    -- registro cuyo tipo seria ambiguo. Lo impide la base, no la aplicacion.
    CONSTRAINT manual_activity_tipo_excluyente CHECK (
        pending_task_type_id IS NULL OR other_type IS NULL)
);

CREATE INDEX manual_activity_del_dia ON manual_activity (owner_id, performed_on);
CREATE INDEX manual_activity_tipo    ON manual_activity (pending_task_type_id);
```

Cuatro decisiones que la tabla fija:

- **`performed_on` es `date`, no `timestamptz`**. Es el día al que pertenece el trabajo, un dato que el usuario elige; no un instante que ocurre. No tiene el problema de zona horaria de `completed_at` porque no es un instante.
- **`active`, no borrado físico**. RF-022 dice que retirar no borra. Es el mismo criterio que el resto del sistema.
- **`version`** para el bloqueo optimista, igual que las otras tablas editables.
- **`manual_activity_tipo_excluyente`** en la base y no solo en el validador: un registro con las dos columnas llenas no sabría qué mostrar, y ninguna pantalla podría arreglarlo después.

### Lo que la V10 hace además de crear la tabla

```sql
-- 1. Ampliar el CHECK de la evidencia, igual que hizo la V9.
ALTER TABLE audit_event DROP CONSTRAINT audit_event_entidad_valida;
ALTER TABLE audit_event ADD CONSTRAINT audit_event_entidad_valida
    CHECK (entity_type IN (…los once de la V9…, 'MANUAL_ACTIVITY'));

-- 2. Permisos de la tabla nueva.
GRANT SELECT, INSERT, UPDATE ON manual_activity TO sistema_juridico_app;

-- 3. La deuda que la 005 dejo anotada (research.md, decision 11).
REVOKE UPDATE, DELETE, TRUNCATE ON flyway_schema_history FROM sistema_juridico_app;
```

**Sin el paso 1, toda escritura de auditoría de una actividad manual falla**: el `CHECK` de `audit_event.entity_type` es cerrado y hoy admite once valores, ninguno de ellos el nuevo. Es la parte de la migración que se olvida y que no se descubre hasta la primera prueba de integración.

`manual_activity` **no lleva `DELETE`** para el rol de la aplicación: retirar es poner `active = false`.

---

## Entidades sin tabla — no se persiten jamás (principio V)

```java
/** La union de lo cumplido y lo escrito a mano. Se construye al leer y se descarta. */
public record ActividadDelDia(
        LocalDate dia,
        List<PendingTask> cumplidos,     // derivado de pending_task.completed_at
        List<ManualActivity> manuales) { }

/** Una fecha con significado tomada de un registro que ya la tiene. */
public record EventoDeAgenda(
        LocalDate dia,
        Tipo tipo,                       // PROGRAMADO, VENCIMIENTO, ACTUACION
        String entidad,                  // PENDING_TASK, JUDICIAL_CASE, ADMINISTRATIVE_PROCEDURE
        UUID entidadId,
        String titulo,
        String tipoDePendiente,          // el nombre del catalogo; null si no aplica
        UUID responsableId,
        String responsable) { }

/** Los N mostrados de un tipo, y si hay mas. Sin total exacto (RF-005). */
public record GrupoDeResultados<T>(List<T> resultados, boolean hayMas) { }
```

---

## La actividad del día — dos consultas

**Los cumplidos.** La frontera se calcula en Java con `ClockConfig.ZONA` y llega como dos marcas de tiempo, nunca como un `CAST` (`research.md`, decisión 4):

```sql
SELECT … FROM pending_task t
WHERE t.owner_id = :persona
  AND t.completed_at >= :inicio
  AND t.completed_at <  :inicioDelSiguiente
ORDER BY t.completed_at ASC, t.id ASC
```

Comparar la columna desnuda es lo que deja usar el índice `pending_task_cumplidos`. `CAST(t.completed_at AS date) = :dia` resolvería en la zona del servidor —mandando lo cumplido después de las 19:00 de Lima al día siguiente— y además inutilizaría el índice.

**Las manuales.**

```sql
SELECT a.*, tipo.name AS tipo_catalogo
FROM manual_activity a
LEFT JOIN pending_task_type tipo ON tipo.id = a.pending_task_type_id
WHERE a.owner_id = :persona AND a.performed_on = :dia AND a.active
ORDER BY a.created_at ASC, a.id ASC
```

`LEFT JOIN`, no `JOIN`: el tipo es opcional, y con `JOIN` desaparecerían justo las actividades sin tipo y las de tipo escrito a mano.

**Presupuesto**: 2 consultas propias más las de la plantilla común. **La invariante**: el mismo número con 1 actividad que con 40.

---

## El calendario — una consulta por rango

Cinco ramas unidas, una por columna de fecha que produce un evento (`research.md`, decisión 5):

```sql
SELECT dia, tipo, entidad, entidad_id, titulo, tipo_pendiente, owner_id, responsable FROM (
    -- 1. Pendientes programados
    SELECT t.scheduled_for AS dia, 'PROGRAMADO' AS tipo, 'PENDING_TASK' AS entidad, … 
      FROM pending_task t LEFT JOIN pending_task_type tp ON tp.id = t.pending_task_type_id
      JOIN app_user u ON u.id = t.owner_id
     WHERE t.active AND t.scheduled_for BETWEEN :desde AND :hasta
    UNION ALL
    -- 2. Vencimientos de pendientes
    SELECT t.deadline, 'VENCIMIENTO', 'PENDING_TASK', … WHERE t.active AND t.deadline BETWEEN …
    UNION ALL
    -- 3. Vencimientos judiciales     4. Actuaciones judiciales     5. Vencimientos administrativos
    …
) e
WHERE (:persona IS NULL OR e.owner_id = :persona)
ORDER BY e.dia ASC, e.tipo ASC, e.titulo ASC, e.entidad_id ASC
```

- **Un pendiente puede aparecer dos veces**, en la rama 1 y en la 2, si tiene programación y vencimiento dentro del rango. Es correcto: son dos hechos en dos días.
- **`:desde` y `:hasta` los calcula Java** según la vista: el día es el mismo día; la semana, de lunes a domingo (misma aritmética que `SemanaDeTrabajo` de la 005); el mes, la **rejilla completa**, que empieza el lunes anterior al día 1 y acaba el domingo posterior al último. El calendario debe pintar esos días vecinos, y pedirlos en el mismo viaje evita una segunda consulta.
- **El orden es estable** por el desempate final en `entidad_id`: dos aperturas del mismo mes dan el mismo orden.
- **`:persona` nulo significa toda el área.** La restricción es de comodidad, no de permiso (principio II).

**Presupuesto**: 1 consulta de eventos, más la del calendario de días no laborables para el sombreado. **La invariante, que es lo que importa**: el mismo número de consultas en las tres vistas. Un mes cuesta lo que un día.

---

## El buscador — tres consultas, una por tipo

```sql
-- Judiciales (RF-002): se AÑADEN subject y notes
WHERE (c.case_number ILIKE :q ESCAPE '\' OR c.claimant ILIKE :q ESCAPE '\'
    OR c.respondent  ILIKE :q ESCAPE '\' OR c.subject  ILIKE :q ESCAPE '\'
    OR c.notes       ILIKE :q ESCAPE '\')
```

Lo mismo en administrativos (se añade `notes`) y en pendientes (se añade `notes`). Cada consulta usa `Paging.limitConSondeo()`: pide 26 para páginas de 25, y si vuelven 26 hay más (`research.md`, decisión 8).

**El escape se aplica a los campos nuevos igual que a los viejos.** El método `escapar(...)` ya existe en los tres repositorios; lo que cambia es la lista de columnas, no el tratamiento del término.

**La misma condición sirve al buscador global y al filtro `q` del listado** (RF-012). Se escribe una vez por repositorio y la usan los dos, que es lo que hace verdadero CE-003: buscar la misma palabra en dos sitios no puede dar resultados distintos.

**Presupuesto**: 3 consultas. **La invariante**: las mismas 3 con 300 resultados que con 3.

---

## Estados y transiciones

La actividad manual tiene una sola transición:

```
activa ──(su autor o la jefa la retira)──> retirada (active = false)
```

No hay vuelta atrás por diseño: volver a registrarla es un alta nueva, y ambas quedan en el historial. Las tres operaciones —alta, modificación, retirada— escriben en `audit_event` con `entity_type = 'MANUAL_ACTIVITY'`, conservando en `before_values` lo que decía antes (principio VII).

Cuando la actividad lleva tipo del catálogo, el alta llama a `AuditRecorder.referenciarCatalogosDePendiente(evento, tipoId)`, que ya existe y escribe `PENDING_TASK_TYPE` en la primera posición. Eso protege el valor del catálogo frente a un borrado posterior.

---

## Autorización

| Operación | Quién |
|---|---|
| Ver actividad diaria, calendario o buscador de cualquiera | Cualquier cuenta activa (principio II) |
| Registrar una actividad manual | Cualquier cuenta activa, siempre a su propio nombre |
| Modificar o retirar una actividad manual | **Solo su autor, o la jefa** (RF-020) |

La comprobación va **en el servicio**, no en la plantilla. Ocultar el botón no es autorización — es la lección que la 005 dejó escrita cuando `th:replace` se comió el `th:if` y el formulario de reasignación apareció para los abogados.

---

## El cambio en `CatalogDefinition`

`manual_activity` es el **segundo** usuario del catálogo de tipos de pendiente, y `enUsoActual` solo sabe mirar uno (`research.md`, decisión 1). El registro pasa a llevar una lista de pares tabla/columna:

```java
// Antes: String tablaEnUso, String columnaEnUso
// Despues: List<UsoDeCatalogo> usos   // (tabla, columna)
```

`TIPOS_DE_PENDIENTE` declara dos usos; los otros cuatro catálogos declaran uno y se comportan igual que hoy. `enUsoActual` recorre la lista y para en el primero que encuentre.
