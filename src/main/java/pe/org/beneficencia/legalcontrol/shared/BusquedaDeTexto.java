package pe.org.beneficencia.legalcontrol.shared;

/**
 * Tratamiento del termino que el usuario escribe para buscar.
 *
 * <p>Existe para que el buscador global y los tres listados traten el termino
 * exactamente igual. Cuando la 006 amplio los campos de busqueda (RF-012), el
 * riesgo no era olvidar una columna sino que el escape divergiera: buscar
 * {@code 100%} devolveria todo en un sitio y lo correcto en otro, y la diferencia
 * solo se veria con un termino que casi nadie escribe.
 *
 * <p>Los tres repositorios tenian una copia identica del escape. Ahora la comparten.
 */
public final class BusquedaDeTexto {

    /**
     * Caracteres minimos para buscar.
     *
     * <p>Con menos, {@code %a%} coincide con casi todo: la pantalla seria inutil y
     * la consulta un recorrido completo de tres tablas para nada.
     */
    public static final int MINIMO = 3;

    private BusquedaDeTexto() {
    }

    /** ¿Vale la pena consultar con este termino? */
    public static boolean suficiente(String termino) {
        return termino != null && termino.strip().length() >= MINIMO;
    }

    /**
     * Neutraliza los comodines de {@code LIKE} para que el termino sea texto literal.
     *
     * <p>La contrabarra va primero: si se escapara despues, doblaria las que los otros
     * dos reemplazos acaban de introducir.
     */
    public static String escapar(String valor) {
        return valor.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** El termino listo para un {@code ILIKE :q ESCAPE '\'}. */
    public static String comodin(String termino) {
        return "%" + escapar(termino.strip()) + "%";
    }
}
