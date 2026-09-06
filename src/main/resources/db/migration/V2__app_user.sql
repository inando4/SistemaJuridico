-- Cuentas del equipo. Dos roles: LAWYER y HEAD (ABOGADO y JEFA en pantalla).
-- El permiso se deriva de status = 'ACTIVE'; no hay booleano `active` paralelo.
CREATE TABLE app_user (
    id            uuid PRIMARY KEY,
    name          text        NOT NULL,
    email         text        NOT NULL,
    role          text        NOT NULL,
    status        text        NOT NULL,
    -- Nulo solo antes de que el titular establezca su primera contrasena.
    password_hash text,
    auth_version  bigint      NOT NULL DEFAULT 1,
    version       bigint      NOT NULL DEFAULT 1,
    -- La primera JEFA se referencia a si misma durante el bootstrap.
    created_by    uuid        NOT NULL REFERENCES app_user (id),
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,

    CONSTRAINT app_user_role_valido   CHECK (role IN ('LAWYER', 'HEAD')),
    CONSTRAINT app_user_status_valido CHECK (status IN
        ('PENDING_ACTIVATION', 'ACTIVE', 'INACTIVE', 'PENDING_REACTIVATION')),
    CONSTRAINT app_user_name_largo    CHECK (char_length(name) <= 150),
    CONSTRAINT app_user_email_largo   CHECK (char_length(email) <= 254)
);

-- Unicidad global del correo, tambien frente a cuentas inactivas. Indice de
-- expresion en vez de columna canonica derivada persistida.
CREATE UNIQUE INDEX app_user_email_unico ON app_user (lower(btrim(email)));

-- Para contar JEFA activas al desactivar una cuenta.
CREATE INDEX app_user_rol_estado ON app_user (role, status);
