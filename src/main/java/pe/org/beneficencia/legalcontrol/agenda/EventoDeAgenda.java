package pe.org.beneficencia.legalcontrol.agenda;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Una fecha con significado, tomada de un registro que ya la tiene.
 *
 * <p>Nunca se persiste. Un mismo pendiente puede producir <b>dos</b> eventos —su
 * programacion y su vencimiento— si las dos fechas caen en el rango: no es un
 * duplicado, son dos hechos en dos dias.
 *
 * @param tipoDePendiente el nombre del tipo de catalogo, cuando el evento viene de un
 *                        pendiente. Asi una audiencia se ve como audiencia <b>sin</b>
 *                        que el codigo compare con la cadena «Audiencia», que se
 *                        romperia el dia que la jefa renombre el valor
 */
public record EventoDeAgenda(
        LocalDate dia,
        String tipo,
        String entidad,
        UUID entidadId,
        String titulo,
        String tipoDePendiente,
        UUID ownerId,
        String responsable) {

    public static final String PROGRAMADO = "PROGRAMADO";
    public static final String VENCIMIENTO = "VENCIMIENTO";
    public static final String ACTUACION = "ACTUACION";

    public static final String PENDIENTE = "PENDING_TASK";
    public static final String JUDICIAL = "JUDICIAL_CASE";
    public static final String ADMINISTRATIVO = "ADMINISTRATIVE_PROCEDURE";

    /** La ruta de la ficha del registro del que sale el evento. */
    public String enlace() {
        return switch (entidad) {
            case PENDIENTE -> "/pendientes/" + entidadId;
            case JUDICIAL -> "/judiciales/" + entidadId;
            case ADMINISTRATIVO -> "/administrativos/" + entidadId;
            default -> "/";
        };
    }
}
