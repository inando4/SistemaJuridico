# Lista de comprobación de la especificación: Pendientes relacionados en la ficha del expediente

**Propósito**: validar que la especificación está completa antes de planificar
**Creada**: 2026-09-08
**Feature**: [spec.md](../spec.md)

## Calidad del contenido

- [X] Sin detalles de implementación (lenguajes, marcos, API)
- [X] Centrada en el valor para el usuario y la necesidad del negocio
- [X] Escrita para quien no programa
- [X] Todas las secciones obligatorias completas

## Completitud de los requisitos

- [X] No quedan marcadores [NEEDS CLARIFICATION]
- [X] Los requisitos son comprobables y sin ambigüedad
- [X] Los criterios de éxito son medibles
- [X] Los criterios de éxito no dependen de la tecnología
- [X] Todos los recorridos de aceptación están definidos
- [X] Los casos límite están identificados
- [X] El alcance está acotado
- [X] Dependencias y supuestos identificados

## Preparación de la feature

- [X] Cada requisito funcional tiene un criterio de aceptación claro
- [X] Los recorridos de usuario cubren los flujos principales
- [X] La feature cumple los resultados medibles de los criterios de éxito
- [X] Ningún detalle de implementación se cuela en la especificación

## Notas de la validación

Tres puntos que se revisaron y quedaron resueltos en el texto:

1. **CE-004 y CE-005 miden cosas distintas y ambas hacen falta.** CE-005 fija el coste
   (una consulta más por ficha); CE-004 fija que ese coste no crece con el número de
   pendientes. Solo con CE-005 pasaría una implementación que consulta el responsable
   fila por fila.

2. **RF-002 y RF-007 no se contradicen.** «Cumplido» y «archivado» son estados
   distintos: el cumplido se muestra en la ficha porque es historia del expediente;
   el archivado no, porque se retiró a propósito. CE-003 compara «el mismo estado de
   visibilidad» justamente por esto.

3. **La consecuencia de RF-015 se declara en su propia sección** en lugar de esconderse
   en un supuesto: la feature no crea el riesgo de crear pendientes propios en
   expedientes ajenos, pero sí lo hace más fácil de alcanzar, y eso merece constar.

## Verificación contra el insumo

| Lo que pide el insumo | Dónde queda |
|---|---|
| §28 «Pendientes relacionados» en la ficha judicial | RF-001, historia 1 |
| §28 «+ Crear nuevo pendiente relacionado» | RF-012, historia 2 |
| §30 «Pendientes relacionados» en la ficha administrativa | RF-001, historia 1 (recorrido 5) |
| §9 vínculo a un judicial **o** a un administrativo, nunca a los dos | RF-014 |
| §9 vínculo múltiple aplazado a más adelante | Fuera de alcance |
