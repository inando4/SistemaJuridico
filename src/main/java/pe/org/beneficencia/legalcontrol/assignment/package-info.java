/**
 * Cambio de responsable de expedientes y pendientes (insumo, seccion 5.3).
 *
 * <p><b>Por que un paquete propio y no un metodo en cada dominio.</b> Reasignar es
 * <em>una</em> operacion con tres puntos de entrada: expediente judicial,
 * procedimiento administrativo y pendiente suelto. Repartida entre
 * {@code judicialcase}, {@code administrativeprocedure} y {@code pendingtask} se
 * escribiria tres veces, y la tercera copia es la que se queda sin la ultima
 * correccion. Aqui viven la transaccion, la comprobacion de permisos y el
 * historial; los controladores de dominio solo invocan.
 *
 * <p>La regla que gobierna todo el paquete: <b>o cambia todo, o no cambia nada</b>.
 * Una reasignacion a medias deja pendientes cuyo responsable ya no tiene el
 * expediente, y con ello permiso de escritura en manos equivocadas.
 */
package pe.org.beneficencia.legalcontrol.assignment;
