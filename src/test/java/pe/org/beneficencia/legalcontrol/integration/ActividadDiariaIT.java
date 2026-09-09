package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import pe.org.beneficencia.legalcontrol.config.ClockConfig;

/**
 * La mitad automatica de «¿que hice hoy?» sale de los pendientes cumplidos, y la
 * frontera del dia es la del area, no la del servidor.
 *
 * <p>El caso que importa es el de las 23:50. Con {@code CAST(completed_at AS date)}
 * la comparacion se resuelve en la zona del servidor: en un contenedor en UTC, todo
 * lo cumplido despues de las 19:00 hora de Lima aparece al dia siguiente. Es un fallo
 * que no se ve nunca durante el desarrollo, porque las pruebas suelen cumplir
 * pendientes a media tarde.
 */
@AutoConfigureMockMvc
class ActividadDiariaIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID abogado;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
    }

    /** Un pendiente cumplido a una hora concreta del dia, en hora de Lima. */
    private UUID cumplido(String titulo, LocalDate dia, LocalTime hora) {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        Instant momento = dia.atTime(hora).atZone(ClockConfig.ZONA).toInstant();
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, registered_at, completed_at,
                                          active, created_at, updated_at, version)
                VALUES (:id, :owner, :titulo, :dia, :cumplido, true, :ahora, :ahora, 1)
                """)
                .param("id", id).param("owner", abogado).param("titulo", titulo)
                .param("dia", dia).param("cumplido", Timestamp.from(momento))
                .param("ahora", ahora).update();
        return id;
    }

    private void actividadManual(String descripcion, LocalDate dia) {
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO manual_activity (id, owner_id, performed_on, description,
                                             created_at, updated_at, version)
                VALUES (:id, :owner, :dia, :desc, :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", abogado)
                .param("dia", dia).param("desc", descripcion).param("ahora", ahora).update();
    }

    private String pantalla(String query) throws Exception {
        return mvc.perform(get("/actividad-diaria" + query).session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private LocalDate hoy() {
        return LocalDate.now(ClockConfig.ZONA);
    }

    @Test
    @DisplayName("lo cumplido en el dia sale sin registrar nada")
    void loCumplidoSaleSolo() throws Exception {
        cumplido("Elaboracion de informe legal", hoy(), LocalTime.of(10, 0));
        cumplido("Revision de convenio", hoy(), LocalTime.of(15, 30));

        String html = pantalla("");

        assertThat(html).contains("Elaboracion de informe legal").contains("Revision de convenio");
    }

    @Test
    @DisplayName("un pendiente cumplido a las 23:50 pertenece a ESE dia")
    void laFronteraEsLaDelArea() throws Exception {
        cumplido("Escrito presentado de noche", hoy(), LocalTime.of(23, 50));

        assertThat(pantalla("?dia=" + hoy()))
                .as("con CAST a date, en un servidor en UTC esto se iria al dia siguiente")
                .contains("Escrito presentado de noche");

        assertThat(pantalla("?dia=" + hoy().plusDays(1)))
                .as("una fecha futura se corrige a hoy, asi que aqui vuelve a salir")
                .contains("Escrito presentado de noche");
    }

    @Test
    @DisplayName("un pendiente cumplido a las 00:10 no pertenece al dia anterior")
    void lasPrimerasHorasSonDelDiaNuevo() throws Exception {
        cumplido("Escrito de madrugada", hoy(), LocalTime.of(0, 10));

        assertThat(pantalla("?dia=" + hoy().minusDays(1)))
                .doesNotContain("Escrito de madrugada");
        assertThat(pantalla("?dia=" + hoy())).contains("Escrito de madrugada");
    }

    @Test
    @DisplayName("un cumplido revertido deja de aparecer")
    void revertirLoRetiraDeLaLista() throws Exception {
        UUID id = cumplido("Informe que se revirtio", hoy(), LocalTime.of(11, 0));
        assertThat(pantalla("")).contains("Informe que se revirtio");

        jdbc.sql("UPDATE pending_task SET completed_at = NULL WHERE id = :id")
                .param("id", id).update();

        assertThat(pantalla(""))
                .as("la pantalla refleja el estado actual, no una foto del dia")
                .doesNotContain("Informe que se revirtio");
    }

    @Test
    @DisplayName("una actividad con fecha anterior aparece al consultar ese dia")
    void loAnadidoDespuesApareceEnSuDia() throws Exception {
        LocalDate anteayer = hoy().minusDays(2);

        assertThat(pantalla("?dia=" + anteayer)).contains("No hay actividad registrada");

        actividadManual("Reunion que no se habia apuntado", anteayer);

        assertThat(pantalla("?dia=" + anteayer))
                .as("por eso CE-005 no afirma estabilidad, sino ausencia de foto")
                .contains("Reunion que no se habia apuntado");
    }

    @Test
    @DisplayName("un dia sin nada lo dice, no se muestra en blanco")
    void diaVacioLoDice() throws Exception {
        assertThat(pantalla("?dia=" + hoy().minusDays(30)))
                .contains("No hay actividad registrada");
    }

    @Test
    @DisplayName("se puede consultar la actividad de otra persona, sin poder registrarle nada")
    void lecturaCompartidaSinEscrituraAjena() throws Exception {
        UUID otra = SesionDePrueba.crearCuenta(jdbc, encoder, "otra@ejemplo.test", "LAWYER");
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO manual_activity (id, owner_id, performed_on, description,
                                             created_at, updated_at, version)
                VALUES (:id, :owner, :dia, 'Atencion al publico', :ahora, :ahora, 1)
                """)
                .param("id", UUID.randomUUID()).param("owner", otra)
                .param("dia", hoy()).param("ahora", ahora).update();

        String html = pantalla("?ownerId=" + otra);

        assertThat(html).as("la lectura es compartida").contains("Atencion al publico");
        assertThat(html)
                .as("se registra lo que uno hizo, no lo que hizo otro")
                .doesNotContain("Agregar actividad manual");
    }
}
