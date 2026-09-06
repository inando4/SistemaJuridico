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
 * Canje de un codigo: activar, reactivar o restablecer contrasena.
 *
 * <p>Una sola ruta para los tres, porque para quien la usa son la misma cosa:
 * «tengo un codigo y quiero poner mi contrasena».
 *
 * <p><b>Todos los rechazos dicen lo mismo.</b> Codigo inexistente, ya usado,
 * vencido o de otra cuenta producen el mismo mensaje: cualquier diferencia
 * permitiria ir probando codigos y deducir cuales existen.
 */
@Service
public class RedeemService {

    private static final String RECHAZO_GENERICO =
            "El codigo no es valido, ya se uso o caduco. Solicite uno nuevo a la jefatura.";

    private final JdbcClient jdbc;
    private final AccessCodeService codigos;
    private final PasswordEncoder encoder;
    private final AuditRecorder auditoria;
    private final AuthAttemptService intentos;
    private final Clock clock;

    public RedeemService(JdbcClient jdbc, AccessCodeService codigos, PasswordEncoder encoder,
                         AuditRecorder auditoria, AuthAttemptService intentos, Clock clock) {
        this.jdbc = jdbc;
        this.codigos = codigos;
        this.encoder = encoder;
        this.auditoria = auditoria;
        this.intentos = intentos;
        this.clock = clock;
    }

    /** @return vacio si todo fue bien; el motivo del rechazo si no */
    @Transactional
    public Optional<String> canjear(String correo, String codigo, String contrasena,
                                    String confirmacion, String origen) {
        var problemaContrasena = PasswordPolicy.validar(contrasena, confirmacion);
        if (problemaContrasena.isPresent()) {
            return problemaContrasena;
        }
        if (intentos.demasiadosFallos(AuthAttemptService.Tipo.CODE_REDEEM_FAILURE, correo, origen)) {
            return Optional.of("Demasiados intentos. Espere unos minutos antes de reintentar.");
        }

        var vigente = codigos.buscarVigente(correo, codigo);
        if (vigente.isEmpty()) {
            intentos.registrar(AuthAttemptService.Tipo.CODE_REDEEM_FAILURE, correo, origen);
            return Optional.of(RECHAZO_GENERICO);
        }

        var token = vigente.get();
        boolean estadoAdmisible = switch (token.purpose()) {
            case "ACTIVATION" -> "PENDING_ACTIVATION".equals(token.status());
            case "REACTIVATION" -> "PENDING_REACTIVATION".equals(token.status());
            case "RESET" -> "ACTIVE".equals(token.status());
            default -> false;
        };
        if (!estadoAdmisible) {
            intentos.registrar(AuthAttemptService.Tipo.CODE_REDEEM_FAILURE, correo, origen);
            return Optional.of(RECHAZO_GENERICO);
        }

        String estadoAnterior = token.status();

        // auth_version sube: las sesiones anteriores mueren, aunque el titular
        // este restableciendo su propia contrasena desde otro equipo.
        jdbc.sql("""
                UPDATE app_user
                SET password_hash = :hash, status = 'ACTIVE',
                    auth_version = auth_version + 1, version = version + 1, updated_at = :ahora
                WHERE id = :id
                """)
                .param("id", token.userId()).param("hash", encoder.encode(contrasena))
                .param("ahora", Timestamp.from(clock.instant()))
                .update();

        codigos.marcarConsumido(token.id());
        codigos.revocarTodos(token.userId());

        // Del cambio de credencial solo queda el hecho, nunca el hash ni el codigo.
        auditoria.registrar("APP_USER", token.userId(), "PASSWORD_SET",
                token.userId(), token.userId(),
                Map.of("status", estadoAnterior, "credentialSet", false),
                Map.of("status", "ACTIVE", "credentialSet", true), null);

        return Optional.empty();
    }
}
