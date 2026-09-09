/**
 * «¿Que hice hoy?» (insumo, seccion 33).
 *
 * <p>La pantalla tiene dos mitades de naturaleza distinta, y esa distincion es lo
 * unico que hay que entender de este paquete:
 *
 * <ul>
 *   <li><b>Lo cumplido se calcula.</b> Son los pendientes con {@code completed_at}
 *       en el dia consultado. Ya estan guardados: copiarlos aqui seria el derivado
 *       que prohibe el principio V, y bastaria revertir un cumplimiento para que las
 *       dos versiones dejaran de coincidir.
 *   <li><b>La actividad manual se guarda.</b> Es trabajo que nunca fue un pendiente
 *       —una consulta atendida, una reunion, un tramite resuelto sobre la marcha— y
 *       no deriva de nada: si no se escribe, no existe en ninguna parte.
 * </ul>
 *
 * <p>Consecuencia que conviene tener presente: consultar un dia pasado dos veces
 * puede dar resultados distintos, si entre medias alguien revirtio un cumplido o
 * registro una actividad con fecha anterior. <b>Eso es lo correcto</b>: la pantalla
 * refleja el estado actual de los registros, no una foto tomada aquel dia.
 *
 * <p>No confundir con {@code /cumplidos} (seccion 32), que responde «que se ha
 * cumplido» a lo largo del tiempo y con sus filtros. Esta responde «que hice ese
 * dia», e incluye lo que nunca fue un pendiente. Sin esa segunda mitad serian la
 * misma pantalla con distinto filtro.
 */
package pe.org.beneficencia.legalcontrol.activity;
