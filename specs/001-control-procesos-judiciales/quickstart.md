# Guía de ejecución y validación — Funcionalidad 001

**Estado**: contrato de comandos que debe materializar la implementación. Aún no existen
`pom.xml`, wrapper, migraciones o pruebas; estos comandos no se han ejecutado sobre una
aplicación. Esta guía no contiene código completo ni sustituye a `tasks.md`.

## Requisitos previos

Java 21, PostgreSQL 17 cliente/servidor, Docker o runtime compatible con Testcontainers,
y navegador instalado por Playwright Java. Maven Wrapper será parte del repositorio.
Preparar PostgreSQL local con `docker-compose`, con puerto únicamente en loopback.
No hace falta buzón ni servidor de correo: el sistema no envía nada.

Trabajar desde la raíz del repositorio. Proveer variables del [contrato operativo](contracts/operations.md)
mediante entorno protegido. Para pruebas, base desechable `legal_control_test`; para manual,
`legal_control_local`. Usuario web distinto de migrador, con solo SELECT/INSERT sobre evidencia;
sin UPDATE/DELETE/TRUNCATE ni propiedad.

## Confirmar versión de Supabase

En el SQL Editor del proyecto, o mediante una conexión autorizada de solo lectura, ejecutar:

```sql
SHOW server_version;
SHOW server_version_num;
SELECT version();
```

La versión mayor esperada es 17 (`server_version_num` entre 170000 y 179999). Registrar
la respuesta exacta sin credenciales y fijar el parche/tag de PostgreSQL 17 usado por las
pruebas y herramientas. No dar por instalado PostgreSQL 18.6. Si el proyecto todavía
usa otra versión mayor, revisar compatibilidad antes de ejecutar migraciones; esta guía
no autoriza ni ejecuta una actualización remota.

## Construir y preparar

Comandos previstos que deberán estar disponibles tras implementación:

```sh
./mvnw -version
./mvnw clean verify
./mvnw -Pbrowser-tests verify
./mvnw -DskipTests package
java -jar target/sistema-juridico.jar --spring.profiles.active=local --app.command=migrate
java -jar target/sistema-juridico.jar --spring.profiles.active=local --app.command=bootstrap
java -jar target/sistema-juridico.jar --spring.profiles.active=local
```

`migrate` usa únicamente DB_MIGRATION_*; `bootstrap` crea primera JEFA pendiente bajo
bloqueo e imprime una sola vez el código de la primera JEFA. Los modos CLI terminan sin arrancar HTTP;
la ejecución normal escucha en 127.0.0.1:8080 en local. No pasar contraseñas como flags.
La implementación debe fallar claramente si faltan secretos o se usa credencial runtime
para migrar. `verify` incluye unitarias, MockMvc e integración PostgreSQL; el perfil
`browser-tests` instala/usa navegador en entorno aislado.

Canjear el código impreso por el comando, definir contraseña y entrar. Dar de alta desde
la aplicación dos ABOGADO y una segunda JEFA, anotando el código que muestra cada alta y
canjeándolo. El catálogo
inicia vacío; crear estados «En trámite» y «Concluido» desde JEFA. El calendario sintético
de tests está separado del calendario operativo, que debe proporcionar y revisar el área.

## Recorridos de aceptación

| Recorrido | Acción verificable | Resultado |
| --- | --- | --- |
| Acceso | Login correcto/incorrecto, logout, reabrir navegador y reiniciar JAR | Cuenta activa accede; la sesión sobrevive a reabrir el navegador dentro de 4 h de inactividad y 12 h absolutas, y se pierde al reiniciar el JAR |
| Alta de cuenta | JEFA crea usuario; ABOGADO intenta mismo POST | El código se muestra una sola vez y activa la cuenta; ABOGADO obtiene 403 |
| Restablecimiento | JEFA genera código; canjearlo dos veces; código de cuenta inactiva | Solo un uso válido; error genérico al repetir; no reactiva inactivos |
| Contraseña propia | Titular la cambia con la actual, sin código | Cambia y cierra las demás sesiones |
| Reactivación | Desactivar cuenta con sesión abierta; pedir reactivación | Sesión previa rechazada, misma identidad, contraseña distinta obligatoria |
| Última JEFA | Desactivar única JEFA y desactivar dos simultáneamente | Nunca quedan cero JEFA activas; pendientes no cuentan |
| Proceso mínimo | Registrar solo número de expediente | Alta propia activa, sin exigir partes/fecha ni archivos |
| Duplicados | Mismo número con mayúsculas/espacios externos y altas concurrentes | Un único proceso; sin excepción JEFA, también con registro oculto |
| Permisos | Abogado A modifica caso B por POST directo; JEFA sí modifica | A rechazado; JEFA deja autor y propietario anterior en evidencia |
| Edición | Dos formularios sobre una versión, guardar ambos | Segundo recibe 409, conserva entrada para revisión; sin pérdida silenciosa |
| Lecturas | Abrir listado de 25, fichas e historia, guardar idénticos | Cero nuevas entradas de historial para ambos roles |
| Visibilidad | Combinar active true/false con En trámite/Concluido | Cuatro combinaciones válidas; fecha/estado no cambia al ocultar |
| Catálogo | Crear/editar/borrar estado sin uso y luego uno con uso histórico | Primero borrable; segundo protegido, deshabilitable; historia preservada |
| Cobertura | Revisar año con 0, 1, 4, 5 entradas | Cero bloquea; 1–4 advertencia reconocida; declaración siempre obligatoria |
| Concurrencia anual | Abrir revisión, editar un día desde otra sesión y confirmar | 409; exige revisar calendario actualizado |
| Cambio de año | Mover feriado entre dos años revisados | Invalida ambos años y próximos conteos advierten |
| Fechas | Ejecutar tabla exacta de historia 4 con Clock fijo | Mismos estados y días en ficha/listado, cero diferencias de un día |
| Red/error | Cortar red al guardar, probar 422 y CSRF inválido | Mensaje español, sin falso éxito ni guardado parcial |

Comprobar que no hay rutas de pendientes, asignación, importación, exportación ni visor.
No introducir esos módulos para preparar datos: cada usuario crea sus propios procesos.

## Pruebas que debe proporcionar la implementación

- `DeadlineEvaluatorTest`: 12 filas de spec y comparación con enumerador independiente,
  años bisiestos, fin de semana coincidente, intervalos de varios años, medianoche y
  dispositivos en zona distinta. No usar feriados oficiales reales como fixtures.
- `AccessLifecycleIT`: reloj controlado en 4 h de inactividad y 12 h absolutas, token 1 h/24 h, concurrente,
  regeneración de código, última HEAD, revocación tras commit y reinicio del contexto.
- `JudicialCaseIT`: unicidad normalizada global y concurrente, permisos dentro de
  transacción, versión, no-op y rollback inducido al fallar inserción de audit.
- `CalendarReviewIT`: locks, versiones, 0/1/4/5 entradas, cambio entre años y snapshot
  coherente de lectura. La cantidad no proviene del cliente ni se persiste como total.
- `AuditImmutabilityIT`: intentar UPDATE, DELETE y TRUNCATE con usuario runtime sobre
  evidencia y referencias; todos fallan. No probar con el usuario propietario.
- `WebContractIT`: endpoints y HTML completo/fragmento, CSRF, 401/403/409/422, campos no
  autorizados, ausencia de secretos y crecimiento nulo de audit tras GET.
- `AcceptanceIT`: recorridos en Chromium y Firefox, teclado, foco, errores HTMX visibles,
  navegación atrás después de logout, canje de código y persistencia.

Los nombres son contratos de suites a crear. No se requiere test por cada getter o detalle
reversible de estilo; se priorizan invariantes de acceso, fechas, evidencia y concurrencia.

## Medición de rendimiento

El perfil de pruebas `performance-tests` deberá preparar 5.000 procesos, 50 estados,
500 fechas sintéticas y 50.000 entradas de historial en una base aislada, sin importar Excel.
Debe registrar HAR, tamaños comprimidos, número de peticiones y consultas SQL por flujo.

```sh
./mvnw -Pperformance-tests verify
```

Ejecutar 100 muestras por listado/ficha/formulario/guardado, cinco usuarios simultáneos,
cliente físico y red del [presupuesto](contracts/operations.md). Registrar p50/p95 y equipo.
Caché fría y caliente deben cumplir sus presupuestos; primera carga ≤120 KiB/4 peticiones,
fragmentos de los fixtures de referencia ≤30 KiB/1 petición. Probar por separado los
registros de tamaño máximo conforme al contrato operativo, sin truncar ni omitir datos.
Verificar que el número de consultas no crece entre 1 y
25 filas. La sesión puede mantener metadatos constantes; auditoría no crece en lecturas.
No declarar SC-001 satisfecho basándose solo en tiempos de servidor o un equipo potente.

## Restauración de respaldo

Realizar este ensayo solo contra `legal_control_restore_test`, nunca producción. Preparar
PGSERVICEFILE/PGPASSFILE protegidos para los servicios `legal_control_backup` y
`legal_control_restore_test`; no incluir contraseñas en comandos.

```sh
pg_dump --dbname=service=legal_control_backup --format=custom --file=/tmp/legal-control-test.dump
pg_restore --dbname=service=legal_control_restore_test --no-owner --no-acl /tmp/legal-control-test.dump
```

El destino debe estar vacío y aislado; el propietario del destino aplica privilegios de
runtime conforme a migraciones. Antes de iniciar la aplicación recuperada, el modo
`recovery-sanitize` del JAR deberá limpiar exclusivamente sesiones/tokens/control de abuso
restaurados y rotar configuración de secretos de operación; nunca borrar auditoría.

```sh
java -jar target/sistema-juridico.jar --spring.profiles.active=local --app.command=recovery-sanitize
```

Comprobar mismo número y muestra de usuarios/procesos/estados/calendario/auditoría del
respaldo. Probar tokens y sesiones guardados antes del respaldo: todos deben rechazarse,
incluso los que fueron consumidos/revocados después de tomarlo. Reconstruir códigos
pendientes por flujo autorizado. Registrar tiempo total <4 h y fecha del respaldo <24 h.
Eliminar de forma controlada el dump local de prueba al terminar: contiene datos sensibles
si se ejecutara contra datos reales; fixtures deben ser sintéticos.

## Evidencia para cerrar la funcionalidad

Archivar informes de `verify`, navegador y rendimiento; evidencia de restauración,
matriz de permisos y conteo de cero auditoría por consultas. Adjuntar hash del JAR, versión
Java/PostgreSQL y configuración de prueba sin secretos. SC-001–012 deben tener resultado
observado, no solo casillas marcadas. Este documento queda listo para ejecutarse tras
`$speckit-tasks` y `$speckit-implement`.
