-- Pendientes: el trabajo diario del area. El insumo la llama «la tabla mas
-- importante del sistema» (seccion 8), y es exacto: los expedientes son el
-- indice, esto es lo que se hace cada dia.
--
-- Una sola migracion para la entidad, los tres catalogos, la tabla de referencia
-- historica y la ampliacion de la restriccion de auditoria: si fueran varias,
-- existiria un momento en que la aplicacion podria auditar un tipo que la base
-- todavia rechaza.
--
-- Las migraciones V1 a V8 estan aplicadas en produccion y NO se modifican.

-- Tres catalogos propios, independientes entre si y de los ya existentes --------
CREATE TABLE pending_task_type (
    id          uuid PRIMARY KEY,
    name        text        NOT NULL,
    description text,
    enabled     boolean     NOT NULL DEFAULT true,
    created_by  uuid        NOT NULL REFERENCES app_user (id),
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    version     bigint      NOT NULL DEFAULT 1,
    CONSTRAINT pending_task_type_name_largo CHECK (char_length(name) <= 150)
);
CREATE UNIQUE INDEX pending_task_type_nombre_unico
    ON pending_task_type (lower(btrim(name)));

CREATE TABLE priority (
    id          uuid PRIMARY KEY,
    name        text        NOT NULL,
    description text,
    enabled     boolean     NOT NULL DEFAULT true,
    created_by  uuid        NOT NULL REFERENCES app_user (id),
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    version     bigint      NOT NULL DEFAULT 1,
    CONSTRAINT priority_name_largo CHECK (char_length(name) <= 150)
);
CREATE UNIQUE INDEX priority_nombre_unico ON priority (lower(btrim(name)));

CREATE TABLE pending_task_status (
    id          uuid PRIMARY KEY,
    name        text        NOT NULL,
    description text,
    enabled     boolean     NOT NULL DEFAULT true,
    created_by  uuid        NOT NULL REFERENCES app_user (id),
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    version     bigint      NOT NULL DEFAULT 1,
    CONSTRAINT pending_task_status_name_largo CHECK (char_length(name) <= 150)
);
CREATE UNIQUE INDEX pending_task_status_nombre_unico
    ON pending_task_status (lower(btrim(name)));

-- El pendiente -----------------------------------------------------------------
CREATE TABLE pending_task (
    id                          uuid PRIMARY KEY,
    -- Quien lo registra, y queda fijo. De aqui se deriva quien puede actuar
    -- sobre el (seccion 5.1 del insumo).
    owner_id                    uuid        NOT NULL REFERENCES app_user (id),
    title                       text        NOT NULL,
    description                 text,
    pending_task_type_id        uuid        REFERENCES pending_task_type (id) ON DELETE RESTRICT,
    priority_id                 uuid        REFERENCES priority (id) ON DELETE RESTRICT,
    pending_task_status_id      uuid        REFERENCES pending_task_status (id) ON DELETE RESTRICT,
    -- Un pendiente cuelga de un expediente judicial, de uno administrativo, o de
    -- ninguno (seccion 9). Nunca de los dos.
    judicial_case_id            uuid        REFERENCES judicial_case (id) ON DELETE RESTRICT,
    administrative_procedure_id uuid        REFERENCES administrative_procedure (id) ON DELETE RESTRICT,
    -- Referencia de antiguedad cuando no hay fecha limite (seccion 19).
    received_at                 date,
    registered_at               date        NOT NULL,
    scheduled_for               date,
    deadline                    date,
    -- Marca del cumplimiento. Al revertir se pone a nulo; scheduled_for NO se
    -- toca en ningun momento, de modo que al deshacer la fecha nunca se perdio.
    completed_at                timestamptz,
    -- El documento de salida es un DATO, no un archivo (secciones 44 y 3.5).
    output_document_type        text,
    output_document_number      text,
    notes                       text,
    active                      boolean     NOT NULL DEFAULT true,
    created_at                  timestamptz NOT NULL,
    updated_at                  timestamptz NOT NULL,
    version                     bigint      NOT NULL DEFAULT 1,

    CONSTRAINT pending_task_titulo_largo CHECK (char_length(title) <= 150),
    CONSTRAINT pending_task_texto_largo CHECK (
        coalesce(char_length(description), 0)            <= 10000 AND
        coalesce(char_length(notes), 0)                  <= 10000 AND
        coalesce(char_length(output_document_type), 0)   <= 150 AND
        coalesce(char_length(output_document_number), 0) <= 150),

    -- Con dos columnas sin restriccion, nada impediria rellenar ambas y quedaria
    -- un registro que ninguna pantalla sabria mostrar. Lo impide la base.
    CONSTRAINT pending_task_vinculo_excluyente CHECK (
        judicial_case_id IS NULL OR administrative_procedure_id IS NULL)
);

CREATE INDEX pending_task_listado    ON pending_task (active, scheduled_for NULLS LAST, id);
CREATE INDEX pending_task_responsable ON pending_task (owner_id, id);
CREATE INDEX pending_task_estado      ON pending_task (pending_task_status_id, id);
CREATE INDEX pending_task_plazo       ON pending_task (deadline NULLS LAST, id);
CREATE INDEX pending_task_judicial    ON pending_task (judicial_case_id);
CREATE INDEX pending_task_administrativo ON pending_task (administrative_procedure_id);
CREATE INDEX pending_task_cumplidos   ON pending_task (completed_at DESC);

-- Ampliacion de la evidencia ---------------------------------------------------
-- Sustituir la restriccion no toca ninguna fila: las entradas ya escritas cumplen
-- la nueva, que es un superconjunto. Tampoco altera privilegios.
ALTER TABLE audit_event DROP CONSTRAINT audit_event_entidad_valida;

ALTER TABLE audit_event ADD CONSTRAINT audit_event_entidad_valida
    CHECK (entity_type IN (
        'APP_USER', 'JUDICIAL_CASE', 'PROCEDURAL_STATUS', 'NON_WORKING_DAY',
        'CALENDAR_REVIEW', 'ADMINISTRATIVE_PROCEDURE', 'ADMINISTRATIVE_STATUS',
        'PENDING_TASK', 'PENDING_TASK_TYPE', 'PRIORITY', 'PENDING_TASK_STATUS'));

-- Referencia historica de los tres catalogos nuevos -----------------------------
-- Aqui SI conviene un discriminador y no tres tablas: las claves foraneas
-- apuntarian a tablas distintas y harian falta tres tablas casi identicas. Se
-- acepta perder la clave foranea al catalogo porque la proteccion la da la
-- comprobacion de uso historico antes de borrar, que consulta esta tabla.
CREATE TABLE pending_task_history_reference (
    audit_event_id uuid NOT NULL REFERENCES audit_event (id) ON DELETE RESTRICT,
    catalog_kind   text NOT NULL,
    catalog_id     uuid NOT NULL,
    PRIMARY KEY (audit_event_id, catalog_kind, catalog_id),

    CONSTRAINT pending_task_history_kind_valido CHECK (catalog_kind IN
        ('PENDING_TASK_TYPE', 'PRIORITY', 'PENDING_TASK_STATUS'))
);

CREATE INDEX pending_task_history_por_catalogo
    ON pending_task_history_reference (catalog_kind, catalog_id);

-- Es evidencia: la aplicacion la lee y la amplia, nunca la modifica.
REVOKE UPDATE, DELETE, TRUNCATE ON pending_task_history_reference
    FROM sistema_juridico_app;
GRANT SELECT, INSERT ON pending_task_history_reference TO sistema_juridico_app;
