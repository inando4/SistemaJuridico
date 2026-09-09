package pe.org.beneficencia.legalcontrol.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
 * Cancelar y devolver: la accion rapida que pide la seccion 25.
 *
 * <p>Es la unica forma que tiene el sistema de quitar de en medio un registro creado
 * por error, porque la constitucion prohibe borrar.
 */
@AutoConfigureMockMvc
class CancelarPendienteContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID yo;
    private UUID laOtra;
    private UUID mio;
    private UUID ajeno;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        yo = SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");
        laOtra = SesionDePrueba.crearCuenta(jdbc, encoder, "otra@ejemplo.test", "LAWYER");
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        sesion = SesionDePrueba.entrar(mvc, "yo@ejemplo.test");

        mio = pendiente(yo, "Registrado por equivocacion");
        ajeno = pendiente(laOtra, "Trabajo de otra persona");
    }

    private UUID pendiente(UUID owner, String titulo) {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, registered_at, active,
                                          created_at, updated_at, version)
                VALUES (:id, :o, :t, :hoy, true, :ts, :ts, 1)
                """)
                .param("id", id).param("o", owner).param("t", titulo)
                .param("hoy", LocalDate.now()).param("ts", ahora).update();
        return id;
    }

    private long version(UUID id) {
        return jdbc.sql("SELECT version FROM pending_task WHERE id = :id")
                .param("id", id).query(Long.class).single();
    }

    private boolean visible(UUID id) {
        return jdbc.sql("SELECT active FROM pending_task WHERE id = :id")
                .param("id", id).query(Boolean.class).single();
    }

    private List<String> acciones(UUID id) {
        return jdbc.sql("""
                SELECT action FROM audit_event
                WHERE entity_type = 'PENDING_TASK' AND entity_id = :id
                ORDER BY occurred_at
                """).param("id", id).query(String.class).list();
    }

    @Test
    @DisplayName("cancelar lo saca del listado de trabajo pero no lo borra (RF-001)")
    void cancelarLoRetira() throws Exception {
        mvc.perform(post("/pendientes/" + mio + "/cancelar").with(csrf())
                        .param("version", String.valueOf(version(mio))).session(sesion))
                .andExpect(status().is3xxRedirection());

        assertThat(visible(mio)).isFalse();
        assertThat(jdbc.sql("SELECT count(*) FROM pending_task WHERE id = :id")
                        .param("id", mio).query(Integer.class).single())
                .as("no se borra: la constitucion no lo permite")
                .isEqualTo(1);

        String listado = mvc.perform(get("/pendientes").session(sesion))
                .andReturn().getResponse().getContentAsString();
        assertThat(listado).doesNotContain("Registrado por equivocacion");
    }

    @Test
    @DisplayName("se encuentra entre los ocultos y se devuelve (RF-002)")
    void seDevuelve() throws Exception {
        mvc.perform(post("/pendientes/" + mio + "/cancelar").with(csrf())
                .param("version", String.valueOf(version(mio))).session(sesion));

        assertThat(mvc.perform(get("/pendientes?visibility=inactive").session(sesion))
                        .andReturn().getResponse().getContentAsString())
                .as("cancelar es una correccion, no una condena")
                .contains("Registrado por equivocacion");

        mvc.perform(post("/pendientes/" + mio + "/devolver").with(csrf())
                        .param("version", String.valueOf(version(mio))).session(sesion))
                .andExpect(status().is3xxRedirection());

        assertThat(visible(mio)).isTrue();
    }

    @Test
    @DisplayName("cancelar y devolver dejan CANCEL y RESTORE en el historial (RF-003)")
    void dejaRastro() throws Exception {
        mvc.perform(post("/pendientes/" + mio + "/cancelar").with(csrf())
                .param("version", String.valueOf(version(mio))).session(sesion));
        mvc.perform(post("/pendientes/" + mio + "/devolver").with(csrf())
                .param("version", String.valueOf(version(mio))).session(sesion));

        assertThat(acciones(mio)).containsExactly("CANCEL", "RESTORE");

        var fila = jdbc.sql("""
                SELECT actor_id, reason FROM audit_event
                WHERE entity_type = 'PENDING_TASK' AND entity_id = :id AND action = 'CANCEL'
                """).param("id", mio).query().singleRow();

        assertThat(fila.get("actor_id")).isEqualTo(yo);
        assertThat(fila.get("reason"))
                .as("RF-005: no se pide motivo, pero el rastro queda igual")
                .isNull();
    }

    @Test
    @DisplayName("un abogado no puede cancelar el pendiente de otro (RF-004)")
    void noCancelaLoAjeno() throws Exception {
        mvc.perform(post("/pendientes/" + ajeno + "/cancelar").with(csrf())
                        .param("version", String.valueOf(version(ajeno))).session(sesion))
                .andExpect(status().isForbidden());

        assertThat(visible(ajeno)).isTrue();
        assertThat(acciones(ajeno)).isEmpty();
    }

    @Test
    @DisplayName("la jefa si puede (RF-004)")
    void laJefaSi() throws Exception {
        MockHttpSession deLaJefa = SesionDePrueba.entrar(mvc, "jefa@ejemplo.test");

        mvc.perform(post("/pendientes/" + ajeno + "/cancelar").with(csrf())
                        .param("version", String.valueOf(version(ajeno))).session(deLaJefa))
                .andExpect(status().is3xxRedirection());

        assertThat(visible(ajeno)).isFalse();
    }

    @Test
    @DisplayName("una version desfasada da conflicto y no aplica el cambio (RF-009)")
    void conflictoDeEdicion() throws Exception {
        long vieja = version(mio);
        jdbc.sql("UPDATE pending_task SET version = version + 1 WHERE id = :id")
                .param("id", mio).update();

        mvc.perform(post("/pendientes/" + mio + "/cancelar").with(csrf())
                        .param("version", String.valueOf(vieja)).session(sesion))
                .andExpect(status().isConflict());

        assertThat(visible(mio)).isTrue();
    }

    @Test
    @DisplayName("cancelar algo ya cancelado no escribe una segunda entrada")
    void cancelarDosVecesNoDuplica() throws Exception {
        mvc.perform(post("/pendientes/" + mio + "/cancelar").with(csrf())
                .param("version", String.valueOf(version(mio))).session(sesion));
        mvc.perform(post("/pendientes/" + mio + "/cancelar").with(csrf())
                .param("version", String.valueOf(version(mio))).session(sesion));

        assertThat(acciones(mio))
                .as("el historial es inmutable: no puede llenarse de entradas sin cambio")
                .containsExactly("CANCEL");
    }

    @Test
    @DisplayName("la ficha ofrece cancelar, y devolver cuando ya esta cancelado")
    void laFichaOfreceElBoton() throws Exception {
        assertThat(mvc.perform(get("/pendientes/" + mio).session(sesion))
                        .andReturn().getResponse().getContentAsString())
                .contains("Cancelar")
                .doesNotContain("Devolver a la lista");

        jdbc.sql("UPDATE pending_task SET active = false WHERE id = :id")
                .param("id", mio).update();

        assertThat(mvc.perform(get("/pendientes/" + mio).session(sesion))
                        .andReturn().getResponse().getContentAsString())
                .contains("Devolver a la lista")
                .contains("Este pendiente está cancelado");
    }
}
