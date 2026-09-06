package pe.org.beneficencia.legalcontrol.access;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Emision y canje de codigos de un solo uso.
 *
 * <p>El codigo se devuelve al llamador para mostrarlo <b>una vez</b>; aqui solo
 * se guarda su digest. No se puede recuperar despues ni por soporte ni mirando
 * la base: si se pierde, se genera otro.
 *
 * <p>Generar otro revoca los vivos del mismo usuario y proposito. Que dos codigos
 * validos convivan seria una via silenciosa para que alguien conserve acceso.
 */
@Service
public class AccessCodeService {

    public enum Proposito {
        ACTIVATION(Duration.ofHours(24)),
        REACTIVATION(Duration.ofHours(24)),
        // Mas corto: se entrega en mano y se usa en el momento.
        RESET(Duration.ofHours(1));

        public final Duration vigencia;

        Proposito(Duration vigencia) {
            this.vigencia = vigencia;
        }
    }

    private final JdbcClient jdbc;
    private final Clock clock;

    public AccessCodeService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** @return el codigo en claro, para mostrarlo una sola vez */
    public String emitir(UUID usuario, Proposito proposito, UUID emisor, long authVersion) {
        revocarVivos(usuario, proposito);

        String codigo = AccessCode.generar();
        jdbc.sql("""
                INSERT INTO access_token (id, user_id, token_hash, purpose, issued_at,
                                          issued_by, auth_version)
                VALUES (:id, :usuario, :hash, :proposito, :ahora, :emisor, :version)
                """)
                .param("id", UUID.randomUUID()).param("usuario", usuario)
                .param("hash", AccessCode.digest(codigo))
                .param("proposito", proposito.name())
                .param("ahora", Timestamp.from(clock.instant()))
                .param("emisor", emisor).param("version", authVersion)
                .update();
        return codigo;
    }

    /** Marca como revocados los codigos no consumidos del mismo usuario y proposito. */
    public void revocarVivos(UUID usuario, Proposito proposito) {
        jdbc.sql("""
                UPDATE access_token SET revoked_at = :ahora
                WHERE user_id = :usuario AND purpose = :proposito
                  AND consumed_at IS NULL AND revoked_at IS NULL
                """)
                .param("usuario", usuario).param("proposito", proposito.name())
                .param("ahora", Timestamp.from(clock.instant()))
                .update();
    }

    /** Revoca todos los codigos de un usuario, sea cual sea su proposito. */
    public void revocarTodos(UUID usuario) {
        jdbc.sql("""
                UPDATE access_token SET revoked_at = :ahora
                WHERE user_id = :usuario AND consumed_at IS NULL AND revoked_at IS NULL
                """)
                .param("usuario", usuario).param("ahora", Timestamp.from(clock.instant()))
                .update();
    }

    /**
     * Busca un codigo utilizable para ese correo. Bloquea la fila para que dos
     * canjes simultaneos del mismo codigo no prosperen los dos.
     */
    public Optional<CodigoVigente> buscarVigente(String correo, String codigo) {
        Instant ahora = clock.instant();
        return jdbc.sql("""
                SELECT t.id, t.user_id, t.purpose, t.issued_at, u.status, u.auth_version
                FROM access_token t
                JOIN app_user u ON u.id = t.user_id
                WHERE t.token_hash = :hash
                  AND lower(btrim(u.email)) = lower(btrim(:correo))
                  AND t.consumed_at IS NULL AND t.revoked_at IS NULL
                FOR UPDATE OF t
                """)
                .param("hash", AccessCode.digest(codigo)).param("correo", correo)
                .query(CodigoVigente.class)
                .optional()
                // La caducidad se calcula desde issued_at: no hay columna que
                // mantener ni proceso que la marque como vencida.
                .filter(c -> ahora.isBefore(c.issuedAt().plus(
                        Proposito.valueOf(c.purpose()).vigencia)));
    }

    public void marcarConsumido(UUID tokenId) {
        jdbc.sql("UPDATE access_token SET consumed_at = :ahora WHERE id = :id")
                .param("id", tokenId).param("ahora", Timestamp.from(clock.instant()))
                .update();
    }

    public record CodigoVigente(UUID id, UUID userId, String purpose,
                                Instant issuedAt, String status, long authVersion) {
    }
}
