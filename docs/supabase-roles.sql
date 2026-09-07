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
--
-- El script es idempotente: se puede volver a ejecutar sin romper nada.

-- 1. Los dos roles ------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sistema_juridico_migrator') THEN
        CREATE ROLE sistema_juridico_migrator LOGIN PASSWORD 'CAMBIAR_ESTA_CONTRASENA_1';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sistema_juridico_app') THEN
        CREATE ROLE sistema_juridico_app LOGIN PASSWORD 'CAMBIAR_ESTA_CONTRASENA_2';
    END IF;
END
$$;

-- 2. Hacerse miembro de los roles recien creados ------------------------------
-- En Supabase el usuario del editor SQL NO es superusuario. Para ejecutar
-- «ALTER DEFAULT PRIVILEGES FOR ROLE x» hay que poder asumir ese rol, y para
-- eso hay que ser miembro. Sin estas dos lineas falla con:
--   ERROR 42501: must be able to SET ROLE "sistema_juridico_migrator"
GRANT sistema_juridico_migrator TO current_user;
GRANT sistema_juridico_app      TO current_user;

GRANT CONNECT ON DATABASE postgres TO sistema_juridico_migrator, sistema_juridico_app;

-- 3. El migrador es dueno del esquema; la aplicacion solo lo usa ---------------
-- Esquema propio, NO `public`: la API REST automatica de Supabase expone
-- `public` a quien tenga la clave anonima, que es publica por diseno. Con las
-- tablas ahi, los expedientes serian legibles sin pasar por la aplicacion.
CREATE SCHEMA IF NOT EXISTS sistema_juridico AUTHORIZATION sistema_juridico_migrator;

GRANT USAGE ON SCHEMA sistema_juridico TO sistema_juridico_app;
REVOKE CREATE ON SCHEMA sistema_juridico FROM sistema_juridico_app;

-- 4. Privilegios por defecto sobre lo que cree el migrador --------------------
-- Sin esto habria que repetir un GRANT por cada tabla nueva de cada migracion.
ALTER DEFAULT PRIVILEGES FOR ROLE sistema_juridico_migrator IN SCHEMA sistema_juridico
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sistema_juridico_app;

ALTER DEFAULT PRIVILEGES FOR ROLE sistema_juridico_migrator IN SCHEMA sistema_juridico
    GRANT USAGE, SELECT ON SEQUENCES TO sistema_juridico_app;

-- 5. Que ambos encuentren las tablas sin calificar el esquema -----------------
ALTER ROLE sistema_juridico_migrator SET search_path TO sistema_juridico;
ALTER ROLE sistema_juridico_app      SET search_path TO sistema_juridico;

-- La migracion V7 retira UPDATE, DELETE y TRUNCATE sobre las tablas de
-- evidencia. No hace falta hacer nada mas aqui.

-- Comprobacion: debe devolver dos filas.
SELECT rolname AS rol_creado FROM pg_roles
WHERE rolname IN ('sistema_juridico_migrator', 'sistema_juridico_app');
