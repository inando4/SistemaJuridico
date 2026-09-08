package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.assignment.ReassignmentService;
import pe.org.beneficencia.legalcontrol.assignment.ReassignmentService.Tipo;

/**
 * El historial de una reasignacion (constitucion, principio VII).
 *
 * <p>La comprobacion que de verdad importa es la ultima: un pendiente colgado del
 * expediente puede pertenecer a una <b>tercera</b> persona, y su entrada tiene que
 * decir de quien era en realidad. Suponer que todos eran del responsable saliente
 * produce un historial verosimil y falso.
 */
class ReasignacionHistorialIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ReassignmentService reasignaciones;

    private CuentaActual jefa;
    private UUID jefaId;
    private UUID abogadaA;
    private UUID abogadoB;
    private UUID abogadaC;

    @BeforeEach
    void preparar() {
        SesionDePrueba.limpiar(jdbc);
        jefaId = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        abogadaA = SesionDePrueba.crearCuenta(jdbc, encoder, "a@ejemplo.test", "LAWYER");
        abogadoB = SesionDePrueba.crearCuenta(jdbc, encoder, "b@ejemplo.test", "LAWYER");
        abogadaC = SesionDePrueba.crearCuenta(jdbc, encoder, "c@ejemplo.test", "LAWYER");
        jefa = ReasignacionIT.cuenta(jefaId, "HEAD");
    }

    private long version(UUID id) {
        return jdbc.sql("SELECT version FROM judicial_case WHERE id = :id")
                .param("id", id).query(Long.class).single();
    }

    @Test
    @DisplayName("el expediente registra el responsable anterior, el nuevo y quien lo hizo")
    void historialDelExpediente() {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 2, 0);

        reasignaciones.reasignarExpediente(Tipo.JUDICIAL, ids.get(0), abogadoB,
                version(ids.get(0)), jefa);

        var fila = jdbc.sql("""
                SELECT actor_id, owner_id, before_values ->> 'ownerId' AS antes,
                       after_values ->> 'ownerId' AS despues
                FROM audit_event
                WHERE entity_type = 'JUDICIAL_CASE' AND action = 'REASSIGN'
                  AND entity_id = :id
                """).param("id", ids.get(0)).query().singleRow();

        assertThat(fila.get("actor_id")).as("la jefa es la autora").isEqualTo(jefaId);
        assertThat(fila.get("owner_id"))
                .as("el responsable en el momento del cambio era A")
                .isEqualTo(abogadaA);
        assertThat(fila.get("antes")).isEqualTo(abogadaA.toString());
        assertThat(fila.get("despues")).isEqualTo(abogadoB.toString());
    }

    @Test
    @DisplayName("cada pendiente movido tiene su propia entrada, no un resumen")
    void unaEntradaPorPendiente() {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 3, 2);

        reasignaciones.reasignarExpediente(Tipo.JUDICIAL, ids.get(0), abogadoB,
                version(ids.get(0)), jefa);

        Integer entradas = jdbc.sql("""
                SELECT count(*) FROM audit_event
                WHERE entity_type = 'PENDING_TASK' AND action = 'REASSIGN'
                """).query(Integer.class).single();
        assertThat(entradas)
                .as("quien abra un pendiente tiene que ver por que cambio de manos")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("el pendiente de una tercera persona conserva SU responsable anterior")
    void elTerceroConservaSuNombre() {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 1, 0);
        UUID expediente = ids.get(0);
        // C registra un pendiente sobre el expediente de A: nada lo impide hoy.
        UUID deC = DatosSinteticos.pendienteDeExpediente(jdbc, abogadaC, expediente,
                "Informe de C", false);

        reasignaciones.reasignarExpediente(Tipo.JUDICIAL, expediente, abogadoB,
                version(expediente), jefa);

        String antes = jdbc.sql("""
                SELECT before_values ->> 'ownerId' FROM audit_event
                WHERE entity_type = 'PENDING_TASK' AND entity_id = :id AND action = 'REASSIGN'
                """).param("id", deC).query(String.class).single();

        assertThat(antes)
                .as("era de C, no de A: dar por hecho lo contrario falsea la evidencia")
                .isEqualTo(abogadaC.toString());
    }

    @Test
    @DisplayName("la entrada del pendiente dice de que expediente venia el traspaso")
    void elMotivoExplicaElTraspaso() {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 1, 0);

        reasignaciones.reasignarExpediente(Tipo.JUDICIAL, ids.get(0), abogadoB,
                version(ids.get(0)), jefa);

        String motivo = jdbc.sql("""
                SELECT reason FROM audit_event
                WHERE entity_type = 'PENDING_TASK' AND action = 'REASSIGN' LIMIT 1
                """).query(String.class).single();
        assertThat(motivo).contains("reasignar el expediente");
    }
}
