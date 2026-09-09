/**
 * Buscador global (insumo, seccion 34).
 *
 * <p>Responde a «llamaron preguntando por un caso y solo recuerdo el apellido».
 * Antes habia que abrir tres listados y probar en cada uno, y ninguno de los tres
 * miraba todos los campos que el insumo enumera: en judiciales faltaban materia y
 * observaciones, y en los otros dos las observaciones.
 *
 * <p><b>Tres consultas, una por tipo, y ese numero no cambia con el resultado.</b>
 * Se descarto unirlas: las tres tablas no comparten columnas y un {@code UNION}
 * obligaria a rellenar con nulos y perderia la paginacion por grupo, que es lo que
 * deja avanzar en judiciales sin mover los otros dos.
 *
 * <p><b>Llega mas lejos que los listados, a proposito.</b> Un listado es una lista
 * de trabajo y muestra lo activo; el buscador alcanza tambien los archivados y los
 * señala, porque el caso por el que preguntan puede estar cerrado. Lo que los dos
 * comparten es en que columnas buscan, no el filtro de visibilidad.
 */
package pe.org.beneficencia.legalcontrol.search;
