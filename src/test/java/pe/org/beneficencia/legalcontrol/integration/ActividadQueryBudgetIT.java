package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import pe.org.beneficencia.legalcontrol.config.ClockConfig;

/**
 * Presupuesto de la actividad diaria (principio IV).
 *
 * <p><b>La invariante</b>: el coste no depende de cuantas cosas se hicieran ese dia.
 * Es lo que se rompe si alguien resuelve el tipo de cada actividad con una consulta
 * por fila en vez del {@code LEFT JOIN}.
 */
@AutoConfigureMockMvc
@Import(ContadorDeConsultas.class)
class ActividadQueryBudgetIT extends PostgresIntegrationTest {

    /** Un dia cargado a proposito: 20 cumplidos y 20 escritas a mano. */
    private static final int POR_MITAD = 20;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private LocalDate cargado;
    private LocalDate tranquilo;

    @BeforeEach
    void preparar() throws Exception {
        LocalDate hoy = LocalDate.now(ClockConfig.ZONA);
        cargado = hoy;
        tranquilo = hoy.minusDays(3);

        if (sinDatos()) {
            SesionDePrueba.limpiar(jdbc);
            UUID abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test",
                    "LAWYER");

            for (int i = 0; i < POR_MITAD; i++) {
                cumplido(abogado, "Cumplido %02d".formatted(i), cargado);
                manual(abogado, "Actividad manual %02d".formatted(i), cargado);
            }
            // El dia tranquilo tiene exactamente una de cada.
            cumplido(abogado, "Unico cumplido", tranquilo);
            manual(abogado, "Unica actividad", tranquilo);
        }
        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
    }

    private boolean sinDatos() {
        Integer total = jdbc.sql("SELECT count(*) FROM manual_activity").query(Integer.class).single();
        return total == null || total <= POR_MITAD;
    }

    private void cumplido(UUID owner, String titulo, LocalDate dia) {
        Timestamp ahora = Timestamp.from(Instant.now());
        Instant momento = dia.atTime(LocalTime.of(10, 0)).atZone(ClockConfig.ZONA).toInstant();
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, registered_at, completed_at,
                                          active, created_at, updated_at, version)
                VALUES (:id, :owner, :titulo, :dia, :cumplido, true, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", owner).param("titulo", titulo)
                .param("dia", dia).param("cumplido", Timestamp.from(momento))
                .param("ahora", ahora).update();
    }

    private void manual(UUID owner, String descripcion, LocalDate dia) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO manual_activity (id, owner_id, performed_on, description,
                                             created_at, updated_at, version)
                VALUES (:id, :owner, :dia, :desc, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", owner)
                .param("dia", dia).param("desc", descripcion).param("ahora", ahora).update();
    }

    private long costeDe(String ruta) {
        return ContadorDeConsultas.contar(() -> {
            try {
                mvc.perform(get(ruta).session(sesion));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("el coste no depende de cuantas actividades tenga el dia")
    void invarianteFrenteAlNumeroDeActividades() {
        long conMuchas = costeDe("/actividad-diaria?dia=" + cargado);
        long conUna = costeDe("/actividad-diaria?dia=" + tranquilo);

        System.out.printf("Actividad diaria: %d consultas con %d actividades, %d con 2%n",
                conMuchas, POR_MITAD * 2, conUna);

        assertThat(conMuchas)
                .as("el tipo de cada actividad sale del LEFT JOIN, no de una consulta por fila")
                .isEqualTo(conUna);
    }

    @Test
    @DisplayName("la actividad diaria se mantiene dentro de su techo")
    void dentroDelTecho() {
        long coste = costeDe("/actividad-diaria?dia=" + cargado);
        System.out.printf("Actividad diaria: %d consultas%n", coste);

        assertThat(coste)
                .as("dos consultas propias mas sesion, catalogo de tipos y lista de personas")
                .isLessThanOrEqualTo(7L);
    }
}
