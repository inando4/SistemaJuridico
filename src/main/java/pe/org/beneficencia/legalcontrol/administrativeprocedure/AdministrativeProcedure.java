package pe.org.beneficencia.legalcontrol.administrativeprocedure;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Procedimiento administrativo: una solicitud que un area de la institucion
 * dirige al area juridica.
 *
 * <p>No es un expediente judicial con otro nombre. Aqui no hay demandante ni
 * demandado ni materia procesal: hay <b>quien pidio</b>, <b>que pidio</b> y
 * <b>para cuando</b>. Por eso es una entidad aparte y no una variante.
 *
 * <p>{@code active} es visibilidad en el listado corriente. Que el estado sea
 * «Archivado» NO lo oculta: son dos ejes distintos, igual que en los judiciales.
 */
public record AdministrativeProcedure(
        UUID id,
        Integer sequenceNumber,
        UUID ownerId,
        String ownerName,
        String fileNumber,
        String requestingArea,
        String request,
        UUID administrativeStatusId,
        String administrativeStatusName,
        LocalDate receivedAt,
        LocalDate deadline,
        String notes,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    /**
     * ¿La fecha limite es anterior a la de recepcion?
     *
     * <p>Se calcula al consultar y se descarta. El sistema avisa pero no corrige:
     * puede haber un pedido con plazo retroactivo, y adivinar cual de las dos
     * fechas esta mal seria inventar.
     */
    public boolean fechasIncoherentes() {
        return receivedAt != null && deadline != null && deadline.isBefore(receivedAt);
    }
}
