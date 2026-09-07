package pe.org.beneficencia.legalcontrol.administrativeprocedure;

import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;

/**
 * Quien puede modificar que procedimiento.
 *
 * <p>Leer no se restringe: todos consultan todo. Lo que se restringe es escribir.
 *
 * <p>Esta comprobacion se repite <b>dentro de la transaccion de escritura</b>, no
 * solo al pintar los botones: ocultar un boton no impide nada a quien componga la
 * peticion a mano.
 */
@Component
public class ProcedureAuthorization {

    public boolean puedeEditar(CuentaActual actor, UUID responsable) {
        return actor != null && (actor.esJefa() || actor.id().equals(responsable));
    }
}
