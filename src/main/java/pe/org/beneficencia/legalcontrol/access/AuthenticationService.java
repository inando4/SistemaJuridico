package pe.org.beneficencia.legalcontrol.access;

import java.util.List;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Carga la cuenta para autenticar.
 *
 * <p><b>Una cuenta que no existe, una pendiente de activacion y una desactivada
 * se tratan igual: como si no existiera.</b> No se lanza {@code DisabledException}
 * ni nada que distinga los casos, porque cualquier diferencia —de mensaje, de
 * codigo o incluso de tiempo de respuesta— permitiria averiguar quien tiene
 * cuenta en el sistema (FR-001).
 *
 * <p>Quien tenga una cuenta pendiente o desactivada lo sabe por otra via: se lo
 * dice la jefa cuando le entrega el codigo.
 */
@Service
public class AuthenticationService implements UserDetailsService {

    private final AppUserRepository usuarios;

    public AuthenticationService(AppUserRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        return usuarios.porCorreo(correo)
                .filter(AppUserRepository.CuentaPersistida::activa)
                .filter(cuenta -> cuenta.passwordHash() != null)
                .map(cuenta -> (UserDetails) User.withUsername(cuenta.email())
                        .password(cuenta.passwordHash())
                        .authorities(List.of())
                        .build())
                // Mensaje interno: Spring Security lo oculta y responde credenciales
                // invalidas, que es lo que ve el usuario.
                .orElseThrow(() -> new UsernameNotFoundException("sin cuenta utilizable"));
    }
}
