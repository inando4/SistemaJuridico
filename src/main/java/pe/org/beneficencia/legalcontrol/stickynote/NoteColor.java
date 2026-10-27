package pe.org.beneficencia.legalcontrol.stickynote;

import java.util.Arrays;
import java.util.Optional;

/**
 * El color del papel de un recordatorio.
 *
 * <p>Es solo apariencia que elige su autor: no clasifica ni significa nada para el
 * sistema. Por eso es una lista fija y no un catalogo administrable, y por eso
 * ninguno coincide con los tonos de estado (urgente, aviso, ok).
 *
 * @param css      sufijo de la clase {@code nota-*} de las hojas de estilo
 * @param etiqueta nombre que se lee en pantalla y con lector de pantalla
 */
public enum NoteColor {
    YELLOW("amarillo", "Amarillo"),
    PINK("rosa", "Rosa"),
    GREEN("verde", "Verde"),
    BLUE("celeste", "Celeste"),
    PURPLE("lila", "Lila");

    private final String css;
    private final String etiqueta;

    NoteColor(String css, String etiqueta) {
        this.css = css;
        this.etiqueta = etiqueta;
    }

    public String css() {
        return css;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** @return vacio si el valor no es ninguno de la lista */
    public static Optional<NoteColor> desde(String valor) {
        return Arrays.stream(values()).filter(c -> c.name().equals(valor)).findFirst();
    }
}
