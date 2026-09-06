package pe.org.beneficencia.legalcontrol.judicialcase;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Indice de control de un expediente judicial fisico.
 *
 * <p>No guarda documentos: el expediente vive en el archivo de la oficina y esto
 * solo registra su informacion de control.
 *
 * <p>{@code active} es visibilidad en el listado corriente y nada mas. Que un
 * expediente este «Concluido» es una situacion procesal y vive en
 * {@code proceduralStatusId}: un concluido puede seguir visible y uno en tramite
 * puede estar oculto (FR-023).
 */
public record JudicialCase(
        UUID id,
        Integer sequenceNumber,
        UUID ownerId,
        String ownerName,
        String caseNumber,
        String claimant,
        String respondent,
        String subject,
        UUID proceduralStatusId,
        String proceduralStatusName,
        String lastProceduralAction,
        String nextProceduralAction,
        LocalDate lastActionDate,
        LocalDate deadline,
        BigDecimal amount,
        String propertyAddress,
        String notes,
        String managementActions,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
