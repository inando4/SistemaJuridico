# Lista de comprobación de calidad: Calendario, actividad diaria y buscador global

**Propósito**: validar que la especificación está completa antes de planificar
**Creada**: 2026-09-08
**Feature**: [spec.md](../spec.md)

## Calidad del contenido

- [x] Sin detalles de implementación (lenguajes, marcos, API)
- [x] Centrada en el valor para el usuario y la necesidad del área
- [x] Escrita para quien no programa
- [x] Todas las secciones obligatorias completas

## Completitud de los requisitos

- [x] No quedan marcas [NEEDS CLARIFICATION] — RF-015 resuelto
- [x] Los requisitos son comprobables y no ambiguos
- [x] Los criterios de éxito son medibles
- [x] Los criterios de éxito no mencionan tecnología
- [x] Los escenarios de aceptación están definidos
- [x] Los casos límite están identificados
- [x] El alcance está acotado
- [x] Dependencias y supuestos identificados

## Preparación

- [x] Cada requisito funcional tiene criterio de aceptación
- [x] Las historias cubren los recorridos principales
- [x] La feature cumple los resultados medibles definidos
- [x] No se filtran detalles de implementación

## Notas

- **RF-015 quedó resuelto por el usuario**: descripción y fecha obligatorias, y **tipo opcional** —del catálogo, en blanco, o «Otro» escrito a mano—. Se eligió la libertad sobre la uniformidad a sabiendas de que el texto libre fragmenta los recuentos; queda razonado en Supuestos, junto con la vía para corregirlo después (convertir en tipos de catálogo los libres que se repitan). RF-015b acota el riesgo real: escribir un tipo **no** amplía el catálogo.
- **Tres decisiones de alcance se resolvieron sin preguntar**, y quedan escritas en Supuestos para que se puedan revisar:
  1. Los **recordatorios** de la sección 31 quedan fuera: el propio insumo los sitúa en la FASE 3 y no los define en ninguna parte. La sección se contesta a sí misma.
  2. Las **audiencias** no son entidad nueva: son un tipo de pendiente que el catálogo ya trae.
  3. Las **actuaciones** se toman de la fecha de última actuación judicial, el único dato de fecha que existe sobre ellas.
- **Hallazgo comprobado contra el código** (no de memoria), que convierte la sección 34 en un requisito y no en un acabado: ninguno de los tres filtros de texto actuales cubre lo que el insumo enumera.

  | Listado | Busca hoy | Falta según el insumo |
  |---|---|---|
  | Judiciales | `case_number`, `claimant`, `respondent` | **materia**, **observaciones** |
  | Administrativos | `file_number`, `requesting_area`, `request` | **observaciones** |
  | Pendientes | `title`, `description` | **observaciones** |

  Las tres columnas que faltan **existen** en el esquema. Es un hueco de la consulta, no del modelo. RF-012 exige cerrarlo también en los listados, para que la misma palabra no dé dos resultados distintos según dónde se escriba.
- **Cuatro requisitos se corrigieron tras la primera revisión**, porque la lista de comprobación los había dado por buenos:
  - **RF-006** exigía encontrar el registro sin tildes. El insumo no lo pide, y era el único requisito que la forma de consulta actual no puede cumplir: `ILIKE` ignora mayúsculas pero **no** tildes. Cumplirlo obliga a la extensión `unaccent`, una envoltura inmutable y un índice funcional por campo. Se acotó a mayúsculas/minúsculas y la exclusión quedó razonada en Supuestos.
  - **CE-005** afirmaba que un día pasado devuelve siempre lo mismo, y RF-016 (actividad manual con fecha anterior) lo desmiente. Se reescribió como lo que de verdad se quiere garantizar: no hay foto guardada, la pantalla refleja el estado actual.
  - **RF-005** pedía el total exacto por grupo y **RF-009** solo «si hay más». Son cosas distintas y la primera cuesta una consulta por grupo. Se alinearon en la barata.
  - **RF-012** cambia el comportamiento de tres pantallas en producción que la sección 34 nunca menciona. Estaba solo implícito en un número de requisito; ahora se nombra en Supuestos, con aviso de que las pruebas existentes entran en el alcance.
