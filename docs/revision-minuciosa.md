⚠️ DEGRADED: single-context (sub-agents declined by user).

# Revisión minuciosa de pantallas y flujos

Revisión individual solicitada por el usuario, con Playwright/Chromium y PostgreSQL efímero de Testcontainers. Se examinó el estado de trabajo que ya contenía la reorganización anterior. Esta revisión añade diagnóstico y evidencia; no modifica las pantallas ni la lógica de producción.

Se recorrieron las **36 plantillas de pantalla utilizadas por los controladores**, incluidos los cinco catálogos que comparten plantilla, formularios de alta y edición, fichas, historiales, acceso y errores. Se registraron **125 combinaciones de pantalla, estado y rol**, con **250 capturas**, a 1280×800 y 390×800. Los roles fueron jefa y abogada; el acceso se revisó sin sesión. La revisión del DOM abarcó todas las capturas y se inspeccionaron visualmente las pantallas representativas y los fallos encontrados.

Se pulsaron los doce destinos de navegación principal y se recorrieron edición y guardado de las tres entidades, completar/revertir/cancelar/devolver un pendiente, alta/corrección/retiro de actividad, creación y activación de cuenta, errores de validación, conflicto por versión, acceso denegado y filtros. No se afirma haber probado todas las combinaciones posibles de datos, todos los navegadores ni cada acción administrativa destructiva: las variantes no capturadas conservan la cobertura de pruebas existente, no se cuentan como verificadas nuevamente.

La base visual sobria es adecuada al producto. Lo que más perjudica la experiencia son la pérdida de contexto y de datos al corregir un error, y la dificultad para descubrir algunas funciones. No hace falta añadir decoración, animaciones, fuentes ni librerías para resolverlo.

## Prioridad alta: fallos reproducidos

### P1 — El responsable cambia después de corregir un error

En alta judicial y administrativa, elegí a otra abogada como responsable y envié un número duplicado. El formulario devuelto perdió el desplegable de responsable. Corregí solamente el número y guardé: el nuevo registro quedó a nombre de la jefa. Es una modificación involuntaria de la asignación, no solo un problema de presentación.

**Propuesta:** devolver las opciones y la selección original al fallar la validación; conservarlas al reintentar. Verificar con una prueba que la persona elegida siga siendo responsable después de corregir el número.

[Captura](/home/n4nd0/Documentos/SistemaJuridico/target/revision-minuciosa/015-diagnostico-judiciales-responsable-error-1280.png) · [Captura](/home/n4nd0/Documentos/SistemaJuridico/target/revision-minuciosa/017-diagnostico-judiciales-responsable-resultado-1280.png) · [JudicialCaseController.java](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/judicialcase/JudicialCaseController.java:151)

### P1 — «Aplicar filtros» elimina el criterio de vencidos

Desde Panel → Vencidos, la URL contiene `alerta=vencidos` y el responsable. Al pulsar «Aplicar filtros» sin cambiar nada, desaparece `alerta` y aparecen también pendientes futuros y sin plazo. El formulario conserva el responsable, pero no el criterio que motivó la navegación.

**Propuesta:** conservar el foco en el envío GET y mostrarlo como criterio activo, con una salida explícita para quitarlo. Aplicar lo mismo a los focos que llegan desde Equipo.

[Captura](/home/n4nd0/Documentos/SistemaJuridico/target/revision-minuciosa/001-diagnostico-foco-vencidos-antes-1280.png) · [Captura](/home/n4nd0/Documentos/SistemaJuridico/target/revision-minuciosa/003-diagnostico-foco-vencidos-despues-1280.png) · [list.html](/home/n4nd0/Documentos/SistemaJuridico/src/main/resources/templates/pending-tasks/list.html:35)

### P1 — Un error al corregir actividad devuelve los datos al formulario de alta

Abrí «Corregir», cambié el texto y seleccioné simultáneamente un tipo de catálogo y «Otro tipo». Tras enviar, el bloque de corrección quedó cerrado y los datos y el mensaje de error aparecieron bajo «Agregar actividad manual». El botón ofrecido pasó a ser «Agregar actividad». Eso conduce a crear otra actividad cuando se pretendía corregir la existente.

**Propuesta:** mantener abierto el formulario del registro editado, con sus datos, versión y error local. El formulario de alta debe conservar su contexto independiente.

[Captura](/home/n4nd0/Documentos/SistemaJuridico/target/revision-minuciosa/021-diagnostico-actividad-error-edicion-1280.png) · [DailyActivityController.java](/home/n4nd0/Documentos/SistemaJuridico/src/main/java/pe/org/beneficencia/legalcontrol/activity/DailyActivityController.java:143)

### P1 — La activación existe, pero no se puede descubrir desde el login

El login informa que se debe solicitar un código, pero no enlaza a «Activar acceso». La pantalla que genera el código indica que hay que entrar allí, sin ofrecer la ruta. El canje sí funcionó al abrir directamente `/acceso/canjear`. Tampoco encontré acceso a `/cuenta/contrasena` desde los enlaces de las pantallas capturadas.

**Propuesta:** ofrecer el acceso al canje junto al ingreso y ubicar cambio de contraseña y cierre de sesión en una zona de cuenta consistente. Mantener el login simple; no hace falta otra pantalla.

[Captura](/home/n4nd0/Documentos/SistemaJuridico/target/revision-minuciosa/211-publico-login-1280.png) · [login.html](/home/n4nd0/Documentos/SistemaJuridico/src/main/resources/templates/access/login.html:19)

## Mejoras de organización y recuperación

### P2 — Formularios extensos sin bloques ni jerarquía de tareas

El alta judicial presenta 15 campos en una sola secuencia y alcanza 1772 px en escritorio; el alta de pendientes presenta 13 campos y alcanza 1577 px. Las fechas y el botón Guardar quedan muy abajo. La edición repite la misma estructura. El formulario administrativo es más corto, pero sigue la misma organización lineal.

**Propuesta:** agrupar en «Identificación y responsable», «Estado y plazos» y «Información adicional»; en pendientes, separar «Vínculo», «Fechas» y «Documento de salida». Compactar únicamente lo opcional mediante controles nativos; abrir un grupo si contiene errores. No retirar campos ni convertirlo en un asistente de múltiples pasos.

[Captura](/home/n4nd0/Documentos/SistemaJuridico/target/revision-minuciosa/049-jefa-judiciales-alta-1280.png) · [Captura](/home/n4nd0/Documentos/SistemaJuridico/target/revision-minuciosa/069-jefa-pendientes-alta-1280.png)

### P2 — Acciones de uso frecuente y de retirada compiten

En la ficha del pendiente, «Cancelar» queda entre «Marcar como cumplido» y «No cumplido…», con el mismo peso. «Cancelar» también significa abandonar un formulario en otras pantallas. Reprogramar y reasignar quedan abiertos aunque se haya entrado solo para consultar.

**Propuesta:** reunir las acciones de trabajo, poner reprogramación y reasignación en grupos desplegables identificables y separar la retirada. Aclarar «Cancelar pendiente» sin eliminar el botón. Mantener todas las funciones accesibles y los errores dentro del grupo correspondiente.

### P2 — La navegación de regreso pierde el contexto

Abrí una ficha desde `/pendientes?q=sintetico&sort=title` y pulsé «Volver a pendientes»: regresó a `/pendientes`, perdiendo búsqueda y orden. En Cuentas y Días no laborables, «Volver» lleva a Judiciales. Los catálogos no tienen la navegación común ni regreso a Configuración. En varias pantallas desaparecen la identidad y el cierre de sesión.

**Propuesta:** conservar la URL interna de origen para volver a la lista y hacer coherentes los regresos de Configuración. Reunir la navegación y el área de cuenta en fragmentos compartidos, sin duplicar opciones en el cuerpo.

### P2 — «Editar» se ofrece incluso cuando acaba en acceso denegado

Como abogada, la ficha de un judicial ajeno muestra «Editar». Entrar en su edición devuelve 403. La ficha administrativa y la del pendiente sí ajustan las acciones a los permisos.

**Propuesta:** conservar la opción, presentarla como no disponible y explicar quién puede editar; mantener la autorización del servidor. El usuario debe conocer la restricción antes del clic.

### P2 — Los errores no siempre permiten recuperar el trabajo

Un correo duplicado al crear una cuenta borra Nombre y Correo y devuelve Rol a su valor inicial. Un conflicto de versión sustituye el formulario por una página genérica cuyo único enlace es «Volver al inicio», aunque el mensaje pide revisar la versión actual. Una ruta inexistente muestra «Whitelabel Error Page» en inglés.

**Propuesta:** preservar los datos no sensibles en validaciones, ofrecer regreso al registro y una vía para revisar los cambios en conflictos, y cubrir el error genérico con una página en español. Las contraseñas y códigos pueden seguir vacíos tras error por su naturaleza sensible.

[Captura](/home/n4nd0/Documentos/SistemaJuridico/target/revision-minuciosa/207-jefa-usuarios-validacion-1280.png) · [Captura](/home/n4nd0/Documentos/SistemaJuridico/target/revision-minuciosa/181-jefa-error-conflicto-1280.png)

### P2 — Tablas secundarias todavía desbordan en móvil

Se detectaron 22 capturas móviles con desborde de página. Son variantes y roles repetidos, no 22 pantallas distintas. Afectan a Alertas, Hoy, Equipo, cinco catálogos, fichas con pendientes relacionados, Cuentas, Cumplidos e historiales con datos. A 390 px, Cumplidos llega a 1069 px, Equipo a 698 px y Cuentas a 687 px. Las tablas principales ya contenidas y el calendario mensual no desbordan.

**Propuesta:** extender las regiones desplazables accesibles a esas tablas; mantener encabezados y todas las columnas. En fichas de pares campo/valor, permitir el ajuste del texto. No reducir la fuente ni ocultar columnas para forzar que quepan.

### P2 — Fechas de auditoría poco legibles y con día aparente distinto

Historiales y Cumplidos imprimen el instante UTC directamente, por ejemplo `2026-09-11T03:44:19.730178Z`, mientras el día local de trabajo es 10/09/2026. Puede parecer que la acción ocurrió al día siguiente.

**Propuesta:** presentar fecha y hora en America/Lima, con formato legible y precisión útil. Conservar el instante original almacenado. El formato de los campos de fecha nativos depende del idioma del navegador de prueba y no se trató como un fallo de la aplicación.

### P2 — Mensajes desactualizados y confirmaciones incompletas

Las altas judicial y administrativa dicen que el responsable será quien registra y que la reasignación no existe en esta versión, aunque ya hay selección y reasignación. Tras activar la cuenta se llega a `/login?activada`, pero no aparece confirmación. Después de retirar una actividad, el aviso dice que sigue en el historial, pero el enlace a ese historial desaparece con la fila.

**Propuesta:** corregir la explicación del responsable según el rol; confirmar la activación; incluir en el resultado del retiro una vía al historial existente. No inventar nuevas reglas de negocio.

### P3 — Detalles que siguen aumentando la carga mental

- Los filtros ampliados siguen siendo una columna extensa: reunir responsable/estado, plazos y visualización/orden.
- La casilla «Toda el área» de Calendario y las confirmaciones de cobertura quedan alejadas del texto por el ancho global de los inputs. Mantener casilla y etiqueta juntas; mostrar claramente cuándo la selección de persona deja de aplicar.
- El grupo Seguimiento se abre automáticamente y ocupa bastante altura. Evaluar una presentación más compacta que mantenga visible el nombre de la sección actual.
- Fechas, título y número de expediente deberían aparecer siempre en posiciones consistentes entre fichas.
- Hay tildes y textos antiguos en varias pantallas; corregir textos de interfaz, sin modificar los datos sintéticos de las pruebas.

## Qué funciona y conviene conservar

- La aplicación usa HTML sencillo y recursos locales. Los controles son reconocibles, los campos capturados tienen etiquetas y no se detectaron IDs duplicados.
- La búsqueda distingue inicio, término demasiado corto, ausencia de resultados y resultados agrupados.
- Hay indicación de sección actual, estados de plazo con texto, historial de cambios y controles de autorización del servidor.
- Los tres listados principales conservan sus opciones dentro del grupo ampliable; no es necesario recuperar una barra con todos los filtros siempre abiertos.

## Evaluación heurística orientativa

Puntuación de juicio UX, de 0 a 4; no es un resultado automatizado.

| Criterio | Puntos | Evidencia principal |
|---|---:|---|
| Estado del sistema | 2 | Buenas alertas; faltan confirmaciones de activación y conservación de foco |
| Lenguaje del trabajo | 2 | Terminología útil; textos de asignación desactualizados y UTC visible |
| Control y regreso | 1 | Filtros y contexto se pierden al regresar |
| Consistencia | 2 | Formularios de distintas entidades y permisos de edición dispares |
| Prevención de errores | 1 | Cambio involuntario de responsable y corrección enviada al alta |
| Reconocimiento | 2 | Agrupación inicial útil; activación y contraseña no descubribles |
| Eficiencia | 2 | Filtros combinables; formularios largos y acciones lejanas |
| Sobriedad y jerarquía | 3 | Base liviana adecuada; falta separar grupos funcionales |
| Recuperación de errores | 1 | Se pierden datos y el conflicto lleva solo al inicio |
| Ayuda contextual | 2 | Algunas ayudas precisas, otras contradicen el comportamiento |
| **Total** | **18/40** | **Priorizar recuperación y contexto antes del acabado visual** |

Para una abogada que trabaja con listas, la mayor fricción es recomponer filtros. Para la jefa, es conservar la asignación al corregir un alta. Para alguien que entra por primera vez, es encontrar dónde usar el código. Estos tres recorridos orientan la prioridad; no hace falta cambiar la identidad visual.

## Alcance, herramientas y reproducción

Los dos recorridos de auditoría terminaron correctamente. Eso demuestra que se completó el diagnóstico; no significa que las 125 combinaciones carezcan de problemas. Las verificaciones incluyeron respuesta de rutas, capturas, lectura del DOM, etiquetas, desborde y acciones reales sobre datos de prueba.

La prueba queda **optativa** para no añadir capturas masivas a cada compilación:

```sh
DOCKER_HOST=unix:///home/n4nd0/.docker/desktop/docker.sock ./mvnw -Dux.audit=true -Dtest=RevisionPantallasTest test
```

Solo se crearon cuentas y registros sintéticos dentro de PostgreSQL efímero. Los navegadores y la aplicación de pruebas se cerraron al terminar. No se modificaron las plantillas de producción durante esta revisión.

El análisis mecánico de Impeccable tuvo módulos HTML no disponibles y recurrió a expresiones regulares. Emitió cinco avisos por guiones usados como marcadores de celdas vacías; no aportó una prueba de contraste ni de layout. No se instaló ni se mostró un overlay en el navegador. No hubo lista de exclusiones; target: `src-main-resources-templates`. La evaluación fue individual por instrucción expresa del usuario, no dos valoraciones independientes.

La cobertura y los enlaces a cada captura están en [la matriz de cobertura](/home/n4nd0/Documentos/SistemaJuridico/docs/revision-minuciosa-cobertura.md). Los archivos están bajo `target/`, por lo que `mvn clean` elimina la evidencia y la prueba permite regenerarla.

Questions skipped: el usuario solicitó detección y ya fijó las restricciones de ligereza, conservación de opciones y ausencia de subagentes; no se requiere una decisión adicional para entregar esta revisión.
