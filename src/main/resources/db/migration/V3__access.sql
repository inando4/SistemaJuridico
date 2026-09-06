-- Serializacion de los cambios de estado de cuenta. Una sola fila, tomada con
-- SELECT FOR UPDATE: impide que dos desactivaciones simultaneas dejen al equipo
-- sin ninguna JEFA activa. Un COUNT previo no basta.
CREATE TABLE access_guard (
    id smallint PRIMARY KEY,
    CONSTRAINT access_guard_fila_unica CHECK (id = 1)
);
INSERT INTO access_guard (id) VALUES (1);

-- Codigos de un solo uso que JEFA entrega en mano. El sistema no envia correo:
-- el codigo se muestra una vez en pantalla y aqui solo vive su digest.
CREATE TABLE access_token (
    id           uuid PRIMARY KEY,
    user_id      uuid        NOT NULL REFERENCES app_user (id),
    token_hash   bytea       NOT NULL UNIQUE,
    purpose      text        NOT NULL,
    issued_at    timestamptz NOT NULL,
    consumed_at  timestamptz,
    revoked_at   timestamptz,
    -- Siempre HEAD: es quien genera y entrega el codigo.
    issued_by    uuid        NOT NULL REFERENCES app_user (id),
    auth_version bigint      NOT NULL,

    CONSTRAINT access_token_purpose_valido CHECK (purpose IN
        ('ACTIVATION', 'RESET', 'REACTIVATION'))
);

-- Para revocar los codigos vivos de un usuario y proposito al generar otro.
CREATE INDEX access_token_usuario_proposito ON access_token (user_id, purpose)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;

-- Registro tecnico de intentos, para los limites de FR-026. Sin FK a usuario:
-- la respuesta debe ser indistinta ante correos que no existen. Las claves van
-- anonimizadas con HMAC.
CREATE TABLE auth_attempt (
    id          uuid PRIMARY KEY,
    kind        text        NOT NULL,
    account_key bytea,
    origin_key  bytea,
    occurred_at timestamptz NOT NULL,

    CONSTRAINT auth_attempt_kind_valido CHECK (kind IN
        ('LOGIN_FAILURE', 'CODE_REDEEM_FAILURE', 'CODE_ISSUE'))
);

CREATE INDEX auth_attempt_ventana ON auth_attempt (kind, occurred_at DESC);
CREATE INDEX auth_attempt_cuenta  ON auth_attempt (kind, account_key, occurred_at DESC);
CREATE INDEX auth_attempt_origen  ON auth_attempt (kind, origin_key, occurred_at DESC);
