package pe.org.beneficencia.legalcontrol.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base de las pruebas de integracion.
 *
 * <p>Levanta PostgreSQL 17 real, la misma version mayor que produccion. La
 * constitucion y el plan prohiben sustituirlo por H2: una base en memoria no
 * reproduce restricciones, transacciones ni concurrencia, que es justo lo que
 * estas pruebas deben comprobar.
 *
 * <p>El contenedor es estatico y se reutiliza entre clases para no pagar el
 * arranque en cada una.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.11-trixie")
                    .withDatabaseName("sistema_juridico")
                    .withUsername("sistema_juridico_test")
                    .withPassword("test")
                    // Crea los roles separados de aplicacion y migracion, como en produccion.
                    .withInitScript("db/init-roles-test.sql");

    static {
        POSTGRES.start();
        // Ryuk esta desactivado (ver testcontainers.properties), asi que el cierre
        // corre por nuestra cuenta para no dejar contenedores sueltos.
        Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop));
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
        // Las migraciones si se aplican en pruebas: se comprueban contra la base real.
        registro.add("spring.flyway.enabled", () -> true);
        registro.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registro.add("spring.flyway.user", POSTGRES::getUsername);
        registro.add("spring.flyway.password", POSTGRES::getPassword);
    }
}
