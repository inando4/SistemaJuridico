package pe.org.beneficencia.legalcontrol.access;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.org.beneficencia.legalcontrol.audit.AuditRecorder;

/**
 * Cambio de contrasena por su propio titular (FR-025b).
 *
 * <p>Es la contrapartida a que la jefa vea los codigos que entrega: quien sospeche
 * de un codigo puede cerrar esa puerta por su cuenta, sin pedir permiso ni avisar
 * a nadie. Exige la contrasena actual, asi que una sesion ajena abierta tampoco
 * sirve para secuestrar la cuenta.
 */
@Service
public class SelfPasswordService {

    private final JdbcClient jdbc;
    private final AppUserRepository usuarios;
    private final PasswordEncoder encoder;
    private final AuditRecorder auditoria;
    private final Clock clock;

    public SelfPasswordService(JdbcClient jdbc, AppUserRepository usuarios,
                               PasswordEncoder encoder, AuditRecorder auditoria, Clock clock) {
        this.jdbc = jdbc;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /** @return vacio si se cambio; el motivo del rechazo si no */
    @Transactional
    public Optional<String> cambiar(CuentaActual actual, String contrasenaActual,
                                    String nueva, String confirmacion) {
        var problema = PasswordPolicy.validar(nueva, confirmacion);
        if (problema.isPresent()) {
            return problema;
        }

        var cuenta = usuarios.porCorreo(actual.email()).orElse(null);
        if (cuenta == null || cuenta.passwordHash() == null
                || !encoder.matches(contrasenaActual, cuenta.passwordHash())) {
            return Optional.of("La contraseña actual no es correcta.");
        }
        if (encoder.matches(nueva, cuenta.passwordHash())) {
            return Optional.of("La contraseña nueva debe ser distinta de la actual.");
        }

        jdbc.sql("""
                UPDATE app_user
                SET password_hash = :hash, auth_version = auth_version + 1,
                    version = version + 1, updated_at = :ahora
                WHERE id = :id
                """)
                .param("id", actual.id()).param("hash", encoder.encode(nueva))
                .param("ahora", Timestamp.from(clock.instant()))
                .update();

        auditoria.registrar("APP_USER", actual.id(), "PASSWORD_CHANGED_BY_OWNER",
                actual.id(), actual.id(),
                Map.of("credentialVersion", cuenta.authVersion()),
                Map.of("credentialVersion", cuenta.authVersion() + 1), null);

        return Optional.empty();
    }
}
