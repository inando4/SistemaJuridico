# Lista de comprobación de calidad: Trabajo en equipo — vista de carga y asignación

**Propósito**: validar que la especificación está completa antes de pasar a la planificación
**Creada**: 2026-09-08
**Feature**: [spec.md](../spec.md)

## Calidad del contenido

- [x] Sin detalles de implementación (lenguajes, frameworks, API)
- [x] Centrada en el valor para el usuario y la necesidad del área
- [x] Redactada para personas no técnicas
- [x] Todas las secciones obligatorias completas

## Completitud de los requisitos

- [x] No quedan marcadores [NEEDS CLARIFICATION] — el único (RF-025) lo resolvió el cliente el 2026-09-08
- [x] Los requisitos son comprobables y no ambiguos
- [x] Los criterios de éxito son medibles
- [x] Los criterios de éxito no mencionan tecnología
- [x] Los escenarios de aceptación están definidos
- [x] Los casos límite están identificados
- [x] El alcance está delimitado
- [x] Supuestos y dependencias identificados

## Preparación de la feature

- [x] Cada requisito funcional tiene criterio de aceptación claro
- [x] Los escenarios cubren los flujos principales
- [x] La feature cumple los resultados medibles de los criterios de éxito
- [x] No se filtran detalles de implementación

## Conformidad con la constitución 4.0.1

- [x] **I. Idioma**: especificación en español; los mensajes al usuario final se exigen en español
- [x] **II. Multiusuario y atribución**: la vista de equipo es consultable por cualquier rol (RF-021); la reasignación es exclusiva de la jefa y se comprueba en el servidor (RF-003, CE-008); el historial distingue autor de responsable (RF-006)
- [x] **III. Índice físico**: no se introduce almacenamiento documental
- [x] **IV. Ligereza**: presupuesto de consultas fijo e independiente del tamaño del equipo (CE-005) y de tiempo en el equipo de referencia (CE-006)
- [x] **V. Sin derivados persistidos**: la carga de trabajo se declara explícitamente no almacenada (RF-024, CE-009, entidad «Carga de trabajo»)
- [x] **VI. Cálculo centralizado**: la semana hábil usa la función única existente y avisa si el año no está confirmado (RF-023)
- [x] **VII. Trazabilidad inmutable**: entrada por cada registro traspasado (RF-007), responsable anterior y nuevo (RF-006), ausencia de responsable previo en el alta (RF-014), atomicidad (RF-005), sin historial en guardados sin cambios (RF-011)
- [x] **VIII. Un solo proceso**: no se introduce API separada ni framework de JavaScript

## Notas

- **RF-025 resuelto (2026-09-08)**: el cliente confirma que la jefa sí puede reasignar un pendiente suelto de forma individual. Se añadieron RF-025 a RF-028, dos escenarios de aceptación en la Historia 1, dos casos límite y el criterio CE-010.
- **Límite que la respuesta no cubría, fijado en RF-027**: un pendiente que sí cuelga de un expediente no se reasigna por separado. La pregunta al cliente era sobre pendientes sueltos, y extender la respuesta a los vinculados contradiría «el expediente viaja completo» de la sección 5.3. Queda anotado por si el área quiere revisarlo.
- El `REVOKE` pendiente sobre `flyway_schema_history` **no** forma parte de esta especificación. Es una corrección de permisos sin relación con las secciones 5.2 y 5.3; corresponde anotarla en el plan si esta feature acaba necesitando migración propia.
