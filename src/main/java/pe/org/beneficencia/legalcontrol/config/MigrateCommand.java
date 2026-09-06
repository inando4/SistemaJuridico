package pe.org.beneficencia.legalcontrol.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Aplica las migraciones como paso explicito, nunca al arrancar:
 *
 * <pre>
 *   java -jar sistema-juridico.jar --spring.profiles.active=local --app.command=migrate
 * </pre>
 *
 * <p>Usa la credencial de <b>migracion</b>, distinta de la de la aplicacion. Asi
 * el usuario con el que corre el sistema no puede alterar el esquema ni por error
 * ni por un fallo de seguridad: no tiene permiso para hacerlo.
 *
 * <p>Que sea un comando aparte tambien evita que un despliegue con varias
 * instancias intente migrar desde todas a la vez.
 */
@Component
public class MigrateCommand implements ApplicationRunner {

    public static final String COMANDO = "migrate";

    private final String url;
    private final String usuario;
    private final String contrasena;

    public MigrateCommand(@Value("${spring.flyway.url:}") String url,
                          @Value("${spring.flyway.user:}") String usuario,
                          @Value("${spring.flyway.password:}") String contrasena) {
        this.url = url;
        this.usuario = usuario;
        this.contrasena = contrasena;
    }

    @Override
    public void run(ApplicationArguments args) {
        var valores = args.getOptionValues("app.command");
        if (valores == null || !valores.contains(COMANDO)) {
            return;
        }
        if (url.isBlank()) {
            System.out.println("""

                    No hay credencial de migracion configurada.
                    Defina spring.flyway.url, .user y .password (o las variables
                    DB_MIGRATION_URL, DB_MIGRATION_USERNAME y DB_MIGRATION_PASSWORD).
                    """);
            return;
        }

        var resultado = Flyway.configure()
                .dataSource(url, usuario, contrasena)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        System.out.printf("""

                ============================================================
                 Migraciones aplicadas.
                ============================================================
                 Migraciones ejecutadas ahora : %d
                 Version del esquema          : %s
                ============================================================
                %n""", resultado.migrationsExecuted,
                resultado.targetSchemaVersion == null ? "sin cambios" : resultado.targetSchemaVersion);
    }
}
