package pe.org.beneficencia.legalcontrol.dashboard;

/**
 * Los cinco niveles de urgencia (insumo, secciones 24 y 35).
 *
 * <p><b>Enumeracion y no catalogo administrable.</b> Los cinco los fija el insumo
 * y no son una decision del area, a diferencia de tipos, prioridades y estados,
 * que si se administran desde la aplicacion porque cambian con el trabajo.
 *
 * <p>El orden de las constantes es el de urgencia, y es el que usa la pantalla.
 * Los niveles 2 y 3 comparten etiqueta —ambos son «Urgente» para quien mira— pero
 * se distinguen al ordenar: un plazo que vence hoy pesa mas que una tarea
 * programada para hoy.
 */
public enum NivelDeAlerta {

    VENCIDO(1, "Vencido"),
    VENCE_HOY(2, "Urgente"),
    PROGRAMADO_HOY(3, "Urgente"),
    PROXIMO_VENCIMIENTO(4, "Proximo vencimiento"),
    ANTIGUO_SIN_PLAZO(5, "Pendiente antiguo");

    private final int orden;
    private final String etiqueta;

    NivelDeAlerta(int orden, String etiqueta) {
        this.orden = orden;
        this.etiqueta = etiqueta;
    }

    public int orden() {
        return orden;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** Traduce el numero que devuelve la consulta al nivel correspondiente. */
    public static NivelDeAlerta desdeOrden(int orden) {
        for (NivelDeAlerta n : values()) {
            if (n.orden == orden) {
                return n;
            }
        }
        throw new IllegalArgumentException("Nivel de alerta desconocido: " + orden);
    }
}
