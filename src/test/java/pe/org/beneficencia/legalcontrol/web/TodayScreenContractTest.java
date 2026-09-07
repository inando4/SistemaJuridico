package pe.org.beneficencia.legalcontrol.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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

import pe.org.beneficencia.legalcontrol.integration.PostgresIntegrationTest;
import pe.org.beneficencia.legalcontrol.integration.SesionDePrueba;

/**
 * «Pendientes de hoy» muestra lo de hoy <b>y lo que quedo atras</b>.
 *
 * <p>Si solo mostrara los de hoy, lo vencido desapareceria de la vista justo cuando
 * mas importa mirarlo.
 */
@AutoConfigureMockMvc
class TodayScreenContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID abogado;
    private UUID deHoy;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");

        LocalDate hoy = LocalDate.now();
        deHoy = pendiente("Lo de hoy", hoy);
        pendiente("Quedo de ayer", hoy.minusDays(3));
        pendiente("Es para manana", hoy.plusDays(2));
    }

    private UUID pendiente(String titulo, LocalDate programada) {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, registered_at, scheduled_for,
                                          active, created_at, updated_at, version)
                VALUES (:id, :owner, :titulo, :hoy, :programada, true, :ahora, :ahora, 1)
                """)
                .param("id", id).param("owner", abogado).param("titulo", titulo)
                .param("hoy", LocalDate.now()).param("programada", programada)
                .param("ahora", ahora).update();
        return id;
    }

    private String pantalla() throws Exception {
        return mvc.perform(get("/pendientes/hoy").session(sesion))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("muestra lo programado para hoy")
    void muestraLoDeHoy() throws Exception {
        assertThat(pantalla()).contains("Lo de hoy");
    }

    @Test
    @DisplayName("muestra tambien lo vencido que sigue activo")
    void muestraLoVencido() throws Exception {
        assertThat(pantalla()).contains("Quedo de ayer")
                .contains("Quedo pendiente de un dia anterior");
    }

    @Test
    @DisplayName("no muestra lo que es para mas adelante")
    void noMuestraLoFuturo() throws Exception {
        assertThat(pantalla()).doesNotContain("Es para manana");
    }

    @Test
    @DisplayName("lo cumplido desaparece de la pantalla")
    void cumplidoDesaparece() throws Exception {
        assertThat(pantalla()).contains("Lo de hoy");

        long version = jdbc.sql("SELECT version FROM pending_task WHERE id = :id")
                .param("id", deHoy).query(Long.class).single();
        mvc.perform(post("/pendientes/" + deHoy + "/cumplir").session(sesion).with(csrf())
                .param("version", String.valueOf(version)));

        assertThat(pantalla()).doesNotContain("Lo de hoy");
    }

    @Test
    @DisplayName("un pendiente oculto tampoco aparece")
    void ocultoNoAparece() throws Exception {
        jdbc.sql("UPDATE pending_task SET active = false WHERE title = 'Lo de hoy'").update();

        assertThat(pantalla()).doesNotContain("Lo de hoy");
    }

    @Test
    @DisplayName("sin nada programado ofrece salida, no un callejon")
    void estadoVacioConSalida() throws Exception {
        jdbc.sql("DELETE FROM pending_task").update();

        assertThat(pantalla()).contains("No hay nada programado")
                .contains("Ver todos los pendientes");
    }
}
