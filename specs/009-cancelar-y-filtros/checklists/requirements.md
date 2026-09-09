# Lista de comprobación de la especificación: Cancelar registros y los filtros que faltan

**Propósito**: validar que la especificación está completa antes de planificar
**Creada**: 2026-09-09
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

**Q1 resuelta el 2026-09-09**: las «acciones rápidas» de la sección 25 van **en la fila
del listado**. Se llevan las cuatro que se resuelven con un clic o un enlace —abrir,
editar, cumplir, cancelar— y se quedan en la ficha «No cumplido» y «Reprogramar», que
piden una fecha o un motivo y dentro de una tabla obligarían a desplegar un formulario.
Quedó como historia 2 (P2).

Cuatro puntos revisados y resueltos en el texto:

1. **RF-005 (sin motivo) y CE-003 (todo deja rastro) no se contradicen.** Que no se pida
   una justificación escrita no quita que quede registrado quién canceló y cuándo. El
   motivo es dato del usuario; el rastro es obligación del sistema.

2. **RF-008 se declara explícitamente** porque la relación invita a suponer lo contrario.
   Ocultar un expediente con doce pendientes activos no puede cancelarlos en cascada: eso
   retiraría trabajo de otras personas sin que nadie lo decidiera.

3. **El supuesto 3 admite que ocultar expedientes no lo pide el insumo.** Se hace porque
   la acción ya está construida en el servidor y una ruta sin salida en pantalla es un
   riesgo mayor que la funcionalidad que le falta.

4. **RF-013 y RF-014 no son adorno.** Sin ellos, la fila ofrecería a cualquiera botones
   que el servidor va a rechazar, y la primera vez que alguien pulse uno pensará que el
   sistema falla. La comprobación de verdad sigue estando en el servidor: la fila decide
   qué se pinta, no quién puede.

## Verificación contra el insumo

| Lo que pide el insumo | Dónde queda |
|---|---|
| §25 acción rápida «Cancelar» | RF-001, historia 1 |
| §25 acciones Ver, Editar, Cumplido | RF-010, historia 2 (a la fila) |
| §25 acciones No cumplido, Reprogramar | RF-011 (se quedan en la ficha, con su razón) |
| §27 filtro por abogado responsable | RF-010 |
| §27 filtro por estado procesal | RF-010 |
| §27 filtro por materia | RF-010 |
| §27 filtro por vencidos | RF-010 |
| §27 filtros con y sin fecha límite | ya existían |
