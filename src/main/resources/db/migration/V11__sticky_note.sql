-- Recordatorios del panel: notas sueltas, tipo post-it, de cada persona.
--
-- Sirven para lo que no es un pendiente ni un expediente: «llamar al notario»,
-- «pedir copia en mesa de partes». No vencen ni se cuentan en ninguna tarjeta.
-- Duran hasta que su autor las quita; nada las retira solo.
--
-- Son PERSONALES: solo las ve y las quita quien las escribio, tambien frente a
-- la jefa. No son datos del area (seccion 5.1 habla de expedientes y
-- pendientes), sino la libreta de cada uno.
--
-- No escriben historial en audit_event. El principio VII cubre fechas y estados
-- de los registros de trabajo, y lo que la jefa cambia en registros ajenos; aqui
-- no hay ni lo uno ni lo otro. Aun asi, quitar NO borra: pone removed_at y la
-- fila queda, que es el mismo criterio de la actividad manual.
--
-- Las migraciones V1 a V10 estan aplicadas en produccion y NO se modifican.

CREATE TABLE sticky_note (
    id         uuid PRIMARY KEY,
    owner_id   uuid        NOT NULL REFERENCES app_user (id),
    content    text        NOT NULL,
    -- Color del papel. Solo apariencia que elige su autor; no clasifica nada,
    -- por eso es una lista cerrada y no un catalogo.
    color      text        NOT NULL DEFAULT 'YELLOW',
    created_at timestamptz NOT NULL,
    -- Null mientras esta pegada en el panel. Un instante y no un booleano: dice
    -- tambien cuando se quito.
    removed_at timestamptz,

    CONSTRAINT sticky_note_texto_largo CHECK (char_length(content) <= 500),
    CONSTRAINT sticky_note_texto_no_vacio CHECK (btrim(content) <> ''),
    CONSTRAINT sticky_note_color_valido CHECK (
        color IN ('YELLOW', 'PINK', 'GREEN', 'BLUE', 'PURPLE')),
    CONSTRAINT sticky_note_quitada_despues CHECK (removed_at IS NULL OR removed_at >= created_at)
);

-- La unica lectura es «las notas pegadas de esta persona», en orden de alta.
CREATE INDEX sticky_note_pegadas ON sticky_note (owner_id, created_at)
    WHERE removed_at IS NULL;

-- Permisos ---------------------------------------------------------------------
-- Mismo motivo que en la V10: la V1 dejo ALTER DEFAULT PRIVILEGES concediendo
-- DELETE sobre toda tabla nueva, asi que hay que revocarlo de forma explicita.
REVOKE DELETE, TRUNCATE ON sticky_note FROM sistema_juridico_app;
GRANT  SELECT, INSERT, UPDATE ON sticky_note TO sistema_juridico_app;
