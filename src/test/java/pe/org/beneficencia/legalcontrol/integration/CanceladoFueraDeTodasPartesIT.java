package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

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

/**
 * Un pendiente cancelado desaparece de <b>las cinco</b> pantallas donde aparecia
 * (RF-006).
 *
 * <p>Cada una filtra por su cuenta —el listado, el bloque del expediente, las alertas,
 * el calendario y la pantalla de hoy—, asi que basta con que una se olvide para que lo
 * cancelado siga estorbando justo donde mas molesta.
 */
@AutoConfigureMockMvc
class CanceladoFueraDeTodasPartesIT extends PostgresIntegrationTest {

    private static final String TITULO = "Pendiente que se cancelo";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID expediente;
    private UUID elCancelado;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        UUID yo = SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");
        int ano = LocalDate.now().getYear();
        DatosSinteticos.sembrarCalendario(jdbc, yo, ano - 1, ano, ano + 1);
        sesion = SesionDePrueba.entrar(mvc, "yo@ejemplo.test");

        expediente = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, active,
                                           created_at, updated_at, version)
                VALUES (:id, :o, 'EXP-CANCELADO-2026', true, :a, :a, 1)
                """).param("id", expediente).param("o", yo)
                .param("a", Timestamp.from(Instant.now())).update();

        // Programado y con plazo para HOY, para que aparezca en las cinco pantallas
        // mientras siga activo.
        elCancelado = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, judicial_case_id, registered_at,
                                          received_at, scheduled_for, deadline, active,
                                          created_at, updated_at, version)
                VALUES (:id, :o, :t, :j, :hoy, :hoy, :hoy, :hoy, true, :ts, :ts, 1)
                """)
                .param("id", elCancelado).param("o", yo).param("t", TITULO)
                .param("j", expediente).param("hoy", LocalDate.now()).param("ts", ahora)
                .update();
    }

    private String pantalla(String ruta) throws Exception {
        return mvc.perform(get(ruta).session(sesion))
                .andReturn().getResponse().getContentAsString();
    }

    private void cancelar() {
        jdbc.sql("UPDATE pending_task SET active = false WHERE id = :id")
                .param("id", elCancelado).update();
    }

    @Test
    @DisplayName("mientras esta activo aparece en las cinco pantallas")
    void primeroAparece() throws Exception {
        // Sin esta comprobacion, la prueba siguiente pasaria aunque el pendiente no
        // hubiera aparecido nunca en ninguna parte.
        assertThat(pantalla("/pendientes")).contains(TITULO);
        assertThat(pantalla("/judiciales/" + expediente)).contains(TITULO);
        assertThat(pantalla("/alertas")).contains(TITULO);
        assertThat(pantalla("/calendario")).contains(TITULO);
        assertThat(pantalla("/pendientes/hoy")).contains(TITULO);
    }

    @Test
    @DisplayName("cancelado desaparece de las cinco (RF-006)")
    void despuesDesaparece() throws Exception {
        cancelar();

        assertThat(pantalla("/pendientes")).as("listado").doesNotContain(TITULO);
        assertThat(pantalla("/judiciales/" + expediente))
                .as("bloque de la ficha del expediente").doesNotContain(TITULO);
        assertThat(pantalla("/alertas")).as("alertas").doesNotContain(TITULO);
        assertThat(pantalla("/calendario")).as("calendario").doesNotContain(TITULO);
        assertThat(pantalla("/pendientes/hoy")).as("pantalla de hoy").doesNotContain(TITULO);
    }

    @Test
    @DisplayName("pero sigue alcanzable pidiendo los ocultos")
    void siguALaVistaSiSePide() throws Exception {
        cancelar();

        assertThat(pantalla("/pendientes?visibility=inactive"))
                .as("nada desaparece del sistema: solo del trabajo del dia")
                .contains(TITULO);
    }
}
