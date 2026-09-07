-- Roles separados en Supabase. Ejecutar UNA VEZ desde el editor SQL del
-- proyecto, antes del primer despliegue.
--
-- Por que dos roles y no uno: el usuario con el que corre la aplicacion NO debe
-- poder alterar el esquema ni modificar el historial. Con un solo usuario, un
-- fallo de la aplicacion o una inyeccion tendrian permiso para borrar la
-- evidencia de quien hizo que. Esto lo impide la base, no el codigo.
--
-- Sustituya las dos contrasenas por unas generadas al azar y guardelas en el
-- gestor de contrasenas. La de migracion NO se pone en Render: se usa a mano
-- desde el equipo de quien despliega.

-- 1. Los dos roles ------------------------------------------------------------
CREATE ROLE sistema_juridico_migrator LOGIN PASSWORD 'CAMBIAR_ESTA_CONTRASENA_1';
CREATE ROLE sistema_juridico_app      LOGIN PASSWORD 'CAMBIAR_ESTA_CONTRASENA_2';

GRANT CONNECT ON DATABASE postgres TO sistema_juridico_migrator, sistema_juridico_app;

-- 2. El migrador es dueno del esquema; la aplicacion solo lo usa ---------------
CREATE SCHEMA IF NOT EXISTS sistema_juridico AUTHORIZATION sistema_juridico_migrator;

GRANT USAGE ON SCHEMA sistema_juridico TO sistema_juridico_app;
REVOKE CREATE ON SCHEMA sistema_juridico FROM sistema_juridico_app;

-- 3. Privilegios por defecto sobre lo que cree el migrador --------------------
-- Sin esto habria que repetir un GRANT por cada tabla nueva de cada migracion.
ALTER DEFAULT PRIVILEGES FOR ROLE sistema_juridico_migrator IN SCHEMA sistema_juridico
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sistema_juridico_app;

ALTER DEFAULT PRIVILEGES FOR ROLE sistema_juridico_migrator IN SCHEMA sistema_juridico
    GRANT USAGE, SELECT ON SEQUENCES TO sistema_juridico_app;

-- 4. Que ambos encuentren las tablas sin calificar el esquema -----------------
ALTER ROLE sistema_juridico_migrator SET search_path TO sistema_juridico;
ALTER ROLE sistema_juridico_app      SET search_path TO sistema_juridico;

-- La migracion V7 retira UPDATE, DELETE y TRUNCATE sobre las tablas de
-- evidencia. No hace falta hacer nada mas aqui.
