package pe.org.beneficencia.legalcontrol.stickynote;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import pe.org.beneficencia.legalcontrol.config.ClockConfig;

/**
 * Un recordatorio pegado en el panel: texto libre, sin plazo ni estado.
 *
 * <p>Solo se leen los que siguen pegados, asi que el registro no lleva
 * {@code removedAt}: en pantalla nunca aparece uno quitado.
 */
public record StickyNote(UUID id, String content, NoteColor color, Instant createdAt) {

    /**
     * Cuanto lleva pegado, en dias naturales (principio V).
     *
     * <p>No son dias habiles a proposito: un recordatorio no es un plazo, y
     * contarlo como tal lo haria parecer un vencimiento.
     */
    public String antiguedad(LocalDate hoy) {
        long dias = ChronoUnit.DAYS.between(
                LocalDate.ofInstant(createdAt, ClockConfig.ZONA), hoy);
        if (dias <= 0) {
            return "Anotado hoy";
        }
        if (dias == 1) {
            return "Anotado ayer";
        }
        return "Anotado hace " + dias + " días";
    }
}
