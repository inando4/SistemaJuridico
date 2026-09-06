# Investigación y decisiones técnicas — Funcionalidad 001

**Fecha**: 2026-09-06. **Base**: [spec](spec.md) y constitución 4.0.0.
Investigación completada antes del diseño. Las decisiones siguientes son propuestas de
implementación del alcance aprobado; las referencias validan capacidades técnicas.

## 1. Aplicación y dependencias

**Decisión**: Java 21, Spring Boot 4.1.1, Maven Wrapper 3.9.11, PostgreSQL 17 y
HTMX 2.0.10 distribuido localmente. El BOM de Boot administra Security,
Thymeleaf, JDBC, Validation, Mail, Flyway y dependencias de prueba. Fijar las versiones en
el build; no usar `latest`. Añadir soporte PostgreSQL de Flyway y Bouncy Castle para Argon2.

**Motivo**: Una sola aplicación MVC empaquetada en JAR, sin build de frontend ni servicios
adicionales de aplicación. Java 21 está dentro del intervalo compatible documentado de Boot.
Los patches de librerías gobernadas por el BOM no se sobreescriben por separado.
[Compatibilidad Boot](https://docs.spring.io/spring-boot/system-requirements.html),
[BOM oficial](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html).
Versiones auxiliares fijadas: [Maven 3.9.11](https://maven.apache.org/docs/3.9.11/release-notes.html),
[PostgreSQL 17 en Supabase](https://supabase.com/docs/guides/platform/upgrading) y
[Playwright Java 1.58](https://playwright.dev/java/docs/release-notes#version-158).

**Alternativas**: SPA/API separada contradice constitución; imágenes nativas añaden trabajo
sin evidencia de necesidad; múltiples módulos Maven no aportan valor al alcance inicial.

### Compatibilidad con Supabase verificada

El objetivo se corrige de PostgreSQL 18.6 a **PostgreSQL 17**, la versión mayor documentada
por Supabase para nuevos proyectos y actualizaciones. El parche/bundle depende del proyecto:
no se atribuye a Supabase un parche upstream concreto ni se asegura que todos sus proyectos
existentes tengan la misma versión. [Actualizaciones Supabase](https://supabase.com/docs/guides/platform/upgrading),
[versiones de proyectos](https://supabase.com/docs/guides/platform/temporary-access).

Antes de fijar la imagen de Testcontainers y los binarios de validación, ejecutar
`SHOW server_version;` y `SHOW server_version_num;` en el proyecto de destino. Mantener
PostgreSQL 17 en desarrollo y pruebas, fijando un tag/patch explícito en la implementación
tras esa comprobación. La documentación consultada no acredita disponibilidad de 18.6
como opción del servicio. No se accedió a un proyecto Supabase de esta cuenta.

El modelo usa date/timestamptz, índices únicos de expresión, jsonb, bloqueos de filas y
transacciones disponibles en PostgreSQL 17; no depende de funciones exclusivas de 18.
Supabase se utiliza aquí como referencia de compatibilidad de PostgreSQL, sin sustituir
Spring Security por Supabase Auth ni introducir Storage, Realtime o Data API en el producto.

## 2. Persistencia explícita y dominio por funcionalidades

**Decisión**: `JdbcClient` y SQL parametrizado, repositorios pequeños por funcionalidad,
servicios transaccionales y DTO de formulario separados de filas persistidas. Flyway con
credencial propietaria fuera del proceso web; usuario de aplicación con privilegios mínimos.

**Motivo**: Facilita medir viajes a base de datos, evita cargas N+1 y hace visibles los bloqueos
y restricciones. La aplicación no depende de una API de acceso remoto.
[JdbcClient](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html).

**Alternativas**: JPA es válido pero su seguimiento automático y relaciones diferidas complican
el presupuesto de consultas; un repositorio genérico de entidades no ayuda a permisos concretos.

## 3. Sesiones persistentes con revocación

**Decisión**: Spring Security de formulario con sesión en memoria del contenedor embebido.
Sin Spring Session ni almacén en base de datos. Cookie `HttpOnly`, `Secure`,
`SameSite=Lax`, `Path=/`, sin `Domain`, de sesión (no persistente en disco del navegador).
Inactividad 4 h por `server.servlet.session.timeout`. Guardar `authenticated_at` y
`auth_version` al ingresar; rechazar al cumplir 12 h desde ese instante. La actividad no
reinicia el límite absoluto. Reiniciar el JAR cierra las sesiones y exige nuevo ingreso.
Sin listados ni entidades en sesión.

**Motivo**: El timeout de inactividad no implementa por sí solo un vencimiento absoluto.
En cada petición se comprueba una vez la cuenta activa y su versión de autorización;
las escrituras las revalidan dentro de su transacción. Cambiar contraseña o desactivar
incrementa `auth_version` junto con el cambio y la auditoría. No hay borrado físico de sesiones que coordinar: al no persistirse, la revocación es
efectiva en la siguiente petición sin tocar la base. Eso elimina también la escritura de
metadatos de sesión por petición, que con la base en Supabase costaría un viaje WAN.
[Configuración JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html),
[API de sesión y cookie](https://docs.spring.io/spring-session/reference/api.html).

**Alternativas**: JWT o remember-me separado complican revocación o reinician autenticación;
sesión en memoria se pierde al reiniciar. No se desarrolla un repositorio propio sin medir.

## 4. Usuarios, enlaces y última JEFA

**Decisión**: Argon2 mediante `DelegatingPasswordEncoder`, sin truncar contraseñas. Medir
parámetros de hash para validar presupuesto de acceso y servidor; no usar bcrypt para
contraseñas de hasta 128 caracteres. Tokens aleatorios de 32 bytes, solo digest SHA-256
persistido, propósito y `issued_at`; expiración calculada según propósito (24 h o 1 h).
GET solo presenta el formulario; POST consume una vez. URL pública configurada, nunca
construida desde una cabecera Host no validada.

**Motivo**: Cuentas/tokens se bloquean para consumo, reenvío y desactivación; el cambio de
credencial, consumo, revocación lógica y auditoría ocurren juntos. La última JEFA se protege
con una fila global de bloqueo antes de contar cuentas activas. No hay contador persistido.
[Password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html),
[recuperación OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html).

**Alternativas**: Token reutilizable o contraseña enviada por correo exponen acceso;
contar JEFA sin bloqueo permite que dos desactivaciones concurrentes eliminen todas.

## 5. Correo y controles de abuso

**Decisión**: SMTP con TLS y emisor configurado. Después del commit, enviar mediante un
executor acotado dentro del JAR (2 trabajadores, cola máxima 20); timeout de conexión/lectura
5 s. No bloquear la respuesta del formulario esperando al proveedor. No hay reintento
automático ni token en disco en texto claro; el reenvío manual genera un token nuevo.
Si el proceso cae después del commit, la cuenta sigue pendiente y JEFA puede reenviar.
La respuesta pública de recuperación permanece genérica, incluso ante fallo de entrega.

**Motivo**: El equipo es pequeño; no requiere broker ni almacenamiento de secretos en una
cola durable. Rechazar/sobrecargar la cola deja el estado pendiente, un aviso interno de
entrega y la posibilidad de reenvío. La interfaz no afirma que el correo llegó.
[Mail Spring Boot](https://docs.spring.io/spring-boot/reference/io/email.html).

**Alternativas**: SMTP dentro de la transacción prolonga bloqueos; una cola durable con tokens
cifrados se difiere, pues el reenvío explícito satisface el flujo y simplifica operación.

**Límites**: Registrar intentos de autenticación y emisión en una tabla técnica, con fecha,
propósito y claves HMAC de correo/origen; contar en consulta, sin contadores guardados.
Ventanas móviles: 1 emisión/minuto y 5/hora por correo y por origen; 10 fallos/15 minutos
para el par cuenta-origen. Serializar comprobación y registro de intentos por clave.
Estos eventos de protección no son historial de expedientes ni auditoría de lecturas;
el GET de listado no los genera. Borrar eventos técnicos mayores de 24 h.

## 6. Plazos exactos y cobertura

**Decisión**: `LocalDate`/`date` para fechas civiles, `Instant`/`timestamptz` para momentos.
Un `DeadlineEvaluator` devuelve estado, días, aviso no hábil y años faltantes. Capturar una
sola fecha de Arequipa por consulta. Contar `(today, deadline]`; no trasladar el límite.
Para intervalos largos contar semanas completas y resto, restando solo feriados de lunes
a viernes del intervalo. Validar ese algoritmo contra un enumerador simple independiente.
[LocalDate](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/LocalDate.html).

**Motivo**: Evita desfases por UTC y diferencias entre pantallas. Todas las fechas e
indicadores se calculan al consultar; no se guardan `remaining_days`, `is_overdue`,
`holiday_count` ni `is_covered`. La revisión anual guarda declaración humana y revisión
observada; su vigencia es una comparación. Lecturas con snapshot `REPEATABLE READ` cargan
calendario una vez por página. Cambios/confirmación bloquean la misma fila anual; mover
una fecha bloquea ambos años en orden ascendente.
[Aislamiento PostgreSQL](https://www.postgresql.org/docs/17/transaction-iso.html),
[bloqueos](https://www.postgresql.org/docs/17/explicit-locking.html).

**Alternativas**: Milisegundos/24 h, fórmulas por pantalla y existencia de un solo feriado
como cobertura no satisfacen los requisitos. Reglas jurídicas por materia están fuera.

## 7. Duplicados, historial y catálogo

**Decisión**: Índice único global de expediente por `lower(btrim(case_number))`, manteniendo
el texto original significativo. No usar un índice parcial para activos. Validar en la
aplicación y traducir también la violación de restricción ante carreras concurrentes.
Los identificadores numéricos siguen siendo texto; ceros, separadores y espacios internos
no se eliminan. Misma normalización SQL para búsqueda de coincidencia exacta.
[Índices de expresión](https://www.postgresql.org/docs/17/indexes-expressional.html).

**Motivo**: El historial se inserta con el cambio, con snapshots de nombres/valores.
El usuario PostgreSQL web solo puede leer/insertar evidencia, no actualizar/borrar/truncar;
no es propietario de tablas. Estados referenciados por procesos o historia de procesos
usan FK restrictiva; la auditoría del propio catálogo conserva identidad sin FK al estado
eliminable. Se distingue modificación de datos de un guardado idéntico, que no altera
historial, `updated_at` ni versión.
[Restricciones](https://www.postgresql.org/docs/17/ddl-constraints.html),
[privilegios](https://www.postgresql.org/docs/17/sql-grant.html).

**Alternativas**: Cascada al borrar catálogos destruye evidencia; auditoría asíncrona deja
cambios sin historia; auditar lecturas contradice constitución 4.0.0.

## 8. HTML y rendimiento

**Decisión**: Thymeleaf devuelve página completa en navegación normal y fragmentos con
`HX-Request`. Formularios estándar GET/POST, CSRF oculto y mejora HTMX; búsquedas al enviar
el formulario, no por pulsación. CSS y fuentes del sistema, sin CDN, imágenes de adorno,
polling ni precarga. Configuración HTMX declarativa para intercambiar errores 409/422.
[HTMX](https://htmx.org/docs/), [CSRF Spring Security](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

**Motivo**: Un guardado HTMX devuelve el fragmento actualizado en la misma respuesta.
La degradación sin JS usa POST/redirect/GET. Sin caché de páginas privadas ni historial
HTMX en localStorage. Una pieza pequeña de JS externo gestiona foco, fallo de red y
comprobación al volver desde bfcache; no contiene lógica de plazos ni permisos.

**Alternativas**: API JSON separada, envío de todo el listado tras cada cambio o gráficos
iniciales añaden viajes y tamaño sin servir al objetivo.

## 9. Operación y respaldo

**Decisión**: JAR en Render con Java 21; PostgreSQL en Supabase con TLS y credenciales
fuera del repositorio, conforme al principio III de la constitución 4.0.0. HTTPS
termina en proxy de infraestructura o en el conector del JAR, sin segundo backend.
Respaldos diarios 02:00 America/Lima, retención 30 diarios y 12 mensuales; copia cifrada
fuera del host, restauración mensual y antes de cambios destructivos de esquema.
Objetivos iniciales: RPO 24 h y RTO 4 h; JEFA supervisa evidencias y un operador técnico
asignado ejecuta restauración. El operador no es un tercer rol del producto.

**Motivo**: `pg_dump` produce un snapshot consistente, pero solo una restauración valida
recuperabilidad. Restaurar sin sesiones/tokens/eventos técnicos; desactivar la red mientras
se eliminan esos secretos y rotar claves antes de abrir acceso, evitando resucitar enlaces
consumidos después del respaldo. Conservar cuentas e historial, sin editar evidencia.
[Respaldo PostgreSQL](https://www.postgresql.org/docs/17/backup-dump.html).

**Alternativas**: Respaldo sin ensayo o recuperación con sesiones antiguas incumple aceptación.
PITR continuo se difiere; RPO menor exigiría otra decisión operativa.

## Resultado

No quedan decisiones técnicas abiertas para generar tareas. Credenciales SMTP, dominio,
operador nominal y calendario real son insumos de despliegue; el diseño permite desarrollo
y validación local con sustitutos. No se han enviado correos, desplegado servicios ni
implementado estas decisiones durante la planificación.
