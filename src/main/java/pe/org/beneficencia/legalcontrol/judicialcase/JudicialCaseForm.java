package pe.org.beneficencia.legalcontrol.judicialcase;

import java.util.UUID;

/**
 * Lo que el formulario acepta del usuario.
 *
 * <p><b>No incluye {@code ownerId}, ni identificadores tecnicos, ni tiempos.</b>
 * El responsable lo fija el servidor con el usuario que crea el expediente; la
 * asignacion entre abogados es funcionalidad posterior. Si llegara un intento de
 * asignar a otra persona, se rechaza explicitamente en vez de ignorarlo en
 * silencio: quien lo intento debe enterarse de que no se hizo.
 */
public record JudicialCaseForm(
        String sequenceNumber,
        String caseNumber,
        String claimant,
        String respondent,
        String subject,
        UUID proceduralStatusId,
        String lastProceduralAction,
        String nextProceduralAction,
        String lastActionDate,
        String deadline,
        String amount,
        String propertyAddress,
        String notes,
        String managementActions,
        Boolean active,
        Long version) {

    /** Copia con otra version, para editar sobre un formulario ya compuesto. */
    public JudicialCaseForm withVersion(Long nueva) {
        return new JudicialCaseForm(sequenceNumber, caseNumber, claimant, respondent, subject,
                proceduralStatusId, lastProceduralAction, nextProceduralAction, lastActionDate,
                deadline, amount, propertyAddress, notes, managementActions, active, nueva);
    }

    /** Formulario vacio para un alta nueva. */
    public static JudicialCaseForm nuevo() {
        return new JudicialCaseForm(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, true, null);
    }
}
