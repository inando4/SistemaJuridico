package pe.org.beneficencia.legalcontrol.access;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Dos guardas sobre cada peticion identificada, del mas barato al mas caro.
 *
 * <p><b>1. Corte absoluto de 12 h.</b> Solo mira la sesion, no toca la base. La
 * actividad NO lo renueva: quien entro a las 8 vuelve a entrar a las 20, haya
 * trabajado todo el dia o no. La inactividad de 4 h la aplica el contenedor por
 * configuracion; esto es el otro limite (FR-002).
 *
 * <p><b>2. Revision de autorizacion.</b> Compara el {@code auth_version} guardado
 * al entrar con el vigente en la base. Si alguien desactivo la cuenta o cambio
 * la contrasena, dejan de coincidir y la sesion muere en la siguiente peticion,
 * sin necesidad de borrar sesiones. Cuesta una lectura por peticion: es el precio
 * de que revocar sea inmediato, y esta presupuestado en el modelo de datos.
 *
 * <p>El orden importa: una sesion caducada se rechaza sin ir a la base.
 */
@Component
public class SessionGuardFilter extends OncePerRequestFilter {

    static final Duration LIMITE_ABSOLUTO = Duration.ofHours(12);

    private final AppUserRepository usuarios;
    private final Clock clock;

    public SessionGuardFilter(AppUserRepository usuarios, Clock clock) {
        this.usuarios = usuarios;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        return "GET".equals(peticion.getMethod()) && "/ping".equals(peticion.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {
        HttpSession sesion = peticion.getSession(false);
        if (sesion == null) {
            cadena.doFilter(peticion, respuesta);
            return;
        }
        Object guardada = sesion.getAttribute(CuentaActual.ATRIBUTO_SESION);
        if (!(guardada instanceof CuentaActual cuenta)) {
            cadena.doFilter(peticion, respuesta);
            return;
        }

        if (caducadaPorLimiteAbsoluto(cuenta) || revocada(cuenta)) {
            sesion.invalidate();
            respuesta.sendRedirect(peticion.getContextPath() + "/login?expirada");
            return;
        }

        cadena.doFilter(peticion, respuesta);
    }

    private boolean caducadaPorLimiteAbsoluto(CuentaActual cuenta) {
        Instant ingreso = Instant.ofEpochSecond(cuenta.authenticatedAtEpochSeconds());
        return !clock.instant().isBefore(ingreso.plus(LIMITE_ABSOLUTO));
    }

    private boolean revocada(CuentaActual cuenta) {
        Optional<Long> vigente = usuarios.authVersionSiActiva(cuenta.id());
        // Cuenta ausente o inactiva: revocada. Version distinta: revocada.
        return vigente.isEmpty() || vigente.get() != cuenta.authVersion();
    }
}
