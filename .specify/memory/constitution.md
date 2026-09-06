<!--
Informe de impacto de sincronización
- Versión: 1.0.0 → 2.0.0 (MAJOR: sustituye la prohibición de roles y permisos por rol).
- Motivación: el cliente confirmó que la jefa asigna expedientes a los abogados.
- Principios modificados:
  - II. Multiusuario, acceso compartido y atribución → mismo título; dos roles,
    consulta total, escritura restringida y asignación o reasignación por la jefa.
  - VI. Plazos hábiles con cálculo centralizado → mismo título; se elimina la
    referencia al único tipo de usuario, conservando la administración del calendario.
  - VII. Trazabilidad inmutable → mismo título; auditoría de operaciones de la jefa
    sobre registros ajenos, incluyendo registro y responsable previo.
- Secciones añadidas o eliminadas: ninguna.
- Controles de calidad: se amplían las verificaciones de permisos y auditoría.
- Adaptación requerida: revisar especificaciones, planes y tareas dependientes para
  incorporar los dos roles, restricciones de escritura y auditoría ampliada.
- Plantillas y comandos dependientes: sin modificaciones; leen la constitución en ejecución.
- Marcadores pendientes e intenciones diferidas: ninguno.
-->
# Constitución de SistemaJuridico

## Principios fundamentales

### I. Idioma según destinatario

Las especificaciones (`specs`), planes (`plan`), tareas (`tasks`) y documentación
DEBEN redactarse en español. El código, las tablas, las columnas y las variables
DEBEN nombrarse en inglés. Todos los mensajes al usuario final, incluidos errores,
validaciones y advertencias, DEBEN estar en español.

### II. Multiusuario, acceso compartido y atribución

El sistema DEBE ser multiusuario desde la primera versión, para aproximadamente cinco
personas: tres o cuatro abogados y la jefa del área. DEBEN existir exactamente dos roles:
abogado y jefa. La jefa DEBE tener todas las capacidades de un abogado más las propias
de su rol. Queda sin efecto la prohibición anterior de roles y permisos por rol.
Esta distinción responde a la asignación de expedientes por la jefa confirmada por el cliente.

Todo usuario DEBE poder CONSULTAR los expedientes y pendientes de cualquier miembro.
Un abogado DEBE poder operar sobre sus propios registros y tener SOLO LECTURA sobre
los de otro abogado: NO DEBE modificarlos, marcarlos como cumplidos, reprogramarlos
ni cancelarlos. La jefa SÍ DEBE poder modificar registros de cualquier abogado y
asignar o reasignar expedientes entre ellos. La asignación y reasignación entre abogados
son capacidades propias de la jefa. Los permisos DEBEN comprobarse en el servidor
en cada operación; ocultar controles en la interfaz no constituye autorización.

Todo registro DEBE tener un usuario responsable identificable. El sistema DEBE permitir
consultar quién trabaja en qué y comparar la carga de trabajo por abogado para identificar
saturación. La atribución NO limita la visibilidad. La identidad de quien realiza una
operación DEBE distinguirse del responsable asignado cuando sean personas diferentes.

Toda operación de la jefa sobre un registro ajeno DEBE quedar auditada en el historial:
qué operación realizó y qué cambió, sobre qué registro, de qué abogado era y cuándo,
identificando a la jefa autora. Si no hay modificación, la auditoría DEBE indicar que
no hubo cambios. El historial DEBE cumplir la inmutabilidad del principio VII.

### III. Índice físico y protección de datos

El sistema DEBE ser únicamente un índice de control de expedientes físicos. NO DEBE
almacenar documentos, adjuntos ni PDFs, ni incorporar digitalización, buckets o visores.
Los expedientes permanecen en formato físico.

El sistema almacena datos personales —partes, materias, montos y direcciones— y laborales
—carga de trabajo por abogado—. La base de datos NO DEBE exponerse a Internet. DEBEN
realizarse respaldos periódicos y verificarse mediante pruebas de restauración; la mera
existencia de un archivo de respaldo no acredita su recuperación.

### IV. Ligereza como requisito de aceptación

La interfaz DEBE ser mínima, sin framework de JavaScript ni carga gráfica innecesaria.
Cada interacción DEBE minimizar tanto el volumen transferido como el número de viajes
al servidor. El sistema DEBE funcionar con laptops modestas y conexiones lentas: la
sustitución de Excel exige eliminar la lentitud que motiva el cambio.

Cada pantalla DEBE ofrecer carga percibida inmediata en el equipo lento de referencia.
Antes de implementarla, el plan DEBE definir equipo, conexión, volumen de datos y
presupuestos medibles de tiempo y solicitudes. La aceptación DEBE medir esas condiciones;
una pantalla que incumpla esos presupuestos NO está terminada.

### V. Interpretación activa sin persistencia de derivados

Toda fecha almacenada DEBE traducirse en una interpretación visible pertinente a su
significado. Los vencimientos DEBEN mostrar «vence hoy», «vencido» o los días hábiles
restantes, según corresponda. Las demás fechas DEBEN mostrar su relación temporal
pertinente sin presentarse como vencimientos cuando no lo sean.

Los valores derivados DEBEN calcularse en tiempo de consulta y NUNCA almacenarse.
Esto incluye indicadores temporales, días restantes y agregados de carga de trabajo.
El sistema DEBE aportar interpretación y NO limitarse a reproducir una tabla digital.

### VI. Plazos hábiles con cálculo centralizado

Los vencimientos DEBEN contarse en días hábiles. Sábados, domingos y fechas registradas
como no laborables NO son hábiles. Una única función centralizada DEBE resolver el
cálculo; NO DEBE reimplementarse por pantalla ni por tipo de expediente.

La tabla de días no laborables DEBE ser administrable desde el sistema
y estar poblada para el año en curso. Si no lo está, el sistema DEBE
advertirlo visiblemente en lugar de calcular en silencio. Los planes DEBEN concretar
cómo se comprueba la cobertura anual y cómo se tratan períodos que cruzan años,
conservando la función única y la advertencia ante cobertura faltante.

### VII. Trazabilidad inmutable

Toda modificación de fecha o estado DEBE registrar el valor anterior, el valor nuevo,
el momento del cambio y el usuario que lo realizó. Para fechas se conservan la fecha
anterior y la nueva; para estados se conservan ambos estados. El autor del cambio DEBE
quedar identificado aunque difiera del responsable asignado al registro.

La auditoría DEBE cubrir además toda operación de la jefa sobre registros ajenos,
aunque no cambie fechas ni estados. DEBE identificar la operación, el registro afectado,
el abogado responsable en ese momento, la jefa autora y el momento de la operación.
Si hay cambios, DEBE conservar los valores anteriores y nuevos; si no los hay, DEBE
indicarlo. Las asignaciones y reasignaciones DEBEN conservar el responsable anterior
—o la ausencia de asignación previa— y el nuevo, sin perder la atribución histórica.

La modificación y su historial DEBEN persistirse de forma atómica. El historial NO DEBE
borrarse ni editarse. Los valores históricos conservados son evidencia de modificaciones,
no valores derivados de consulta.

### VIII. Arquitectura de un solo proceso

El backend DEBE utilizar Java con Spring Boot y PostgreSQL como base de datos. Las vistas
DEBEN utilizar Thymeleaf y HTMX y servirse desde el mismo proceso del backend. La aplicación
DEBE desplegarse como un único JAR autocontenido; PostgreSQL constituye su servicio de
persistencia y no forma parte del JAR.

La primera versión NO DEBE incorporar una API REST separada ni un framework de JavaScript.
Las interacciones con HTMX DEBEN respetar los requisitos de ligereza y de viajes al servidor.

## Restricciones operativas

- El acceso compartido corresponde a usuarios identificados del sistema; no implica
  acceso público a datos personales o laborales.
- La operación DEBE mantener PostgreSQL fuera de la exposición pública a Internet.
- El plan operativo DEBE fijar frecuencia, retención y responsable de respaldos, junto
  con la periodicidad y evidencia de las pruebas de restauración.
- La operación DEBE mantener la cobertura del calendario no laborable del año en curso
  y comprobar su actualización al cambiar de año.

## Controles de calidad

Cada especificación, plan y conjunto de tareas DEBE comprobar su conformidad con esta
constitución. Los planes DEBEN traducir los principios aplicables en criterios de aceptación
y tareas verificables antes de iniciar la implementación.

La revisión de cambios DEBE comprobar, según el alcance afectado:

- Idioma de documentación, identificadores y mensajes al usuario final.
- Consulta compartida entre usuarios y atribución de registros y operaciones.
- Pruebas de autorización en el servidor: un abogado puede operar sobre registros propios
  y consultar los ajenos, pero no modificarlos, cumplirlos, reprogramarlos ni cancelarlos;
  la jefa puede modificar registros ajenos y asignar o reasignar expedientes.
- Ausencia de almacenamiento documental y conformidad con la arquitectura establecida.
- Función única de plazos, con pruebas de fines de semana, días no laborables, cambios
  de año y advertencia por cobertura anual faltante.
- Interpretación visible de fechas y recálculo de derivados al consultar, sin persistirlos.
- Historial con valores anterior y nuevo, autor y momento; pruebas de integración de
  atomicidad y de protección frente a edición y borrado del historial.
- Auditoría de toda operación de la jefa sobre registros ajenos, con registro, abogado
  responsable previo, autora, momento y cambios o constancia de ausencia de cambios;
  conservación de responsables anterior y nuevo en asignaciones y reasignaciones.
- Mediciones de carga y solicitudes con el equipo, red y datos definidos en el plan.
- Ausencia de exposición pública de la base de datos y evidencia de restauración de
  respaldos cuando el cambio afecte a la operación.

Una revisión NO DEBE dar por cumplido un criterio sin evidencia. Los incumplimientos DEBEN
resolverse antes de aceptar el cambio o tramitarse como una enmienda explícita de la
constitución; no se admiten excepciones implícitas.

## Gobernanza

Esta constitución prevalece sobre especificaciones, planes, tareas y decisiones de diseño
incompatibles. Toda enmienda DEBE documentar su motivación, principios afectados, impacto
y adaptación necesaria de los artefactos dependientes. La persona responsable del proyecto
DEBE aprobar la enmienda antes de su adopción.

Las versiones siguen MAJOR.MINOR.PATCH: MAJOR para eliminaciones o redefiniciones
incompatibles de principios; MINOR para nuevos principios o ampliaciones materiales;
PATCH para aclaraciones sin cambios normativos. Cada enmienda DEBE actualizar versión,
fecha de última modificación e informe de impacto. La fecha de ratificación se conserva.

Las revisiones de especificaciones, planes, tareas e implementación DEBEN verificar los
principios aplicables. El flujo de actualización de la constitución modifica únicamente
este archivo; los artefactos dependientes se revisan en sus propios flujos conforme a la
versión vigente.

**Versión**: 2.0.0 | **Ratificación**: 2026-09-06 | **Última modificación**: 2026-09-06
