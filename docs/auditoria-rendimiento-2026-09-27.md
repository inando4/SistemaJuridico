# Auditoría de velocidad y optimización

**Sistema Jurídico · 27 de septiembre de 2026, hora de Perú · código `80d8033`**

> **Seguimiento:** los hallazgos fueron corregidos y verificados. Consulta [las optimizaciones y sus resultados](optimizacion-rendimiento-2026-09-27.md). Este documento conserva la medición inicial de `80d8033`.

## Resultado

La aplicación tiene un coste de servidor bajo con el volumen examinado. Las mejoras más claras están en la navegación: los recursos estáticos no se conservan en caché, su descarga añade consultas de autorización y la hoja Estándar se descubre tarde. También hay una debilidad en las pruebas que permite confundir una redirección al login con una pantalla rápida.

Se identificaron **cinco hallazgos prioritarios** y una oportunidad adicional en el calendario. Esta entrega contiene la auditoría y sus evidencias; las optimizaciones propuestas no están implementadas.

## Alcance y método

- **Producción pública:** `https://sistemajuridico-f1m4.onrender.com`; 24 lecturas HTTP de `/ping`, `/login` y seis recursos, más 12 cargas medidas de login/activación en Chromium. Las muestras HTTP comenzaron alrededor de las 22:30 de Perú. No se usó una sesión privada de producción ni se modificaron sus datos.
- **Aplicación autenticada local:** PostgreSQL 17.11 independiente, 5.000 expedientes, 5.000 procedimientos y 5.000 pendientes sintéticos. Cuenta de jefatura sintética. Caché de Thymeleaf activada y pool JDBC de cinco conexiones.
- **Navegador:** Playwright MCP `0.0.80`, Playwright `1.63.0-alpha-2026-08-31`, Chromium `151.0.7922.34`. 32 cargas locales, ambos modos, anchuras de 1.440 y 390 px. El perfil móvil usa CPU ×4 y red de 2 Mbps/150 ms emulada mediante CDP; no sustituye un teléfono real.
- **Servidor por HTTP:** 16 rutas × 20 muestras, después de tres calentamientos por ruta; 48 muestras con 50 ms añadidos por consulta SQL y 100 peticiones repartidas entre cinco sesiones concurrentes. **468 muestras principales**, comprobando estados HTTP, además de preparación y reproducción negativa.
- **Interacción:** seis acciones sobre el selector de modo y los filtros con CPU ×4. Se observaron eventos del navegador, no INP de usuarios reales.
- **Código y SQL:** consultas de relaciones con Graphify, revisión del recorrido de recursos/sesión/controladores y cuatro planes `EXPLAIN ANALYZE` en la base sintética. Se aplicaron los criterios de rendimiento de Impeccable.
- **Pruebas existentes:** 61 casos de rendimiento/presupuestos de consultas terminaron sin fallos. Se repitieron otros siete casos, ya incluidos en esos 61, para estudiar el falso positivo descrito en P03.

El Java local es **OpenJDK 25.0.4.1**; el Dockerfile de producción utiliza Java 21. El equipo local no reproduce la cuota de CPU/memoria de Render. Los tiempos locales acreditan este laboratorio, no un SLA de producción.

## Mediciones

### Respuestas HTTP del servidor local, con sesión válida

Todos los casos de esta tabla devolvieron **HTTP 200**. P95 significa que al menos el 95 % de las muestras tardó ese tiempo o menos. Incluye el transporte local y la recepción del cuerpo; no incluye descargar CSS/JS ni dibujar la pantalla.

| Pantalla | P50 | P95 | Consultas del documento |
|---|---:|---:|---:|
| Panel | 11,47 ms | **13,29 ms** | 4 |
| Alertas | 22,02 ms | **29,50 ms** | 4 |
| Expedientes judiciales | 20,56 ms | **25,24 ms** | 6 |
| Procedimientos administrativos | 19,81 ms | **26,44 ms** | 6 |
| Pendientes | 29,07 ms | **52,48 ms** | 8 |
| Pendientes, página 150 | 46,51 ms | **60,38 ms** | 8 |
| Búsqueda con coincidencias | 42,12 ms | **50,82 ms** | 4 |
| Búsqueda sin coincidencias | 48,52 ms | **68,50 ms** | 4 |
| Calendario mensual | 154,69 ms | **212,35 ms** | 5 |
| Calendario semanal | 52,60 ms | **82,86 ms** | 5 |
| Equipo | 11,82 ms | **12,95 ms** | 4 |

[Muestras HTTP y rutas adicionales](auditoria-rendimiento-2026-09-27/backend-http.json) · [Consultas por ruta](auditoria-rendimiento-2026-09-27/consultas-por-ruta.json).

### Carga visual de las pantallas internas

Ocho cargas por combinación de modo/anchura, sobre panel, listados, búsqueda, calendario y ficha judicial. Se informa **LCP**, el momento en que se dibuja el contenido visible de mayor tamaño. Se trata de muestras de laboratorio, no de percentiles de usuarios reales.

| Entorno local | LCP mediano | Intervalo observado | CLS máximo |
|---|---:|---:|---:|
| Estándar, escritorio | 152 ms | 76–280 ms | 0 |
| Rendimiento, escritorio | 88 ms | 40–200 ms | 0 |
| Estándar, móvil emulado | 684 ms | 632–748 ms | 0,0445 |
| Rendimiento, móvil emulado | 438 ms | 392–472 ms | 0,0038 |

**0 excepciones JavaScript** en las 44 cargas principales, públicas y locales. En el móvil local Estándar aparecieron dos tareas largas, de hasta 57 ms; no se observó un bloqueo sostenido. Las seis interacciones medidas duraron **16–128 ms**. La instrumentación no permite afirmar un INP real de producción.

El laboratorio local sirve recursos sin la compresión de Cloudflare/Render; por ello, sus tiempos con red limitada no deben extrapolarse literalmente a producción. [Mediciones de navegación](auditoria-rendimiento-2026-09-27/sj_perf_local.json) · [Interacciones](auditoria-rendimiento-2026-09-27/sj_perf_interacciones.json).

### Producción pública

| Perfil | Muestras | LCP mediano | Intervalo observado |
|---|---:|---:|---:|
| Estándar, escritorio | 3 | 1.272 ms | 848–1.644 ms |
| Rendimiento, escritorio | 3 | 696 ms | 540–2.532 ms |
| Estándar, móvil emulado | 3 | 712 ms | 704–780 ms |
| Rendimiento, móvil emulado | 3 | 572 ms | 536–704 ms |

Estas tandas se ejecutaron en momentos distintos y la red mostró variación. **No demuestran que el móvil sea más rápido que el escritorio.** No se calculó un P95 de producción con tres observaciones por perfil.

- `/ping`: 384–404 ms totales desde este equipo, con una conexión nueva de curl por muestra. Ese tiempo incorpora DNS, conexión, TLS y red; no es tiempo de CPU del servidor.
- `/login`: 360–390 ms en las tres muestras iniciales de curl.
- HTTP/2 y **Brotli ya funcionan** en la entrega pública. No se identificó falta de compresión en producción.
- El JavaScript desplegado ya contiene la corrección `data-presentacion-cargando`. En las seis cargas públicas Estándar la hoja terminó de cargar antes de mostrar el cuerpo. Las mediciones no reprodujeron el destello anterior.
- No hubo reutilización de los recursos estáticos desde caché en las 12 cargas públicas medidas.

[Tiempos y cabeceras HTTP](auditoria-rendimiento-2026-09-27/produccion-http.json) · [Navegador público y secuencia de recursos](auditoria-rendimiento-2026-09-27/sj_perf_prod.json).

## Hallazgos prioritarios

### P01 · Alta · CSS y JavaScript se descargan de nuevo al navegar

**Evidencia:** los seis recursos públicos examinados devolvieron `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`. El navegador volvió a transferirlos incluso al regresar a `/login` dentro de la misma sesión. `Last-Modified` está presente, pero no permite aprovechar una copia que `no-store` impide conservar.

**Impacto:** cada cambio de sección vuelve a pagar los viajes de red de CSS y JS. La descarga dinámica de Estándar agrava este coste. En producción, los recursos comunes de una pantalla Estándar que incluye HTMX suman aproximadamente **24,1 KB comprimidos**, antes del HTML.

**Corrección propuesta:** configurar caché para `/css/**`, `/js/**` y `/vendor/**` y versionar sus URL por contenido. Esto permite conservar los archivos y obtener la versión nueva al desplegar. Mantener el tratamiento de las páginas privadas y formularios de acceso. Spring Security permite que una respuesta establezca su propia política de caché; no hace falta desactivar globalmente la protección. [Documentación oficial de Spring Security](https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html).

**Ubicaciones:** [application.yml](../src/main/resources/application.yml), [application-prod.yml](../src/main/resources/application-prod.yml), [SecurityConfig.java](../src/main/java/pe/org/beneficencia/legalcontrol/config/SecurityConfig.java) y [fragmento de recursos](../src/main/resources/templates/fragments/presentacion.html).

**Aceptación:** en una segunda navegación, los recursos sin cambios se recuperan de caché; una modificación de contenido genera otra URL; el HTML privado conserva su política apropiada.

### P02 · Alta · Cada recurso estático consulta la cuenta en PostgreSQL

**Evidencia:** `SessionGuardFilter.shouldNotFilter`, línea 48, solo excluye `/ping`. Con sesión válida, CSS, JS y HTMX ejecutan `SELECT auth_version FROM app_user WHERE id = ? AND status = 'ACTIVE'`. El contador por petición lo confirmó en el laboratorio.

**Impacto:** una navegación Estándar a Pendientes hace **8 consultas por el documento + 6 por sus recursos = 14**, aunque estos archivos son públicos e iguales para todos. El panel pasa de 4 a 10. Las consultas adicionales ocupan el pool de cinco conexiones y vinculan la entrega del estilo a la latencia/disponibilidad de PostgreSQL.

Con **50 ms añadidos por consulta**, un archivo CSS pasó a tardar aproximadamente **55 ms** por sí solo; el documento de Pendientes llegó a un P95 de **439 ms**. Las cinco sesiones concurrentes pasaron de **58 ms a 438 ms de P95**. La demora SQL es una simulación controlada, no una medición del enlace Render–Supabase.

**Corrección propuesta:** excluir del guardián de sesión los GET/HEAD de esos recursos públicos. Conservar la comprobación de revocación en las rutas de negocio. [SessionGuardFilter.java](../src/main/java/pe/org/beneficencia/legalcontrol/access/SessionGuardFilter.java).

**Aceptación:** cero consultas SQL por petición de CSS/JS/HTMX con o sin sesión; revocación de acceso efectiva en la siguiente petición a una ruta privada.

### P03 · Alta · Las pruebas de rendimiento pueden medir redirecciones al login

**Evidencia del código:** `PendingTaskPerformanceTest.preparar` comprueba si ya hay 5.000 pendientes, pero no si existe `abogado@ejemplo.test`. `EquipoPerformanceTest` puede dejar ese volumen con `jefa@ejemplo.test` y `abogado1` a `abogado4`, sin crear el correo que necesita la siguiente clase. El helper de acceso no exige éxito y los bucles de tiempo no verifican HTTP 200 ni contenido.

**Reproducción:** ejecutar ambas clases en orden alfabético reporta siete pruebas exitosas y tiempos de **1–2 ms** para Pendientes. En una reproducción HTTP independiente, con 5.000 pendientes y ese usuario ausente, el acceso devolvió `/login?error` y las siguientes 20 peticiones a Pendientes fueron **302**, con P95 de **3 ms**. El listado autenticado, medido correctamente, dio **52,48 ms de P95**.

**Impacto:** la suite puede parecer más rápida precisamente porque no está cargando la pantalla. Los 61 casos verdes no bastan para certificar todos sus tiempos publicados.

**Corrección propuesta:** garantizar cuenta/datos propios por clase; comprobar que el login crea una sesión válida; exigir estado 200 y un marcador de la pantalla en cada muestra de rendimiento. Revisar también los presupuestos de consultas, que podrían aceptar un coste bajo causado por la ausencia de sesión.

**Ubicaciones:** [PendingTaskPerformanceTest.java, líneas 44–74](../src/test/java/pe/org/beneficencia/legalcontrol/acceptance/PendingTaskPerformanceTest.java), [EquipoPerformanceTest.java, líneas 48–81](../src/test/java/pe/org/beneficencia/legalcontrol/acceptance/EquipoPerformanceTest.java), [SesionDePrueba.java](../src/test/java/pe/org/beneficencia/legalcontrol/integration/SesionDePrueba.java).

[Salida de la reproducción](auditoria-rendimiento-2026-09-27/reproduccion-pruebas.txt) · [Estados HTTP de la reproducción negativa](auditoria-rendimiento-2026-09-27/backend-http.json) · [Resumen de las 61 pruebas](auditoria-rendimiento-2026-09-27/tests-summary.json).

### P04 · Media · Estándar tiene una dependencia adicional antes del primer dibujo

**Evidencia:** el script de presentación está después de dos CSS y, al ejecutarse, añade `estandar.css`. En una carga pública medida, los recursos base empezaron a **279 ms**, el CSS Estándar a **480 ms**, terminó a **819 ms** y el LCP llegó a **848 ms**. Su solicitud se inició unos **201 ms después** de los recursos iniciales.

**Impacto:** el arreglo del destello cumple su función, pero ahora esa espera es explícita antes de mostrar la página. Una caché adecuada reducirá el coste al navegar; el primer acceso seguirá teniendo esta dependencia.

**Corrección propuesta:** hacer que la hoja necesaria se descubra antes, resolviendo la preferencia al principio del documento o en el HTML servido. Preservar la carga ligera de Rendimiento y la protección contra el destello. No basta con poner `defer` al script actual, porque aplazaría aún más la selección. El descubrimiento tardío de recursos forma parte del camino crítico de carga. [Guía técnica de web.dev](https://web.dev/learn/performance/optimize-resource-loading).

**Ubicaciones:** [fragments/presentacion.html:5–7](../src/main/resources/templates/fragments/presentacion.html), [presentacion.js:13–30](../src/main/resources/static/js/presentacion.js).

**Aceptación:** comparar la secuencia de solicitudes con caché vacía y llena; primera imagen con el modo correcto; Rendimiento no solicita el CSS Estándar.

### P05 · Media · Se entrega HTMX aunque los recorridos actuales no lo utilizan

**Evidencia:** seis plantillas incluyen `htmx.min.js` —una de ellas es el layout base—, pero no hay atributos `hx-*`, llamadas a la API de HTMX ni consumidores de `HtmxSupport` en el código de producción revisado. Los recorridos examinados funcionan mediante navegación y formularios normales. Graphify confirma que el helper no tiene llamadas externas de producción.

**Impacto:** **51.238 bytes sin comprimir / 17.158 bytes con Brotli**, una petición adicional y, actualmente, una consulta SQL adicional en las pantallas que lo cargan. Representa aproximadamente el **71 % de los bytes comprimidos de sus recursos comunes**; no significa el 71 % de su tiempo de carga.

**Corrección propuesta:** retirar la inclusión de las pantallas que no necesitan HTMX o incorporarla únicamente cuando exista una interacción que lo use. [dashboard/index.html:7](../src/main/resources/templates/dashboard/index.html), [HtmxSupport.java](../src/main/java/pe/org/beneficencia/legalcontrol/shared/HtmxSupport.java).

**Aceptación:** mismas acciones, navegación y validaciones; desaparición de la solicitud a HTMX en esas páginas.

## Oportunidades de segundo orden

### Calendario mensual

Es la pantalla de mayor coste local: P95 **212 ms**, todavía dentro de un margen razonable para este escenario. El SQL devolvió **3.088 eventos** en aproximadamente **13 ms de ejecución**. La plantilla mensual itera sobre todos los eventos de cada día y descarta los que tienen índice ≥3, aunque solo necesita tres entradas y el total.

Conviene construir una vista con las tres entradas visibles y el recuento por día antes de procesar la plantilla. Esto conserva el enlace «N más» y evita iteraciones de presentación innecesarias. Los tiempos SQL y HTTP se tomaron por separado: no se puede atribuir exactamente su diferencia a la plantilla sin un perfil de CPU. [calendar.html:97–112](../src/main/resources/templates/agenda/calendar.html) · [Planes SQL](auditoria-rendimiento-2026-09-27/sql-plans.json).

### Búsquedas y crecimiento

Las búsquedas contienen `ILIKE '%texto%'` sobre varios campos. Los planes sin coincidencias recorren las tablas y costaron aproximadamente **11–19 ms por consulta** con 5.000 filas. El total HTTP de búsqueda permaneció bajo 69 ms de P95 en este laboratorio. No justifica introducir un motor externo de búsqueda; repetir el perfil con el volumen real antes de añadir índices de texto o cambiar la estrategia.

## Aspectos que ya funcionan bien

- Recursos locales y tipografía del sistema: no hay fuentes remotas ni imágenes pesadas en estas pantallas.
- Compresión Brotli y HTTP/2 observados en producción.
- Paginación de listados, agregación de las cifras del panel y consultas agrupadas; no se observó un crecimiento de consultas por cada fila en los presupuestos examinados.
- Caché de plantillas activada en producción y en el laboratorio HTTP.
- Cinco sesiones simultáneas completaron 100 peticiones de lectura en los dos escenarios, todas HTTP 200.
- El modo Rendimiento evita descargar `estandar.css`.
- El arreglo del destello ya está desplegado y el cuerpo esperó al CSS Estándar en las cargas públicas observadas.

## Orden recomendado

1. **Caché y versionado de recursos (P01).** Mayor alcance en la navegación cotidiana.
2. **Excluir recursos públicos del guardián SQL (P02).** Reduce viajes a PostgreSQL y presión sobre conexiones.
3. **Corregir las mediciones automatizadas (P03).** Proporciona una referencia fiable para las siguientes mejoras.
4. **Adelantar el descubrimiento del estilo (P04)** y **retirar HTMX no utilizado (P05)**.
5. Medir el calendario después de las anteriores mejoras; optimizar su modelo de presentación si sigue siendo relevante.

## Límites de la conclusión

No se midieron CPU, RAM, pausas de GC, colas de conexiones ni la latencia real Render–Supabase en producción. Tampoco el acceso autenticado real, sesiones distribuidas por cuentas productivas o un arranque después de suspensión. La muestra pública es breve y hubo variación de red.

El repositorio declara el plan gratuito de Render, que puede suspender un servicio tras 15 minutos sin tráfico y necesita tiempo para reactivarlo. UptimeRobot mide disponibilidad, pero sus tiempos de `/ping` no representan el coste completo de una pantalla ni resuelven los hallazgos anteriores. [Comportamiento documentado de Render](https://render.com/docs/free).

Los datos y sesiones empleados en las mediciones internas fueron sintéticos. El informe no certifica el objetivo completo del equipo físico de referencia del proyecto. Las conclusiones principales se apoyan en cabeceras reales, solicitudes del navegador, código identificado y mediciones locales con estado HTTP comprobado.
