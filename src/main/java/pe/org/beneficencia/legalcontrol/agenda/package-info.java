/**
 * El calendario de eventos (insumo, seccion 31).
 *
 * <p><b>Por que este paquete se llama {@code agenda} y no {@code calendar}.</b> El
 * paquete {@code calendar} ya existe y significa otra cosa: el calendario <b>de dias
 * no laborables</b>, con {@code CalendarController} sirviendo {@code /dias-no-laborables}.
 * Uno decide que dias cuentan para un plazo; este dibuja una rejilla con lo que ocurre
 * cada dia. Poner los dos en el mismo paquete dejaria dos clases de nombre casi
 * identico haciendo cosas sin relacion. La ruta que ve el usuario sigue siendo
 * {@code /calendario}, que es lo que pide el insumo.
 *
 * <p><b>Ningun evento se almacena</b> (principio V). Todos salen de fechas que ya
 * estan guardadas en los registros: la programacion y el vencimiento de un pendiente,
 * el vencimiento de un expediente, la fecha de la ultima actuacion judicial.
 *
 * <p><b>Una consulta por rango, no una por dia.</b> Es lo que hace que el mes cueste
 * lo mismo que el dia. Preguntar dia a dia serian 31 viajes para un mes, y con datos
 * de prueba no se nota.
 *
 * <p>Los <b>recordatorios</b> que la seccion 31 enumera quedan fuera: el insumo no los
 * define en ninguna otra parte y si los sitúa en la FASE 3. No existe ninguna fecha
 * guardada que mostrar.
 */
package pe.org.beneficencia.legalcontrol.agenda;
