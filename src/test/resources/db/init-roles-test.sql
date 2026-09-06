-- Crea los mismos roles separados que produccion dentro del contenedor de
-- pruebas. Sin esto, las migraciones que otorgan y revocan privilegios fallan,
-- y la prueba no comprobaria lo que de verdad importa: que la aplicacion no
-- pueda alterar el esquema ni tocar la evidencia.
CREATE ROLE sistema_juridico_migrator LOGIN PASSWORD 'test';
CREATE ROLE sistema_juridico_app      LOGIN PASSWORD 'test';
GRANT USAGE ON SCHEMA public TO sistema_juridico_app;
