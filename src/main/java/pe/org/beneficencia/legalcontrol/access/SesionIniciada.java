package pe.org.beneficencia.legalcontrol.access;

import java.io.IOException;
import java.time.Clock;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Al entrar, deja en la sesion lo minimo que hace falta despues: identidad, rol,
 * la revision de autorizacion y el instante del ingreso.
 *
 * <p>Ese instante es la referencia del corte absoluto de 12 h y no se vuelve a
 * tocar mientras dure la sesion.
 */
public class SesionIniciada implements AuthenticationSuccessHandler {

    private final AppUserRepository usuarios;
    private final Clock clock;

    public SesionIniciada(AppUserRepository usuarios, Clock clock) {
        this.usuarios = usuarios;
        this.clock = clock;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest peticion, HttpServletResponse respuesta,
                                        Authentication autenticacion) throws IOException, ServletException {
        usuarios.porCorreo(autenticacion.getName()).ifPresent(cuenta -> {
            CuentaActual actual = new CuentaActual(
                    cuenta.id(), cuenta.name(), cuenta.email(), cuenta.role(),
                    cuenta.authVersion(), clock.instant().getEpochSecond());
            peticion.getSession().setAttribute(CuentaActual.ATRIBUTO_SESION, actual);
        });
        respuesta.sendRedirect(peticion.getContextPath() + "/judiciales");
    }
}
