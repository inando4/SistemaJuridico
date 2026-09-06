package pe.org.beneficencia.legalcontrol.judicialcase;

import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;

/**
 * Quien puede modificar que (FR-010).
 *
 * <p>Leer no se comprueba aqui: todos consultan todo. Lo que se restringe es
 * escribir: un abogado solo sobre lo suyo, la jefa sobre cualquiera.
 *
 * <p>Esta comprobacion se repite <b>dentro de la transaccion de escritura</b>, no
 * solo al pintar los botones. Ocultar un boton no es una medida de seguridad:
 * una peticion directa lo salta sin esfuerzo.
 */
@Component
public class CaseAuthorization {

    public boolean puedeEditar(CuentaActual actor, UUID responsableDelExpediente) {
        if (actor == null) {
            return false;
        }
        return actor.esJefa() || actor.id().equals(responsableDelExpediente);
    }

    /** ¿Es una operacion de la jefa sobre un expediente ajeno? Se audita distinto. */
    public boolean esIntervencionDeJefa(CuentaActual actor, UUID responsableDelExpediente) {
        return actor != null && actor.esJefa() && !actor.id().equals(responsableDelExpediente);
    }
}
