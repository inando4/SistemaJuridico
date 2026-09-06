package pe.org.beneficencia.legalcontrol.shared;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Distingue una peticion de HTMX de una navegacion normal.
 *
 * <p>Cuando HTMX pide, se devuelve solo el fragmento que cambia; cuando el
 * navegador navega, la pagina completa. Es lo que permite que marcar algo no
 * repinte toda la pantalla en una laptop lenta (principio IV).
 *
 * <p>La aplicacion debe funcionar sin JavaScript: si HTMX no carga, los mismos
 * formularios siguen siendo GET/POST normales y devuelven la pagina entera.
 */
public final class HtmxSupport {

    private static final String CABECERA_PETICION = "HX-Request";
    private static final String CABECERA_RESTAURACION = "HX-History-Restore-Request";

    private HtmxSupport() {
    }

    public static boolean esFragmento(HttpServletRequest peticion) {
        // Una restauracion de historial necesita la pagina completa, no un fragmento.
        return "true".equals(peticion.getHeader(CABECERA_PETICION))
                && !"true".equals(peticion.getHeader(CABECERA_RESTAURACION));
    }

    /** Devuelve la vista de fragmento o la completa segun quien pregunte. */
    public static String vista(HttpServletRequest peticion, String completa, String fragmento) {
        return esFragmento(peticion) ? fragmento : completa;
    }
}
