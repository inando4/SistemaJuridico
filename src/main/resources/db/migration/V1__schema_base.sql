-- Base del esquema y separacion de privilegios.
--
-- El rol de migracion (sistema_juridico_migrator) es dueño de los objetos.
-- El rol de aplicacion (sistema_juridico_app) NUNCA puede alterar el esquema:
-- solo opera sobre los datos. Los roles ya existen: en local los crea
-- docker/initdb/01-roles.sql y en produccion se crean en el proveedor.
--
-- Constitucion 4.0.1, principio III.

-- Privilegios por defecto: todo objeto que cree el migrador de aqui en adelante
-- queda utilizable por la aplicacion sin tener que repetir GRANT por tabla.
ALTER DEFAULT PRIVILEGES FOR ROLE sistema_juridico_migrator IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sistema_juridico_app;

ALTER DEFAULT PRIVILEGES FOR ROLE sistema_juridico_migrator IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO sistema_juridico_app;

-- La aplicacion no crea objetos.
REVOKE CREATE ON SCHEMA public FROM sistema_juridico_app;
GRANT USAGE ON SCHEMA public TO sistema_juridico_app;
