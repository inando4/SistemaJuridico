package pe.org.beneficencia.legalcontrol.access;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea la primera cuenta JEFA. Modo CLI del mismo JAR, sin servidor web:
 *
 * <pre>
 *   java -jar sistema-juridico.jar --spring.profiles.active=local \
 *        --app.command=bootstrap --app.bootstrap.email=... --app.bootstrap.name="..."
 * </pre>
 *
 * <p>Solo funciona con la tabla de usuarios vacia. A partir de ahi, las altas se
 * hacen desde la aplicacion: este comando no es una puerta trasera permanente.
 *
 * <p><b>Imprime el codigo de activacion una sola vez por la salida del comando.</b>
 * Es la unica excepcion a la regla de que los codigos no se registran, y existe
 * porque no hay ninguna sesion todavia a la que mostrarselo. Quien lo ejecuta
 * debe anotarlo: no se puede recuperar despues.
 */
@Component
public class BootstrapCommand implements ApplicationRunner {

    public static final String COMANDO = "bootstrap";
    private static final long VIGENCIA_HORAS = 24;

    private final JdbcClient jdbc;
    private final AppUserRepository usuarios;
    private final Clock clock;

    public BootstrapCommand(JdbcClient jdbc, AppUserRepository usuarios, Clock clock) {
        this.jdbc = jdbc;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!COMANDO.equals(valor(args, "app.command"))) {
            return;
        }
        String correo = valor(args, "app.bootstrap.email");
        String nombre = valor(args, "app.bootstrap.name");
        if (correo == null || nombre == null) {
            System.out.println("Faltan --app.bootstrap.email y --app.bootstrap.name");
            return;
        }
        ejecutar(nombre, correo);
    }

    /** @return el codigo generado, o null si ya existia alguna cuenta */
    @Transactional
    public String ejecutar(String nombre, String correo) {
        // Se serializa con el guard, igual que cualquier cambio de estado de cuenta.
        jdbc.sql("SELECT id FROM access_guard WHERE id = 1 FOR UPDATE").query().singleRow();

        if (!usuarios.sinNingunaCuenta()) {
            System.out.println("""

                    No se creo nada: ya existen cuentas en el sistema.
                    Las altas posteriores se hacen desde la aplicacion, con una sesion JEFA.
                    """);
            return null;
        }

        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(clock.instant());

        jdbc.sql("""
                INSERT INTO app_user (id, name, email, role, status, auth_version, version,
                                      created_by, created_at, updated_at)
                VALUES (:id, :nombre, :correo, 'HEAD', 'PENDING_ACTIVATION', 1, 1,
                        :id, :ahora, :ahora)
                """)
                .param("id", id).param("nombre", nombre).param("correo", correo)
                .param("ahora", ahora).update();

        String codigo = AccessCode.generar();
        jdbc.sql("""
                INSERT INTO access_token (id, user_id, token_hash, purpose, issued_at,
                                          issued_by, auth_version)
                VALUES (:id, :usuario, :hash, 'ACTIVATION', :ahora, :usuario, 1)
                """)
                .param("id", UUID.randomUUID()).param("usuario", id)
                .param("hash", AccessCode.digest(codigo)).param("ahora", ahora).update();

        System.out.printf("""

                ============================================================
                 Primera cuenta JEFA creada, pendiente de activacion.
                ============================================================

                 Nombre : %s
                 Correo : %s

                 CODIGO DE ACTIVACION: %s

                 Anotelo ahora: no se vuelve a mostrar y no se puede recuperar.
                 Caduca en %d horas. Para activarla, entre a /access/redeem.

                ============================================================
                %n""", nombre, correo, codigo, VIGENCIA_HORAS);

        return codigo;
    }

    private String valor(ApplicationArguments args, String nombre) {
        var valores = args.getOptionValues(nombre);
        return valores == null || valores.isEmpty() ? null : valores.get(0);
    }
}
