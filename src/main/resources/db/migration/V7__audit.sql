-- Evidencia inmutable. Solo modificaciones efectivas: consultar no escribe nada
-- y guardar sin cambios tampoco (constitucion 4.0.1, principio VII).
CREATE TABLE audit_event (
    id            uuid PRIMARY KEY,
    entity_type   text        NOT NULL,
    entity_id     uuid        NOT NULL,
    action        text        NOT NULL,
    -- Quien ejecuto el cambio, que no siempre es el responsable del registro:
    -- JEFA puede modificar expedientes ajenos.
    actor_id      uuid        NOT NULL REFERENCES app_user (id),
    owner_id      uuid        NOT NULL REFERENCES app_user (id),
    occurred_at   timestamptz NOT NULL,
    before_values jsonb,
    after_values  jsonb,
    -- Motivo opcional salvo donde la spec lo exija.
    reason        text,

    CONSTRAINT audit_event_entidad_valida CHECK (entity_type IN
        ('APP_USER', 'JUDICIAL_CASE', 'PROCEDURAL_STATUS', 'NON_WORKING_DAY', 'CALENDAR_REVIEW')),
    CONSTRAINT audit_event_reason_largo CHECK (reason IS NULL OR char_length(reason) <= 10000)
);

CREATE INDEX audit_event_historial
    ON audit_event (entity_type, entity_id, occurred_at DESC, id);

-- Impide eliminar un estado procesal que alguna vez se uso en un expediente,
-- aunque despues se le quitara. Sin ON DELETE CASCADE hacia la evidencia.
CREATE TABLE case_history_status_reference (
    audit_event_id       uuid NOT NULL REFERENCES audit_event (id) ON DELETE RESTRICT,
    procedural_status_id uuid NOT NULL REFERENCES procedural_status (id) ON DELETE RESTRICT,
    PRIMARY KEY (audit_event_id, procedural_status_id)
);

-- La aplicacion puede leer e insertar evidencia, pero NUNCA modificarla ni
-- borrarla. Esto no depende de que el codigo se porte bien: lo impide la base.
REVOKE UPDATE, DELETE, TRUNCATE ON audit_event                   FROM sistema_juridico_app;
REVOKE UPDATE, DELETE, TRUNCATE ON case_history_status_reference FROM sistema_juridico_app;
GRANT  SELECT, INSERT             ON audit_event                   TO sistema_juridico_app;
GRANT  SELECT, INSERT             ON case_history_status_reference TO sistema_juridico_app;
