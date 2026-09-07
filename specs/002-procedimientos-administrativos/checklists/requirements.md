# Lista de verificación de la especificación: Control de procedimientos administrativos

**Propósito**: validar que la especificación está completa antes de planificar
**Creada**: 2026-09-06
**Funcionalidad**: [spec.md](../spec.md)

## Calidad del contenido

- [X] Sin detalles de implementación (lenguajes, marcos de trabajo, interfaces técnicas)
- [X] Centrada en el valor para el usuario y la necesidad del área
- [X] Redactada para quien no programa
- [X] Todas las secciones obligatorias completas

## Completitud de los requisitos

- [X] No quedan marcadores de aclaración pendiente
- [X] Los requisitos son comprobables y sin ambigüedad
- [X] Los criterios de éxito son medibles
- [X] Los criterios de éxito no mencionan tecnología
- [X] Los escenarios de aceptación están definidos
- [X] Los casos límite están identificados
- [X] El alcance está delimitado
- [X] Dependencias y supuestos identificados

## Preparación de la funcionalidad

- [X] Cada requisito funcional tiene criterio de aceptación claro
- [X] Los escenarios cubren los flujos principales
- [X] La funcionalidad cumple los resultados medibles de los criterios de éxito
- [X] No se filtran detalles de implementación

## Notas

Cobertura del insumo verificada: §7 (campos y cálculo dinámico), §7.1 (catálogo propio),
§29 (listado y columnas), §30 (ficha). La sección «Pendientes relacionados» de la §30 queda
explícitamente fuera por depender de la funcionalidad 003.

Tres supuestos merecen confirmación del cliente antes de construir, aunque ninguno bloquea la
planificación:

1. **Área solicitante como texto libre.** Si el cliente tiene un organigrama estable, podría
   preferir un catálogo. Se optó por texto libre porque el insumo no lo define y una lista
   inventada sería peor que ninguna.
2. **Numeraciones independientes** entre judicial y administrativo. Si en la práctica comparten
   una serie única, la unicidad debería abarcar ambos registros.
3. **La fecha de recepción no se autocompleta** con la del día.
