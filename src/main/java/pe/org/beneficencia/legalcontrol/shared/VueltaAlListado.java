package pe.org.beneficencia.legalcontrol.shared;

import java.util.regex.Pattern;

/**
 * A donde vuelve una accion ejecutada desde una fila de un listado.
 *
 * <p><b>El destino se escribe en el codigo; del usuario solo llega la cadena de
 * consulta.</b> Pasar la ruta entera en un parametro —el clasico {@code volverA=}— es
 * la forma canonica del redirect abierto: basta con que alguien ponga ahi una
 * direccion externa. Con la base fija, el destino no se puede desviar por definicion.
 *
 * <p>La cadena se valida igualmente. Hoy {@code /pendientes//algo} sigue siendo una
 * ruta propia y no es explotable, pero una cadena sin comprobar pegada a una cabecera
 * {@code Location} es la forma que se convierte en un fallo de verdad en cuanto
 * alguien cambie la base. Una cadena que no encaja se descarta en silencio y se vuelve
 * al listado sin filtros: perder los filtros molesta, pero es preferible a un error en
 * mitad de una accion que si se ejecuto.
 */
public final class VueltaAlListado {

    /**
     * Lo que puede contener una cadena de consulta nuestra: los caracteres que produce
     * {@code URLEncoder.encode} mas los separadores. Ni espacios, ni saltos de linea,
     * ni {@code :} ni {@code /}, que son los que harian falta para escribir otra
     * direccion.
     */
    private static final Pattern ACEPTADA = Pattern.compile("^\\?[A-Za-z0-9=&_%.\\-]*$");

    private VueltaAlListado() {
    }

    /**
     * @param base    ruta de destino, <b>siempre</b> una constante del codigo
     * @param consulta cadena recibida del formulario, o nula
     */
    public static String a(String base, String consulta) {
        if (consulta == null || consulta.isBlank() || !ACEPTADA.matcher(consulta).matches()) {
            return "redirect:" + base;
        }
        return "redirect:" + base + consulta;
    }
}
