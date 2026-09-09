package pe.org.beneficencia.legalcontrol.activity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Trabajo realizado que nunca paso por un pendiente.
 *
 * <p>El tipo es opcional y tiene dos formas excluyentes: {@code pendingTaskTypeId}
 * apunta al catalogo que la jefa administra —y sigue sus renombrados—, mientras que
 * {@code otherType} es lo que alguien escribio ese dia y no cambia nunca, porque
 * nadie lo administra. Poder distinguirlas es lo que evita que un recuento futuro
 * los mezcle sin advertirlo.
 *
 * @param performedOn el dia en que se hizo el trabajo, no el dia del registro
 */
public record ManualActivity(
        UUID id,
        UUID ownerId,
        String ownerName,
        LocalDate performedOn,
        String description,
        UUID pendingTaskTypeId,
        String pendingTaskTypeName,
        String otherType,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    /** El tipo tal como se muestra, venga del catalogo o escrito a mano. */
    public String tipo() {
        return pendingTaskTypeName != null ? pendingTaskTypeName : otherType;
    }

    public boolean tieneTipo() {
        return tipo() != null && !tipo().isBlank();
    }

    /** ¿El tipo lo escribio alguien, en vez de elegirlo del catalogo? */
    public boolean tipoEscritoAMano() {
        return pendingTaskTypeId == null && otherType != null && !otherType.isBlank();
    }
}
