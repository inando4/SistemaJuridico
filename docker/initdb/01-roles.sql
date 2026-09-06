-- Roles separados para desarrollo local, con la misma forma que produccion:
-- el usuario de la aplicacion NO puede modificar el esquema; solo el de
-- migracion. Los privilegios sobre los objetos los otorga la migracion V1.
--
-- Solo para el contenedor local. En produccion los roles se crean en Supabase
-- y sus credenciales viven en variables de entorno.
CREATE ROLE sistema_juridico_migrator LOGIN PASSWORD 'local_solo_desarrollo';
CREATE ROLE sistema_juridico_app      LOGIN PASSWORD 'local_solo_desarrollo';

GRANT CONNECT ON DATABASE sistema_juridico TO sistema_juridico_migrator, sistema_juridico_app;

-- El migrador es dueño del esquema public; la aplicacion solo lo usa.
ALTER SCHEMA public OWNER TO sistema_juridico_migrator;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO sistema_juridico_app;
