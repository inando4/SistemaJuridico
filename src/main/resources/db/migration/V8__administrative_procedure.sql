-- Procedimientos administrativos: la segunda clase de expediente que tramita el
-- area. Insumo secciones 7, 7.1, 29 y 30.
--
-- Una sola migracion para las tres tablas y la ampliacion de la restriccion de
-- auditoria: si fueran dos, existiria un momento en que la aplicacion podria
-- intentar auditar un tipo que la base todavia rechaza.
--
-- Las migraciones V1 a V7 estan aplicadas en produccion y NO se modifican.

-- Catalogo propio de estados ---------------------------------------------------
-- Distinto del de estados procesales judiciales y del de pendientes. Arranca
-- vacio: los valores los carga JEFA desde la aplicacion.
CREATE TABLE administrative_status (
    id          uuid PRIMARY KEY,
    name        text        NOT NULL,
    description text,
    enabled     boolean     NOT NULL DEFAULT true,
    created_by  uuid        NOT NULL REFERENCES app_user (id),
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    version     bigint      NOT NULL DEFAULT 1,

    CONSTRAINT administrative_status_name_largo CHECK (char_length(name) <= 150)
);

CREATE UNIQUE INDEX administrative_status_nombre_unico
    ON administrative_status (lower(btrim(name)));

-- Procedimiento administrativo -------------------------------------------------
CREATE TABLE administrative_procedure (
    id                       uuid PRIMARY KEY,
    sequence_number          integer,
    owner_id                 uuid        NOT NULL REFERENCES app_user (id),
    file_number              text        NOT NULL,
    -- Texto libre, sin catalogo: el insumo no define las areas de la institucion
    -- y una lista inventada seria peor que ninguna.
    requesting_area          text,
    request                  text,
    administrative_status_id uuid        REFERENCES administrative_status (id)
                                         ON DELETE RESTRICT,
    -- No se autocompleta con la fecha del dia: un pedido puede registrarse dias
    -- despues de haber llegado, y rellenarla convertiria un descuido en un dato falso.
    received_at              date,
    deadline                 date,
    notes                    text,
    -- Solo visibilidad en el listado corriente. Que el estado sea «Archivado» NO
    -- implica ocultarlo: son dos ejes distintos.
    active                   boolean     NOT NULL DEFAULT true,
    created_at               timestamptz NOT NULL,
    updated_at               timestamptz NOT NULL,
    version                  bigint      NOT NULL DEFAULT 1,

    CONSTRAINT administrative_procedure_numero_largo
        CHECK (char_length(file_number) <= 150),
    CONSTRAINT administrative_procedure_texto_largo CHECK (
        coalesce(char_length(requesting_area), 0) <= 1000 AND
        coalesce(char_length(request), 0)         <= 10000 AND
        coalesce(char_length(notes), 0)           <= 10000)
);

-- Unicidad normalizada, independiente de la de expedientes judiciales: son
-- expedientes de naturaleza distinta y cada serie la lleva su registro.
CREATE UNIQUE INDEX administrative_procedure_numero_unico
    ON administrative_procedure (lower(btrim(file_number)));

CREATE INDEX administrative_procedure_listado
    ON administrative_procedure (active, lower(btrim(file_number)), id);
CREATE INDEX administrative_procedure_responsable
    ON administrative_procedure (owner_id, id);
CREATE INDEX administrative_procedure_estado
    ON administrative_procedure (administrative_status_id, id);
CREATE INDEX administrative_procedure_plazo
    ON administrative_procedure (deadline NULLS LAST, id);

-- Ampliacion de la evidencia ---------------------------------------------------
-- Sustituir la restriccion no toca ninguna fila: las entradas ya escritas cumplen
-- la nueva, que es un superconjunto de la anterior. Tampoco altera privilegios:
-- la aplicacion conserva solo SELECT e INSERT sobre audit_event.
ALTER TABLE audit_event DROP CONSTRAINT audit_event_entidad_valida;

ALTER TABLE audit_event ADD CONSTRAINT audit_event_entidad_valida
    CHECK (entity_type IN (
        'APP_USER', 'JUDICIAL_CASE', 'PROCEDURAL_STATUS', 'NON_WORKING_DAY',
        'CALENDAR_REVIEW', 'ADMINISTRATIVE_PROCEDURE', 'ADMINISTRATIVE_STATUS'));

-- Referencia historica de estados administrativos ------------------------------
-- Tabla propia porque la clave foranea debe apuntar a administrative_status, y
-- una sola tabla no puede referenciar dos catalogos distintos con integridad.
-- Impide borrar un estado que alguna vez se uso, aunque despues se le quitara.
CREATE TABLE procedure_history_status_reference (
    audit_event_id           uuid NOT NULL REFERENCES audit_event (id) ON DELETE RESTRICT,
    administrative_status_id uuid NOT NULL REFERENCES administrative_status (id)
                                  ON DELETE RESTRICT,
    PRIMARY KEY (audit_event_id, administrative_status_id)
);

-- Es evidencia: la aplicacion puede leerla y ampliarla, nunca modificarla.
REVOKE UPDATE, DELETE, TRUNCATE ON procedure_history_status_reference
    FROM sistema_juridico_app;
GRANT SELECT, INSERT ON procedure_history_status_reference TO sistema_juridico_app;
