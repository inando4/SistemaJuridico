package pe.org.beneficencia.legalcontrol.pendingtask;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un pendiente: lo que hay que hacer.
 *
 * <p>El insumo llama a esta «la tabla mas importante del sistema» (seccion 8). Los
 * expedientes de las funcionalidades anteriores son el indice; esto es el trabajo.
 *
 * <p>Puede colgar de un expediente judicial, de uno administrativo o de ninguno
 * —«comprar toner para impresora» es el ejemplo del propio insumo—, pero nunca de
 * los dos a la vez.
 */
public record PendingTask(
        UUID id,
        UUID ownerId,
        String ownerName,
        String title,
        String description,
        UUID pendingTaskTypeId,
        String pendingTaskTypeName,
        UUID priorityId,
        String priorityName,
        UUID pendingTaskStatusId,
        String pendingTaskStatusName,
        UUID judicialCaseId,
        String judicialCaseNumber,
        UUID administrativeProcedureId,
        String administrativeProcedureNumber,
        LocalDate receivedAt,
        LocalDate registeredAt,
        LocalDate scheduledFor,
        LocalDate deadline,
        Instant completedAt,
        String outputDocumentType,
        String outputDocumentNumber,
        String notes,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public boolean cumplido() {
        return completedAt != null;
    }

    /** Numero del expediente del que cuelga, sea de la clase que sea. */
    public String expedienteRelacionado() {
        if (judicialCaseNumber != null) {
            return judicialCaseNumber;
        }
        return administrativeProcedureNumber;
    }

    /** El documento que se emitio al cumplirlo, como texto legible. */
    public String documentoDeSalida() {
        if (outputDocumentType == null && outputDocumentNumber == null) {
            return null;
        }
        if (outputDocumentNumber == null) {
            return outputDocumentType;
        }
        return outputDocumentType == null
                ? outputDocumentNumber
                : outputDocumentType + " N.º " + outputDocumentNumber;
    }
}
