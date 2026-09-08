package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

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

import pe.org.beneficencia.legalcontrol.assignment.ReassignmentService;
import pe.org.beneficencia.legalcontrol.assignment.ReassignmentService.Tipo;

/**
 * Los permisos cambian con el responsable (insumo, seccion 5.3).
 *
 * <p>Sale gratis del modelo —{@code PendingTaskAuthorization} decide a partir de
 * {@code owner_id}—, pero es una <b>decision explicita</b> del cliente: el nuevo
 * responsable puede revertir un cumplido que marco el anterior. Una decision
 * declarada merece prueba propia, no darse por supuesta.
 */
@AutoConfigureMockMvc
class PermisosTrasReasignarIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ReassignmentService reasignaciones;
    @Autowired private pe.org.beneficencia.legalcontrol.pendingtask.PendingTaskActionService acciones;

    private UUID abogadaA;
    private UUID abogadoB;
    private List<UUID> ids;

    @BeforeEach
    void preparar() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        UUID jefaId = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        abogadaA = SesionDePrueba.crearCuenta(jdbc, encoder, "a@ejemplo.test", "LAWYER");
        abogadoB = SesionDePrueba.crearCuenta(jdbc, encoder, "b@ejemplo.test", "LAWYER");

        // Un expediente de A con un activo y un cumplido, ya reasignado a B.
        ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 1, 1);
        long version = jdbc.sql("SELECT version FROM judicial_case WHERE id = :id")
                .param("id", ids.get(0)).query(Long.class).single();
        reasignaciones.reasignarExpediente(Tipo.JUDICIAL, ids.get(0), abogadoB, version,
                ReasignacionIT.cuenta(jefaId, "HEAD"));
    }

    private UUID cumplido() {
        return jdbc.sql("""
                SELECT id FROM pending_task
                WHERE judicial_case_id = :exp AND completed_at IS NOT NULL LIMIT 1
                """).param("exp", ids.get(0)).query(UUID.class).single();
    }

    private long versionPendiente(UUID id) {
        return jdbc.sql("SELECT version FROM pending_task WHERE id = :id")
                .param("id", id).query(Long.class).single();
    }

    @Test
    @DisplayName("el nuevo responsable revierte un cumplido que marco el anterior")
    void elNuevoPuedeRevertirLoDelAnterior() {
        UUID id = cumplido();

        var reparo = acciones.revertirCumplimiento(id, versionPendiente(id),
                "En realidad no estaba hecho", ReasignacionIT.cuenta(abogadoB, "LAWYER"));

        assertThat(reparo)
                .as("al reasignarse, el trabajo pendiente pasa a ser decision del nuevo")
                .isEmpty();
        assertThat(jdbc.sql("SELECT completed_at FROM pending_task WHERE id = :id")
                .param("id", id).query(java.sql.Timestamp.class).optional())
                .isEmpty();
    }

    @Test
    @DisplayName("el responsable anterior ya no puede revertir lo que el mismo marco")
    void elAnteriorYaNoRevierteLoSuyo() {
        UUID id = cumplido();

        assertThatThrownBy(() -> acciones.revertirCumplimiento(id, versionPendiente(id),
                "Me arrepiento", ReasignacionIT.cuenta(abogadaA, "LAWYER")))
                .as("el anterior deja de tener injerencia sobre el trabajo traspasado")
                .isInstanceOf(pe.org.beneficencia.legalcontrol.shared.ErrorHandling.SinPermiso.class);
    }

    @Test
    @DisplayName("el responsable anterior pierde la escritura y conserva la lectura")
    void elAnteriorSoloLee() throws Exception {
        MockHttpSession sesionA = SesionDePrueba.entrar(mvc, "a@ejemplo.test");
        UUID activo = jdbc.sql("""
                SELECT id FROM pending_task
                WHERE judicial_case_id = :exp AND completed_at IS NULL LIMIT 1
                """).param("exp", ids.get(0)).query(UUID.class).single();

        // Leer si puede: la visibilidad es total y no depende del responsable.
        mvc.perform(get("/pendientes/" + activo).session(sesionA))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk());

        // Editar no: dejo de ser suyo.
        mvc.perform(get("/pendientes/" + activo + "/editar").session(sesionA))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isForbidden());
    }
}
