package pe.org.beneficencia.legalcontrol.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Desactiva lo que un respaldo restaurado resucita.
 *
 * <pre>
 *   java -jar sistema-juridico.jar --spring.profiles.active=local \
 *        --app.command=recovery-sanitize --app.recovery.database=nombre_de_la_base
 * </pre>
 *
 * <p>Un respaldo es una foto del pasado. Si se tomo el lunes y se restaura el
 * viernes, trae los codigos de acceso que estaban vivos el lunes: <b>un codigo
 * revocado el martes vuelve a servir</b>, y el sistema no tiene forma de saberlo.
 * Los contadores de intentos fallidos vuelven a cero y regalan otra tanda a quien
 * estuviera probando.
 *
 * <p>Se ejecuta entre el {@code pg_restore} y el primer arranque de la aplicacion
 * recuperada. Arrancar antes deja una ventana con codigos zombis activos.
 *
 * <p><b>Nunca toca la auditoria.</b> Una restauracion no es excusa para perder la
 * evidencia de quien hizo que: este comando limpia lo que caduca, no lo que
 * documenta. Las sesiones no hace falta borrarlas porque viven en memoria del
 * contenedor y un proceso nuevo arranca sin ninguna.
 */
@Component
public class RecoverySanitizeCommand implements ApplicationRunner {

    public static final String COMANDO = "recovery-sanitize";

    private final JdbcClient jdbc;

    public RecoverySanitizeCommand(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        var valores = args.getOptionValues("app.command");
        if (valores == null || !valores.contains(COMANDO)) {
            return;
        }

        String baseActual = jdbc.sql("SELECT current_database()").query(String.class).single();
        var confirmada = args.getOptionValues("app.recovery.database");
        String declarada = confirmada == null || confirmada.isEmpty() ? null : confirmada.get(0);

        // Guarda deliberada: obliga a mirar contra que base se esta ejecutando.
        // Corrido por error sobre produccion, mataria todos los codigos pendientes.
        if (!baseActual.equals(declarada)) {
            System.out.printf("""

                    ============================================================
                     OPERACION DETENIDA
                    ============================================================

                     Este comando invalida los codigos de acceso y reinicia los
                     contadores de proteccion. Debe ejecutarse SOLO sobre una copia
                     restaurada, nunca sobre produccion.

                     Conectado a la base : %s
                     Base declarada      : %s

                     Repita indicando la base explicitamente:
                       --app.command=recovery-sanitize --app.recovery.database=%s

                    ============================================================
                    %n""", baseActual, declarada == null ? "(no indicada)" : declarada, baseActual);
            return;
        }

        Resumen resumen = limpiar();

        System.out.printf("""

                ============================================================
                 Copia restaurada saneada.
                ============================================================
                 Base                          : %s

                 Codigos de acceso invalidados : %d
                 Contadores de abuso borrados  : %d
                 Entradas de auditoria         : %d  (intactas, no se tocan)
                 Usuarios                      : %d
                 Expedientes                   : %d

                 Los codigos pendientes deben volver a emitirse desde la
                 aplicacion. Ninguno de los anteriores sirve ya.
                ============================================================
                %n""", resumen.base, resumen.codigos, resumen.intentos,
                resumen.auditoria, resumen.usuarios, resumen.expedientes);
    }

    @Transactional
    public Resumen limpiar() {
        String base = jdbc.sql("SELECT current_database()").query(String.class).single();

        // Se cuentan antes de borrar, para poder informar de lo que se hizo.
        int codigos = jdbc.sql("DELETE FROM access_token").update();
        int intentos = jdbc.sql("DELETE FROM auth_attempt").update();

        // Se leen despues para dejar constancia de que siguen ahi.
        int auditoria = jdbc.sql("SELECT count(*) FROM audit_event").query(Integer.class).single();
        int usuarios = jdbc.sql("SELECT count(*) FROM app_user").query(Integer.class).single();
        int expedientes = jdbc.sql("SELECT count(*) FROM judicial_case").query(Integer.class).single();

        return new Resumen(base, codigos, intentos, auditoria, usuarios, expedientes);
    }

    public record Resumen(String base, int codigos, int intentos,
                   int auditoria, int usuarios, int expedientes) {
    }
}
