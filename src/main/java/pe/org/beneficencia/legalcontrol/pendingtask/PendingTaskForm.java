package pe.org.beneficencia.legalcontrol.pendingtask;

import java.util.UUID;

/**
 * Lo que el formulario acepta.
 *
 * <p>Sin responsable: lo fija el servidor con quien registra.
 */
public record PendingTaskForm(
        String title,
        String description,
        UUID pendingTaskTypeId,
        UUID priorityId,
        UUID pendingTaskStatusId,
        UUID judicialCaseId,
        UUID administrativeProcedureId,
        String receivedAt,
        String scheduledFor,
        String deadline,
        String outputDocumentType,
        String outputDocumentNumber,
        String notes,
        Long version) {

    public static PendingTaskForm nuevo() {
        return new PendingTaskForm(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    /** ¿Se intento vincular a los dos expedientes a la vez? */
    public boolean vinculoDoble() {
        return judicialCaseId != null && administrativeProcedureId != null;
    }

    public PendingTaskForm withVersion(Long nueva) {
        return new PendingTaskForm(title, description, pendingTaskTypeId, priorityId,
                pendingTaskStatusId, judicialCaseId, administrativeProcedureId, receivedAt,
                scheduledFor, deadline, outputDocumentType, outputDocumentNumber, notes, nueva);
    }
}
