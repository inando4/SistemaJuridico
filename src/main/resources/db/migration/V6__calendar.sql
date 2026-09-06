-- Calendario de dias no laborables y constancia de revision anual.
--
-- La ausencia de fila en calendar_year equivale a cobertura faltante: el
-- contador de plazos avisa en vez de contar en silencio (FR-019).
CREATE TABLE calendar_year (
    year       integer PRIMARY KEY,
    -- Aumenta con cada alta, edicion o retiro de un dia de ese ano; invalida
    -- las revisiones anteriores.
    revision   bigint      NOT NULL DEFAULT 1,
    created_by uuid        NOT NULL REFERENCES app_user (id),
    created_at timestamptz NOT NULL,

    CONSTRAINT calendar_year_rango CHECK (year BETWEEN 1 AND 9999)
);

CREATE TABLE non_working_day (
    id          uuid PRIMARY KEY,
    -- El ano se obtiene de day; no se duplica en una columna aparte.
    day         date        NOT NULL UNIQUE,
    description text        NOT NULL,
    kind        text        NOT NULL,
    created_by  uuid        NOT NULL REFERENCES app_user (id),
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    version     bigint      NOT NULL DEFAULT 1,

    CONSTRAINT non_working_day_kind_valido CHECK (kind IN
        ('NATIONAL_HOLIDAY', 'REGIONAL_HOLIDAY', 'NON_WORKING_DAY', 'OTHER')),
    CONSTRAINT non_working_day_desc_largo CHECK (char_length(description) <= 150)
);

-- Una sola lectura por consulta para todo el listado, no una por fila.
CREATE INDEX non_working_day_por_dia ON non_working_day (day);

-- Evidencia inmutable de que una persona reviso la cobertura de un ano.
-- Tener entradas no acredita cobertura: hace falta esta declaracion explicita.
CREATE TABLE calendar_review (
    id                     uuid PRIMARY KEY,
    year                   integer     NOT NULL REFERENCES calendar_year (year),
    -- Revision tecnica del ano observada al confirmar. Si el calendario cambia
    -- despues, esta revision deja de valer y hay que revisar de nuevo.
    reviewed_revision      bigint      NOT NULL,
    reviewed_by            uuid        NOT NULL REFERENCES app_user (id),
    reviewed_at            timestamptz NOT NULL,
    full_year_reviewed     boolean     NOT NULL,
    low_count_acknowledged boolean     NOT NULL DEFAULT false,

    CONSTRAINT calendar_review_unica UNIQUE (year, reviewed_revision),
    -- La declaracion es obligatoria: no se confirma cobertura sin afirmarla.
    CONSTRAINT calendar_review_declarada CHECK (full_year_reviewed)
);
