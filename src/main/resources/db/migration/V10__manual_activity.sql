-- Actividad manual: trabajo realizado que nunca fue un pendiente (seccion 33).
--
-- Es la unica tabla que esta funcionalidad crea, y la unica cosa que persiste. La
-- otra mitad de la pantalla «¿que hice hoy?» son los pendientes cumplidos del dia,
-- que ya estan guardados con su completed_at y NO se copian aqui: seria el derivado
-- que prohibe el principio V, y bastaria revertir un cumplimiento para que las dos
-- versiones dejaran de coincidir. La actividad manual, en cambio, no deriva de nada:
-- si no se guarda, no existe.
--
-- Las migraciones V1 a V9 estan aplicadas en produccion y NO se modifican.

CREATE TABLE manual_activity (
    id                   uuid PRIMARY KEY,
    owner_id             uuid        NOT NULL REFERENCES app_user (id),
    -- El dia en que se hizo el trabajo, NO el dia en que se registro. Es un dato
    -- que la persona elige, por eso es date y no timestamptz: no es un instante
    -- que ocurre, y no tiene el problema de zona horaria de completed_at.
    performed_on         date        NOT NULL,
    description          text        NOT NULL,
    -- El tipo es OPCIONAL y tiene dos formas excluyentes (seccion 33): uno del
    -- catalogo, o uno escrito a mano cuando ninguno encaja. Dos columnas y no una
    -- porque hay que poder distinguirlos: con una sola de texto habria que adivinar
    -- por el contenido si «Audiencia» salio del desplegable o lo escribio alguien,
    -- y un recuento futuro los mezclaria sin advertirlo.
    pending_task_type_id uuid        REFERENCES pending_task_type (id) ON DELETE RESTRICT,
    other_type           text,
    -- Retirar es active = false. Nunca se borra: el historial tiene que poder
    -- explicar que hubo (principio VII).
    active               boolean     NOT NULL DEFAULT true,
    created_at           timestamptz NOT NULL,
    updated_at           timestamptz NOT NULL,
    version              bigint      NOT NULL DEFAULT 1,

    CONSTRAINT manual_activity_texto_largo CHECK (
        char_length(description)             <= 10000 AND
        coalesce(char_length(other_type), 0) <= 150),

    CONSTRAINT manual_activity_descripcion_no_vacia CHECK (btrim(description) <> ''),

    -- Con las dos columnas sueltas, nada impediria rellenar ambas y quedaria un
    -- registro cuyo tipo seria ambiguo. Lo impide la base, no el validador.
    CONSTRAINT manual_activity_tipo_excluyente CHECK (
        pending_task_type_id IS NULL OR other_type IS NULL)
);

-- La consulta de la pantalla es «lo de esta persona en este dia», en ese orden.
CREATE INDEX manual_activity_del_dia ON manual_activity (owner_id, performed_on);
-- Para la comprobacion de uso del catalogo antes de borrar un tipo.
CREATE INDEX manual_activity_tipo    ON manual_activity (pending_task_type_id);

-- Ampliacion de la evidencia ---------------------------------------------------
-- El CHECK de entity_type es cerrado y SE SUSTITUYE, no se amplia: los once
-- valores anteriores se repiten literalmente. Sin esto, toda escritura de
-- auditoria de una actividad manual falla la restriccion.
ALTER TABLE audit_event DROP CONSTRAINT audit_event_entidad_valida;

ALTER TABLE audit_event ADD CONSTRAINT audit_event_entidad_valida
    CHECK (entity_type IN (
        'APP_USER', 'JUDICIAL_CASE', 'PROCEDURAL_STATUS', 'NON_WORKING_DAY',
        'CALENDAR_REVIEW', 'ADMINISTRATIVE_PROCEDURE', 'ADMINISTRATIVE_STATUS',
        'PENDING_TASK', 'PENDING_TASK_TYPE', 'PRIORITY', 'PENDING_TASK_STATUS',
        'MANUAL_ACTIVITY'));

-- Permisos ---------------------------------------------------------------------
-- El REVOKE no es redundante: la V1 dejo ALTER DEFAULT PRIVILEGES concediendo
-- SELECT, INSERT, UPDATE y DELETE sobre toda tabla nueva del esquema. Asi que
-- manual_activity nace CON permiso de borrado, y no otorgarlo no lo quita. Es el
-- mismo motivo por el que la V7 revoca sobre audit_event.
REVOKE DELETE, TRUNCATE ON manual_activity FROM sistema_juridico_app;
GRANT  SELECT, INSERT, UPDATE ON manual_activity TO sistema_juridico_app;

-- Deuda de la 005, que esperaba la primera migracion que hiciera falta ---------
-- El rol de la aplicacion no ejecuta migraciones: application.yml deja Flyway en
-- enabled: false y le da credencial propia (DB_MIGRATION_*). Por tanto no escribe
-- nunca en esta tabla, y quitarle el permiso no puede romper el arranque.
REVOKE UPDATE, DELETE, TRUNCATE ON flyway_schema_history FROM sistema_juridico_app;
