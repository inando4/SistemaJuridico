# Guía de validación — Funcionalidad 002

Cómo comprobar que los procedimientos administrativos funcionan. Presupone la 001 desplegada:
esta funcionalidad **no** trae acceso, calendario ni auditoría propios.

## Requisitos previos

PostgreSQL local levantado y migraciones al día:

```sh
docker compose up -d
./mvnw -DskipTests package
java -jar target/sistema-juridico.jar --spring.profiles.active=local --app.command=migrate
```

Debe informar de las migraciones nuevas aplicadas. Las V1 a V7 ya estarán, y **no se
reejecutan**: Flyway las reconoce por su suma de verificación.

## Suite automática

```sh
./mvnw verify
```

Ejecuta unitarias y de integración, estas últimas contra PostgreSQL 17 real por Testcontainers.
Prohibido sustituirlo por una base en memoria: no reproduce restricciones ni concurrencia, que
es justo lo que estas pruebas comprueban.

## Recorrido manual

Con sesión iniciada y el calendario del año cargado y **confirmado**:

1. `/estados-administrativos` — crear los cinco estados del área. El catálogo arranca vacío.
2. `/administrativos/nuevo` — registrar indicando solo el número de expediente. Debe guardarse.
3. Repetir el mismo número: debe rechazarse, aunque el anterior esté oculto o sea de otra
   persona.
4. Registrar uno completo con área solicitante, pedido, fecha de recepción y fecha límite
   futura. La ficha debe mostrar los días hábiles restantes, no un aviso.
5. Registrar uno con fecha límite **anterior** a la de recepción: debe guardarse **y advertir**,
   sin corregir ninguna de las dos fechas.
6. `/administrativos` — comprobar que los filtros se conservan al paginar.
7. Editar desde otra cuenta de ABOGADO: debe devolver 403 y dejar el registro intacto.
8. Editar como JEFA: debe permitirse y el historial debe distinguir autor de responsable.
9. `/administrativos/{id}/historial` — consultarlo no debe generar entradas nuevas.

## Comprobaciones que no se ven en pantalla

**Un mismo número puede existir como judicial y como administrativo.** Registrar el mismo
número en ambos registros debe funcionar: las series son independientes.

**El calendario es compartido.** Añadir un feriado desde `/non-working-days` debe cambiar el
conteo de los procedimientos administrativos, no solo el de los judiciales. Si no cambia, hay
dos calendarios donde debería haber uno.

**La evidencia sigue siendo inmutable tras ampliar la restricción.** Desde el editor SQL:

```sql
SET ROLE sistema_juridico_app;
DELETE FROM sistema_juridico.audit_event;
RESET ROLE;
```

Debe fallar con `permission denied`. La migración V8 sustituye una restricción `CHECK`, no
altera privilegios; si esta comprobación empezara a pasar, algo se rompió.

**Ningún valor derivado persistido.** Ninguna columna nueva debe llamarse `days_remaining`,
`is_overdue` ni equivalente. Se comprueba automáticamente sobre `information_schema`.

## Antes de desplegar

Aplicar las migraciones nuevas contra Supabase con la credencial de **migración**, no con la de
la aplicación, y solo después redesplegar en Render. El orden importa: si la aplicación arranca
antes de migrar, encontrará tablas que no existen.
