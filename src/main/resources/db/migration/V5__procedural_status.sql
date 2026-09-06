-- Catalogo de situacion procesal del expediente. ARRANCA VACIO a proposito:
-- JEFA crea los valores desde la aplicacion (US7). El cliente confirmo que usa
-- «Pendiente de actuacion», «En tramite», «Concluido» y «Archivado», pero no se
-- siembran aqui porque la spec exige que el catalogo vacio sea un estado valido.
--
-- No confundir con los estados de pendientes ni con los de procedimientos
-- administrativos: son catalogos distintos, de funcionalidades posteriores.
CREATE TABLE procedural_status (
    id          uuid PRIMARY KEY,
    name        text        NOT NULL,
    description text,
    enabled     boolean     NOT NULL DEFAULT true,
    created_by  uuid        NOT NULL REFERENCES app_user (id),
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    version     bigint      NOT NULL DEFAULT 1,

    CONSTRAINT procedural_status_name_largo CHECK (char_length(name) <= 150)
);

CREATE UNIQUE INDEX procedural_status_nombre_unico
    ON procedural_status (lower(btrim(name)));

-- RESTRICT: un estado en uso no se puede borrar, solo deshabilitar.
ALTER TABLE judicial_case
    ADD CONSTRAINT judicial_case_estado_fk
    FOREIGN KEY (procedural_status_id) REFERENCES procedural_status (id)
    ON DELETE RESTRICT;
