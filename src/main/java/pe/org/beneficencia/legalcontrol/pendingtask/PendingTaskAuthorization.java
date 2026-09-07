package pe.org.beneficencia.legalcontrol.pendingtask;

import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;

/**
 * Quien puede actuar sobre que pendiente.
 *
 * <p>Leer no se restringe. Lo que se restringe es actuar: cumplir, revertir,
 * reprogramar o editar. Se revalida <b>dentro de la transaccion</b>, no solo al
 * pintar los botones.
 */
@Component
public class PendingTaskAuthorization {

    public boolean puedeActuar(CuentaActual actor, UUID responsable) {
        return actor != null && (actor.esJefa() || actor.id().equals(responsable));
    }
}
