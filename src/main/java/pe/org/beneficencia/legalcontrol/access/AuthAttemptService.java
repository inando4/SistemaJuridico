package pe.org.beneficencia.legalcontrol.access;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Limites de abuso de FR-026, para los tres tipos de evento.
 *
 * <p>Los conteos se calculan al consultar sobre {@code auth_attempt}: no hay
 * contadores guardados que puedan quedar desincronizados ni haya que reiniciar.
 *
 * <p>Las claves de cuenta y de origen se guardan como HMAC, nunca en claro. Es
 * una tabla tecnica de proteccion, no historial de negocio: no dice quien navego
 * por donde, solo cuantos intentos hubo por una clave opaca.
 */
@Service
public class AuthAttemptService {

    public enum Tipo { LOGIN_FAILURE, CODE_REDEEM_FAILURE, CODE_ISSUE }

    private static final Duration VENTANA_FALLOS = Duration.ofMinutes(15);
    private static final int MAXIMO_FALLOS = 10;
    private static final Duration VENTANA_EMISION = Duration.ofHours(1);
    private static final int MAXIMO_EMISIONES = 5;

    private final JdbcClient jdbc;
    private final Clock clock;
    private final byte[] clave;

    public AuthAttemptService(JdbcClient jdbc, Clock clock,
                              @Value("${app.rate-limit-key:clave-local-de-desarrollo}") String clave) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.clave = clave.getBytes(StandardCharsets.UTF_8);
    }

    public void registrar(Tipo tipo, String cuenta, String origen) {
        jdbc.sql("""
                INSERT INTO auth_attempt (id, kind, account_key, origin_key, occurred_at)
                VALUES (:id, :kind, :cuenta, :origen, :ahora)
                """)
                .param("id", UUID.randomUUID())
                .param("kind", tipo.name())
                .param("cuenta", hmac(cuenta))
                .param("origen", hmac(origen))
                .param("ahora", Timestamp.from(clock.instant()))
                .update();
    }

    /** ¿Se superaron 10 fallos en 15 minutos para esta cuenta y origen? */
    public boolean demasiadosFallos(Tipo tipo, String cuenta, String origen) {
        return cuenta(tipo, cuenta, origen, VENTANA_FALLOS) >= MAXIMO_FALLOS;
    }

    /** ¿Se superaron 5 generaciones de codigo en una hora para esta cuenta? */
    public boolean demasiadasEmisiones(String cuenta) {
        return cuenta(Tipo.CODE_ISSUE, cuenta, null, VENTANA_EMISION) >= MAXIMO_EMISIONES;
    }

    private int cuenta(Tipo tipo, String cuenta, String origen, Duration ventana) {
        Timestamp desde = Timestamp.from(clock.instant().minus(ventana));
        Integer total = jdbc.sql("""
                SELECT count(*) FROM auth_attempt
                WHERE kind = :kind
                  AND occurred_at >= :desde
                  AND (:cuenta IS NULL OR account_key = :cuenta)
                  AND (:origen IS NULL OR origin_key = :origen)
                """)
                .param("kind", tipo.name())
                .param("desde", desde)
                .param("cuenta", hmac(cuenta))
                .param("origen", hmac(origen))
                .query(Integer.class).single();
        return total == null ? 0 : total;
    }

    private byte[] hmac(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            return mac.doFinal(valor.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("No fue posible calcular la clave de proteccion", e);
        }
    }

}
