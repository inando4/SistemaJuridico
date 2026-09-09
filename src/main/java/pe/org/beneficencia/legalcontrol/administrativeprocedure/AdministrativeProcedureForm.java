package pe.org.beneficencia.legalcontrol.administrativeprocedure;

import java.util.UUID;

/**
 * Lo que el formulario acepta.
 *
 * <p>No incluye responsable ni identificadores tecnicos: el responsable lo fija
 * el servidor con quien registra. Un intento de asignar a otra persona se rechaza
 * en vez de ignorarse en silencio.
 */
public record AdministrativeProcedureForm(
        String sequenceNumber,
        String fileNumber,
        String requestingArea,
        String request,
        UUID administrativeStatusId,
        String receivedAt,
        String deadline,
        String notes,
        Long version) {

    public static AdministrativeProcedureForm nuevo() {
        return new AdministrativeProcedureForm(null, null, null, null, null,
                null, null, null, null);
    }

    public AdministrativeProcedureForm withVersion(Long nueva) {
        return new AdministrativeProcedureForm(sequenceNumber, fileNumber, requestingArea,
                request, administrativeStatusId, receivedAt, deadline, notes, nueva);
    }
}
