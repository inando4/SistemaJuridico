# Optimizaciones de rendimiento y verificación

**Sistema Jurídico · 27 de septiembre de 2026, hora de Perú**  
**Código publicado en `main`: `15ad88c` y `3cf236c` (versión final).**

## Cambios aplicados

1. **Caché y versionado por contenido.** CSS y JavaScript llevan una huella en su URL y se conservan un año. El HTML de acceso y de las pantallas privadas mantiene `no-store`. Una modificación de un archivo produce otra URL. [Configuración](../src/main/resources/application.yml) · [Registro de recursos](../src/main/java/pe/org/beneficencia/legalcontrol/config/StaticResourcesConfig.java).
2. **Cero consultas SQL al servir recursos públicos.** El filtro de sesión excluye sus GET/HEAD; la revocación sigue comprobándose en las pantallas privadas. Se verificaron recursos con sesión activa, revocada y sin sesión. [Filtro](../src/main/java/pe/org/beneficencia/legalcontrol/access/SessionGuardFilter.java) · [Contrato](../src/test/java/pe/org/beneficencia/legalcontrol/web/RecursosEstaticosContractTest.java).
3. **Pruebas que validan la pantalla medida.** Cada prueba prepara sus propios datos. El helper exige autenticación correcta y las muestras verifican HTTP 200, HTML, sesión y contenido; los tiempos también comprueban la vista esperada. Esto corrigió los falsos positivos de Pendientes y Configuración. [Helper de sesión](../src/test/java/pe/org/beneficencia/legalcontrol/integration/SesionDePrueba.java) · [Validación de pantalla](../src/test/java/pe/org/beneficencia/legalcontrol/integration/PantallaDePrueba.java).
4. **Descarga anticipada de Estándar.** Un arranque breve antes de los CSS lee la preferencia e inicia la descarga de la hoja necesaria. Se conserva la protección contra el destello. Rendimiento sigue sin solicitar esa hoja. [Fragmento](../src/main/resources/templates/fragments/presentacion.html) · [Pruebas en navegador](../src/test/java/pe/org/beneficencia/legalcontrol/acceptance/ModosPresentacionTest.java).
5. **HTMX retirado de las seis plantillas que lo cargaban sin utilizarlo.** Desaparece una descarga de 51.238 bytes sin comprimir, 17.158 bytes con Brotli en la auditoría inicial.
6. **Resumen del calendario mensual.** La plantilla recibe como máximo tres eventos por día y el recuento restante. Las vistas de día y semana conservan todos. El recorrido automatizado comprueba que «4 más» permite abrir los siete eventos y conserva los filtros. [Resumen](../src/main/java/pe/org/beneficencia/legalcontrol/agenda/RejillaDelMes.java) · [Recorrido](../src/test/java/pe/org/beneficencia/legalcontrol/acceptance/RecorridoAgendaTest.java).

### Ajuste detectado al medir la primera implementación

El manejador estático genérico `/**` hacía que la resolución de URL examinara también los enlaces a fichas y pantallas como posibles archivos. Se sustituyó por manejadores concretos de `/css/**`, `/js/**` y `/vendor/**`. La nueva prueba exige que las rutas de negocio queden fuera de esos manejadores.

La comparación del calendario semanal hizo visible ese coste: su P95 pasó de 82,86 ms en la auditoría a 133,98 ms en la primera implementación. Tras acotar los recursos quedó en **61,86 ms**. Las evidencias finales corresponden a esta última configuración.

## Verificación automatizada

**825 casos contabilizados: 822 aprobados, 0 fallos, 0 errores y 3 omitidos.** Los omitidos son capturas visuales opcionales activadas por propiedades; las pruebas funcionales en navegador sí se ejecutaron.

La ejecución conjunta se interrumpió antes de acabar. Se completó la verificación por grupos de servidor, integración y aceptación; después se repitieron los contratos de recursos, modos, calendario y presupuestos afectados por el último ajuste. El resumen usa el último informe de cada clase y no suma las repeticiones como casos nuevos.

Se comprobó, entre otros:

- URLs con la huella correcta, respuesta 404 ante una huella inexistente y caché privada del HTML.
- Ausencia de SQL en recursos públicos y revocación efectiva al volver a una pantalla privada.
- Reutilización de cinco recursos en Estándar y cuatro en Rendimiento.
- Primera visualización con Estándar aplicado, incluso retrasando el script externo.
- Formulario visible si falla la hoja de estilo, navegación sin JavaScript y funcionamiento con almacenamiento bloqueado.
- Cambio de modo con teclado, conservación de campos y foco, sincronización entre pestañas.
- Pantallas pobladas a 320, 390 y 1.440 px, en ambos modos.

[Resumen de pruebas](optimizacion-rendimiento-2026-09-27/tests-summary.json).

## Laboratorio con datos sintéticos

Misma configuración de la auditoría: PostgreSQL 17.11 aislado, 5.000 expedientes, 5.000 procedimientos, 5.000 pendientes, cinco conexiones JDBC y caché de Thymeleaf activada. Java local 25; Render utiliza Java 21. Playwright MCP con Chromium; móvil emulado a 390 px, CPU ×4 y red de 2 Mbps/150 ms.

### Carga visual (LCP mediano)

Ocho navegaciones por combinación; caché vacía al empezar cada combinación y navegación normal después.

| Modo y tamaño | Antes | Después |
|---|---:|---:|
| Estándar, escritorio | 152 ms | **72 ms** |
| Estándar, móvil emulado | 684 ms | **242 ms** |
| Rendimiento, escritorio | 88 ms | **64 ms** |
| Rendimiento, móvil emulado | 438 ms | **228 ms** |

**32 respuestas HTTP 200, cero excepciones JavaScript.** Las siete navegaciones siguientes de cada combinación reutilizaron todos sus CSS y JS: **126 recursos desde caché**. El CLS máximo observado fue 0,0449 en Estándar y 0,0057 en Rendimiento.

[Datos del navegador](optimizacion-rendimiento-2026-09-27/sj_opt_local.json) · [Panel sintético](optimizacion-rendimiento-2026-09-27/local-estandar-1440-panel.png) · [Vista móvil sintética](optimizacion-rendimiento-2026-09-27/local-estandar-390-calendario.png).

### Servidor por HTTP (P95)

Veinte muestras por ruta, tras tres calentamientos. Incluye recibir el documento, sin descargar sus recursos ni dibujarlo.

| Ruta | Antes | Después |
|---|---:|---:|
| Panel | 13,29 ms | 21,45 ms |
| Pendientes | 52,48 ms | 35,06 ms |
| Calendario mensual | 212,35 ms | 204,74 ms |
| Calendario semanal | 82,86 ms | 61,86 ms |

Los tiempos de servidor tienen variación y **no todas las rutas mejoraron**; el panel subió en esta tanda. La mejora principal comprobada está en las descargas y en el navegador. En el calendario mensual la mediana pasó de 154,69 a 126,17 ms, pero el cambio del P95 fue pequeño.

Se completaron **468 muestras HTTP**, incluyendo la simulación de 50 ms por consulta SQL y cinco sesiones concurrentes, todas con HTTP 200. Los recursos estáticos conservaron **cero consultas** incluso con la demora SQL simulada. Los documentos mantuvieron sus presupuestos: panel 4, Pendientes 8 y calendario 5.

Con sesión y caché vacía, una pantalla Estándar de Pendientes pasa de **14 consultas totales a 8**, y el panel de **10 a 4**, al quitar el coste de sus recursos.

[Mediciones HTTP](optimizacion-rendimiento-2026-09-27/backend-http.json) · [Consultas por ruta](optimizacion-rendimiento-2026-09-27/consultas-por-ruta.json).

## Producción

Se comprobó `https://sistemajuridico-f1m4.onrender.com` con la cuenta facilitada por el usuario. La huella del JavaScript servido coincidió con el archivo del commit **`3cf236c`**. Se realizaron 32 navegaciones antes y otras 32 después: panel, Pendientes, Judiciales, Administrativos, Alertas, Calendario, búsqueda sin coincidencias y Hoy; ocho rutas por combinación de modo/anchura.

| Modo y perfil | LCP mediano antes | LCP mediano después |
|---|---:|---:|
| Estándar, escritorio | 1.064 ms | **840 ms** |
| Estándar, móvil emulado | 884 ms | **384 ms** |
| Rendimiento, escritorio | 686 ms | **436 ms** |
| Rendimiento, móvil emulado | 560 ms | **338 ms** |

**Recorrido final: 32 respuestas HTTP 200, cero excepciones JavaScript y cero desbordes horizontales del documento.** El navegador reutilizó **126 recursos desde caché** en las navegaciones siguientes; antes fueron cero. No se solicitó HTMX ni se descargó el CSS Estándar en Rendimiento.

Comprobaciones adicionales:

- `/ping` respondió **200 a GET y HEAD**.
- Los cinco recursos activos respondieron 200 y `Cache-Control: max-age=31536000, public`.
- Todas las páginas privadas medidas conservaron `no-store`.
- La primera visualización de las 16 cargas Estándar ya tenía su hoja aplicada.
- El selector respondió a Espacio y Enter y conservó el contenido de un formulario **sin guardarlo**.
- En las primeras cargas Estándar, la demora de descubrimiento del CSS respecto a `app.css` pasó de **206 a 7 ms** en escritorio y de **226 a 36 ms** en móvil.

**Límites de la comparación:** son ocho observaciones por perfil, tomadas en tandas distintas. El TTFB mediano de Estándar escritorio subió de 517 a 798 ms y su primera carga con caché vacía pasó de 1.032 a 1.348 ms. Por tanto, esta medición no demuestra que toda carga inicial sea más rápida. La mejora consistente comprobada es la reutilización de recursos durante la navegación. El resultado móvil no permite concluir que un teléfono sea más rápido que un escritorio: influyen el momento de ejecución, la red y el estado de Render después del despliegue.

Un primer intento inmediatamente posterior al despliegue perdió la sesión y redirigió al login. Se repitió con una sesión nueva y completó las 32 cargas y las comprobaciones adicionales sin cortes. Es compatible con un cambio de instancia y las sesiones en memoria, pero no se dispone de logs internos de Render para confirmar la causa de ese primer corte.

[Medición anterior](optimizacion-rendimiento-2026-09-27/sj_prod_antes.json) · [Medición posterior y comprobaciones](optimizacion-rendimiento-2026-09-27/sj_prod_despues.json) · [Comparación](optimizacion-rendimiento-2026-09-27/comparacion-produccion.json) · [Comprobación de despliegue](optimizacion-rendimiento-2026-09-27/deploy-checks.jsonl).

## Alcance

Las mediciones son de laboratorio y de una sesión de producción, no estadísticas de usuarios reales ni un SLA. El móvil es una emulación de Chromium, no un dispositivo físico. No se midieron CPU, RAM, GC o latencia SQL dentro de Render, ni un arranque tras suspensión. Los datos internos del laboratorio son sintéticos; en producción se navega y se comprueba un formulario sin guardarlo.

[Auditoría previa y método original](auditoria-rendimiento-2026-09-27.md).
