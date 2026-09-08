package pe.org.beneficencia.legalcontrol.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
 * El motivo es obligatorio al revertir un cumplido, y solo ahi.
 *
 * <p>Es la unica accion del sistema que reescribe una afirmacion sobre trabajo ya
 * declarado como hecho (insumo, seccion 20.2).
 */
@AutoConfigureMockMvc
class RevertReasonContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID pendiente;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        UUID abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");

        pendiente = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, registered_at, scheduled_for,
                                          active, created_at, updated_at, version)
                VALUES (:id, :owner, 'Tarea de prueba', :hoy, :hoy, true, :ahora, :ahora, 1)
                """).param("id", pendiente).param("owner", abogado)
                .param("hoy", LocalDate.now()).param("ahora", ahora).update();
    }

    private long version() {
        return jdbc.sql("SELECT version FROM pending_task WHERE id = :id")
                .param("id", pendiente).query(Long.class).single();
    }

    private void cumplir() throws Exception {
        mvc.perform(post("/pendientes/" + pendiente + "/cumplir").session(sesion).with(csrf())
                .param("version", String.valueOf(version())));
    }

    private boolean sigueCumplido() {
        return jdbc.sql("SELECT completed_at FROM pending_task WHERE id = :id")
                .param("id", pendiente).query().singleRow().get("completed_at") != null;
    }

    @Test
    @DisplayName("revertir sin motivo no deshace nada")
    void sinMotivoNoRevierte() throws Exception {
        cumplir();

        mvc.perform(post("/pendientes/" + pendiente + "/revertir").session(sesion).with(csrf())
                .param("version", String.valueOf(version())));

        assertThat(sigueCumplido()).as("sigue cumplido: la reversion se rechazo").isTrue();
    }

    @Test
    @DisplayName("revertir con motivo si deshace")
    void conMotivoRevierte() throws Exception {
        cumplir();

        mvc.perform(post("/pendientes/" + pendiente + "/revertir").session(sesion).with(csrf())
                .param("version", String.valueOf(version()))
                .param("motivo", "Me equivoque de tarea"));

        assertThat(sigueCumplido()).isFalse();
    }

    @Test
    @DisplayName("cumplir no exige motivo")
    void cumplirSinMotivo() throws Exception {
        cumplir();
        assertThat(sigueCumplido()).as("solo revertir lo exige").isTrue();
    }

    @Test
    @DisplayName("reprogramar tampoco exige motivo")
    void reprogramarSinMotivo() throws Exception {
        mvc.perform(post("/pendientes/" + pendiente + "/reprogramar").session(sesion).with(csrf())
                .param("version", String.valueOf(version()))
                .param("scheduledFor", "2026-12-15"));

        var fecha = jdbc.sql("SELECT scheduled_for FROM pending_task WHERE id = :id")
                .param("id", pendiente).query().singleRow().get("scheduled_for");
        assertThat(fecha.toString()).isEqualTo("2026-12-15");
    }

    @Test
    @DisplayName("el formulario de reversion marca el motivo como obligatorio")
    void formularioMarcaObligatorio() throws Exception {
        cumplir();

        String html = mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/pendientes/" + pendiente).session(sesion))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Motivo de la reversión");
        assertThat(html).contains("name=\"motivo\" required");
    }
}
