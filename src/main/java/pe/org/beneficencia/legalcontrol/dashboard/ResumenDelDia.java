package pe.org.beneficencia.legalcontrol.dashboard;

/**
 * Las seis cifras del dashboard (insumo, seccion 23), calculadas al abrirlo.
 *
 * <p><b>Nada de esto se almacena</b> (principio V): son valores derivados de los
 * pendientes y del calendario, y guardarlos crearia una segunda verdad que ademas
 * quedaria obsoleta cada medianoche sin que nadie tocara una fila.
 *
 * <p><b>Cero y «no se puede saber» son cosas distintas.</b> Las dos cifras que
 * dependen de contar dias habiles son {@code Integer} y valen null cuando falta
 * cobertura de calendario: en pantalla, «0 proximos vencimientos» tranquiliza y
 * «no se puede calcular» avisa. Presentar lo segundo como lo primero seria mentir
 * con un numero, que es peor que no dar ninguno.
 *
 * <p>Las seis condiciones no son excluyentes: un pendiente vencido y programado
 * para hoy entra en dos tarjetas. La suma no cuadra con ningun total, y la
 * pantalla no debe sugerir que si.
 */
public record ResumenDelDia(
        int urgentesHoy,
        int vencidos,
        Integer proximosVencimientos,
        Integer sinPlazoAntiguos,
        int activos,
        int cumplidosEsteMes) {

    /** true si alguna cifra no pudo calcularse por falta de calendario. */
    public boolean faltaCalendario() {
        return proximosVencimientos == null || sinPlazoAntiguos == null;
    }

    /** true si esta persona no tiene ningun pendiente: la pantalla lo dice. */
    public boolean sinNada() {
        return activos == 0 && cumplidosEsteMes == 0;
    }
}
