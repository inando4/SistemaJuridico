# Lista de calidad: Acceso y control de procesos judiciales con plazos

**Propósito**: Validar integridad y calidad de la especificación antes de planificar.
**Creada**: 2026-09-06
**Última revisión del insumo**: 2026-09-06
**Funcionalidad**: [Especificación](../spec.md)
**Responsable de revisión**: Revisión de requisitos realizada por el agente en este flujo.
**Significado**: Una marca completada acredita calidad del requisito, no implementación.

## Calidad del contenido

- [x] CHK001 Sin detalles de implementación: lenguajes, frameworks o interfaces técnicas.
- [x] CHK002 Centrada en valor para el usuario y necesidades del negocio.
- [x] CHK003 Escrita en español para personas no técnicas.
- [x] CHK004 Todas las secciones obligatorias completas.

## Integridad de requisitos

- [x] CHK005 Sin marcadores de aclaración pendientes.
- [x] CHK006 Requisitos verificables y sin ambigüedades operativas.
- [x] CHK007 Criterios de éxito medibles.
- [x] CHK008 Criterios de éxito independientes de la tecnología.
- [x] CHK009 Escenarios de aceptación definidos.
- [x] CHK010 Casos límite identificados.
- [x] CHK011 Alcance expresamente delimitado.
- [x] CHK012 Dependencias y supuestos identificados.

## Preparación de la funcionalidad

- [x] CHK013 Todos los requisitos funcionales tienen criterios de aceptación claros.
- [x] CHK014 Las historias cubren los recorridos principales.
- [x] CHK015 Los resultados definidos permiten evaluar el éxito de la funcionalidad.
- [x] CHK016 No se filtran decisiones de implementación en la especificación.

## Notas

- Revisión de las ocho enmiendas completada el 2026-09-06: 16/16 controles de calidad,
  30 requisitos, 12 criterios de éxito y 7 historias, tras las aclaraciones de usuarios. No acredita implementación.
- Auditoría: FR-011 y FR-012, historia 3.4 y SC-005 permiten evidencia solo de cambios;
  no quedan cláusulas de lectura recursiva. Consultas y guardados sin cambios generan cero entradas.
- Cobertura: FR-018, historia 5.4–7 y SC-009 exigen declaración no preseleccionada de
  revisión completa, total actual visible y reconocimiento de advertencia con 1–4 días.
  Cero impide confirmar; cinco no confirma automáticamente. Se cubren cambios concurrentes.
- Visibilidad y situación procesal: FR-007–008 y FR-023, historias 2.7 y 3.8 y SC-012
  separan visible/oculto de en trámite/concluido, con filtros combinables y cambios independientes.
- Duplicados: FR-006, historia 2.6 y SC-008 rechazan coincidencias normalizadas entre
  responsables, registros ocultos y altas concurrentes. No existe excepción por confirmación.
- Sesión: FR-002, historia 1.5–6 y SC-011 establecen 4 h de inactividad y 12 h absolutas
  desde el ingreso, no renovables por uso, con revocación por cierre o cambio de
  credenciales. Es un supuesto explícito de producto: un único ingreso por jornada sin que
  la sesión sobreviva a la noche, para que el historial siga identificando a la persona.
- Exactitud: FR-014–016 y SC-004 fijan hoy excluido, límite hábil incluido y fecha civil
  de Arequipa. Se verificaron las 12 filas de historia 4 mediante enumeración independiente
  de fechas; también la transición de medianoche usando America/Lima. Se exige cero
  discrepancias, sin afirmar que se haya probado aún código de la aplicación.
- Usuarios: FR-024–026, FR-025b, historia 6 y SC-010–011 cubren alta por JEFA, activación, códigos
  de recuperación de un solo uso, límites, mensajes genéricos y revocación de sesiones.
  Sin dependencia de correo: entrega presencial de códigos y preparación de la primera JEFA.
- Estados: FR-027–028, historia 7 y SC-010/012 cubren crear, consultar, editar y eliminar
  estados sin uso; los referenciados se deshabilitan y conservan el historial. El catálogo
  puede arrancar vacío. JEFA lo administra y ABOGADO lo consulta y usa.
- Trazabilidad adicional: FR-001 corresponde a historia 1; FR-003–005 a historia 2;
  FR-009–013 a historias 2–3; FR-017–020 a historias 4–5; FR-021 a SC-001–002 y escenarios
  de validación; FR-022 a SC-007 y verificación operativa de acceso público.
- Alcance actualizado: incluye alta de usuarios, recuperación y catálogo de estados,
  anteriormente excluidos. Asignación, pendientes, otros catálogos, exportación y demás
  exclusiones permanecen fuera; Excel sigue sin importación en todo el proyecto.
- Sincronización documental completada: constitución 4.0.1, principios II y VII y controles,
  exige historial solo para modificaciones. La spec y el plan usan la versión vigente.
- Aclaraciones de usuarios integradas: FR-029–030 e historia 6 cubren desactivación,
  reactivación con contraseña nueva y protección concurrente de la última JEFA activa.
- Supuestos revisables: sesión de jornada (4 h/12 h), administración por JEFA, entrega presencial
  de códigos, sus plazos, moneda y presupuesto de rendimiento. No hay aclaraciones bloqueantes.
- Sin hooks antes ni después de la especificación. Se conserva el directorio 001 y
  `.specify/feature.json`. Planificación realizada con constitución 4.0.1; los
  controles documentales no acreditan implementación ni pruebas de software.
