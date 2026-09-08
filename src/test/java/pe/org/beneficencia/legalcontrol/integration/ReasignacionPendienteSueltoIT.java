package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.assignment.ReassignmentService;

/**
 * Los pendientes sueltos tambien se pueden mover (decision del cliente, 2026-09-08).
 *
 * <p>Sin esto, los pendientes que no cuelgan de ningun expediente quedarian
 * inmovilizados cuando su responsable deja el area: las secciones 5.2 y 5.3 solo
 * describen mover expedientes, y no habria ninguna otra via.
 *
 * <p>Un pendiente <b>vinculado</b> no se mueve por separado: se reasigna su
 * expediente, y eso los mueve todos.
 */
@AutoConfigureMockMvc
class ReasignacionPendienteSueltoIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ReassignmentService reasignaciones;

    private CuentaActual jefa;
    private UUID abogadaA;
    private UUID abogadoB;
    private UUID tipo;
    private UUID prioridad;
    private UUID estado;

    @BeforeEach
    void preparar() {
        SesionDePrueba.limpiar(jdbc);
        UUID jefaId = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        abogadaA = SesionDePrueba.crearCuenta(jdbc, encoder, "a@ejemplo.test", "LAWYER");
        abogadoB = SesionDePrueba.crearCuenta(jdbc, encoder, "b@ejemplo.test", "LAWYER");
        jefa = ReasignacionIT.cuenta(jefaId, "HEAD");

        var ahora = java.sql.Timestamp.from(java.time.Instant.now());
        tipo = DatosSinteticos.sembrarCatalogo(jdbc, "pending_task_type", "T", 1,
                abogadaA, ahora).get(0);
        prioridad = DatosSinteticos.sembrarCatalogo(jdbc, "priority", "P", 1,
                abogadaA, ahora).get(0);
        estado = DatosSinteticos.sembrarCatalogo(jdbc, "pending_task_status", "E", 1,
                abogadaA, ahora).get(0);
    }

    private UUID suelto() {
        return DatosSinteticos.pendienteSuelto(jdbc, abogadaA, tipo, prioridad, estado,
                "Consulta suelta", LocalDate.now().plusDays(5), LocalDate.now().minusDays(2));
    }

    private long version(UUID id) {
        return jdbc.sql("SELECT version FROM pending_task WHERE id = :id")
                .param("id", id).query(Long.class).single();
    }

    private UUID responsable(UUID id) {
        return jdbc.sql("SELECT owner_id FROM pending_task WHERE id = :id")
                .param("id", id).query(UUID.class).single();
    }

    @Test
    @DisplayName("un pendiente sin expediente cambia de responsable")
    void elSueltoSeMueve() {
        UUID id = suelto();

        var resultado = reasignaciones.reasignarPendienteSuelto(id, abogadoB, version(id), jefa);

        assertThat(resultado.correcto()).isTrue();
        assertThat(responsable(id)).isEqualTo(abogadoB);
    }

    @Test
    @DisplayName("y queda en su historial quien lo movio")
    void quedaEnElHistorial() {
        UUID id = suelto();

        reasignaciones.reasignarPendienteSuelto(id, abogadoB, version(id), jefa);

        var fila = jdbc.sql("""
                SELECT owner_id, after_values ->> 'ownerId' AS nuevo FROM audit_event
                WHERE entity_id = :id AND action = 'REASSIGN'
                """).param("id", id).query().singleRow();
        assertThat(fila.get("owner_id")).isEqualTo(abogadaA);
        assertThat(fila.get("nuevo")).isEqualTo(abogadoB.toString());
    }

    @Test
    @DisplayName("un pendiente vinculado NO se reasigna por separado")
    void elVinculadoNoSeMuevePorSuCuenta() {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 1, 0);
        UUID vinculado = ids.get(1);

        var resultado = reasignaciones.reasignarPendienteSuelto(vinculado, abogadoB,
                version(vinculado), jefa);

        assertThat(resultado.correcto()).isFalse();
        assertThat(resultado.error()).contains("pertenece a un expediente");
        assertThat(responsable(vinculado)).isEqualTo(abogadaA);
    }

    @Test
    @DisplayName("un abogado no reasigna un pendiente suelto ajeno")
    void soloLaJefatura() {
        UUID id = suelto();

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                reasignaciones.reasignarPendienteSuelto(id, abogadoB, version(id),
                        ReasignacionIT.cuenta(abogadoB, "LAWYER")))
                .isInstanceOf(pe.org.beneficencia.legalcontrol.shared.ErrorHandling.SinPermiso.class);
    }

    @Test
    @DisplayName("la ficha ofrece el formulario al suelto y el camino correcto al vinculado")
    void laFichaOrienta() throws Exception {
        UUID libre = suelto();
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 1, 0);
        var sesion = SesionDePrueba.entrar(mvc, "jefa@ejemplo.test");

        String delSuelto = mvc.perform(get("/pendientes/" + libre).session(sesion))
                .andReturn().getResponse().getContentAsString();
        assertThat(delSuelto).contains("Cambiar de responsable");

        String delVinculado = mvc.perform(get("/pendientes/" + ids.get(1)).session(sesion))
                .andReturn().getResponse().getContentAsString();
        assertThat(delVinculado)
                .as("un boton ausente sin explicacion deja a la jefa sin saber que hacer")
                .doesNotContain("Cambiar de responsable")
                .contains("Se reasigna reasignando el expediente");
    }
}
