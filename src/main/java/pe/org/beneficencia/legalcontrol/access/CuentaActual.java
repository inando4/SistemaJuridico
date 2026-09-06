package pe.org.beneficencia.legalcontrol.access;

import java.io.Serializable;
import java.util.UUID;

/**
 * Lo que se guarda en la sesion sobre quien entro.
 *
 * <p>Nunca contrasena ni codigos. {@code authenticatedAt} es el instante del
 * ingreso y NO se renueva con la actividad: de el depende el corte absoluto de
 * 12 h. {@code authVersion} es la revision de autorizacion vigente al entrar;
 * si la cuenta se revoca, deja de coincidir y la sesion muere (FR-002).
 */
public record CuentaActual(
        UUID id,
        String name,
        String email,
        String role,
        long authVersion,
        long authenticatedAtEpochSeconds) implements Serializable {

    public static final String ATRIBUTO_SESION = "cuentaActual";

    public boolean esJefa() {
        return "HEAD".equals(role);
    }
}
