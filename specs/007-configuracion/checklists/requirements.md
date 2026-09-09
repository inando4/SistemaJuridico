# Lista de comprobación de calidad: Configuración

**Propósito**: validar que la especificación está completa antes de planificar
**Creada**: 2026-09-08
**Feature**: [spec.md](../spec.md)

## Calidad del contenido

- [x] Sin detalles de implementación
- [x] Centrada en el valor para el usuario
- [x] Escrita para quien no programa
- [x] Todas las secciones obligatorias completas

## Completitud de los requisitos

- [x] No quedan marcas [NEEDS CLARIFICATION]
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

- **Sin preguntas abiertas.** Es la única de las tres que quedan de FASE 2 que no necesita respuesta del cliente: §38 (exportación) y §39 (estadísticas) sí.
- **Una sola historia, a propósito.** Partirla en tres daría fases que no se pueden probar por separado: media página de enlaces no es un incremento entregable.
- **El hallazgo que justifica la feature se comprobó plantilla por plantilla**, no de memoria: `grep` de `@{/tipos-de-pendiente}` y los otros seis destinos sobre `src/main/resources/templates`. Cinco no aparecen en ninguna plantilla; `/usuarios` solo en sus propias subpáginas.
- **Dos decisiones que conviene revisar** porque van más allá de lo que §36 enumera, y quedan razonadas en Supuestos:
  1. Se incluye el enlace a **cuentas de usuario**, que la sección no lista.
  2. Ese enlace **se oculta** a quien no es jefa, mientras que los demás no. Refleja una asimetría real del sistema: los catálogos dejan leer a todos, `/usuarios` no.
