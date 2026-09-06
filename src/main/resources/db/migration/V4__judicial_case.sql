-- Indice de control de expedientes judiciales fisicos. No guarda documentos.
--
-- La FK a procedural_status se anade en V5, porque ese catalogo se crea alli;
-- la columna existe desde aqui para no reescribir la tabla despues.
CREATE TABLE judicial_case (
    id                     uuid PRIMARY KEY,
    sequence_number        integer,
    -- Fijo al creador en 001: la asignacion entre abogados es funcionalidad posterior.
    owner_id               uuid        NOT NULL REFERENCES app_user (id),
    case_number            text        NOT NULL,
    claimant               text,
    respondent             text,
    subject                text,
    procedural_status_id   uuid,
    last_procedural_action text,
    next_procedural_action text,
    last_action_date       date,
    deadline               date,
    amount                 numeric(18, 2),
    property_address       text,
    notes                  text,
    management_actions     text,
    -- Solo visibilidad en el listado corriente. NO significa concluido: eso es
    -- una situacion procesal y vive en procedural_status_id.
    active                 boolean     NOT NULL DEFAULT true,
    created_at             timestamptz NOT NULL,
    updated_at             timestamptz NOT NULL,
    version                bigint      NOT NULL DEFAULT 1,

    CONSTRAINT judicial_case_numero_largo    CHECK (char_length(case_number) <= 150),
    CONSTRAINT judicial_case_direccion_largo CHECK (property_address IS NULL
                                                    OR char_length(property_address) <= 1000),
    CONSTRAINT judicial_case_texto_largo     CHECK (
        coalesce(char_length(claimant), 0)               <= 10000 AND
        coalesce(char_length(respondent), 0)             <= 10000 AND
        coalesce(char_length(subject), 0)                <= 10000 AND
        coalesce(char_length(last_procedural_action), 0) <= 10000 AND
        coalesce(char_length(next_procedural_action), 0) <= 10000 AND
        coalesce(char_length(notes), 0)                  <= 10000 AND
        coalesce(char_length(management_actions), 0)     <= 10000)
);

-- Unicidad normalizada del numero, conservando letras, separadores y ceros
-- iniciales del original. Aplica tambien a registros ocultos y de otros
-- responsables: el numero identifica al expediente en todo el area.
CREATE UNIQUE INDEX judicial_case_numero_unico
    ON judicial_case (lower(btrim(case_number)));

-- Indices de los filtros del listado. Desempate por id; el orden por fecha
-- coloca los NULL al final (FR-008).
CREATE INDEX judicial_case_listado    ON judicial_case (active, lower(btrim(case_number)), id);
CREATE INDEX judicial_case_responsable ON judicial_case (owner_id, id);
CREATE INDEX judicial_case_estado      ON judicial_case (procedural_status_id, id);
CREATE INDEX judicial_case_plazo       ON judicial_case (deadline NULLS LAST, id);
