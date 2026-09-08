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
 * Sin cambios no hay historial, y «sin cambios» se mide <b>por registro</b>.
 *
 * <p>Es la parte que una comprobacion global se saltaria: un expediente que ya es
 * de B puede tener pendientes de A. Reasignarlo a B no cambia el expediente, pero
 * si esos pendientes, y no moverlos dejaria la propiedad dispersa justo donde la
 * seccion 5.3 quiere lo contrario.
 */
class ReasignacionSinCambiosIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ReassignmentService reasignaciones;

    private CuentaActual jefa;
    private UUID abogadaA;
    private UUID abogadoB;

    @BeforeEach
    void preparar() {
        SesionDePrueba.limpiar(jdbc);
        UUID jefaId = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        abogadaA = SesionDePrueba.crearCuenta(jdbc, encoder, "a@ejemplo.test", "LAWYER");
        abogadoB = SesionDePrueba.crearCuenta(jdbc, encoder, "b@ejemplo.test", "LAWYER");
        jefa = ReasignacionIT.cuenta(jefaId, "HEAD");
    }

    private long version(UUID id) {
        return jdbc.sql("SELECT version FROM judicial_case WHERE id = :id")
                .param("id", id).query(Long.class).single();
    }

    private int entradasDeReasignacion() {
        Integer n = jdbc.sql("SELECT count(*) FROM audit_event WHERE action = 'REASSIGN'")
                .query(Integer.class).single();
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("reasignar a quien ya lo tiene no escribe historial")
    void aQuienYaLoTieneNoEscribeNada() {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 2, 0);

        var resultado = reasignaciones.reasignarExpediente(Tipo.JUDICIAL, ids.get(0), abogadaA,
                version(ids.get(0)), jefa);

        assertThat(resultado.correcto()).isFalse();
        assertThat(resultado.error()).contains("ya está a nombre de esa persona");
        assertThat(entradasDeReasignacion())
                .as("un guardado sin cambios no genera historial (principio VII)")
                .isZero();
    }

    @Test
    @DisplayName("un expediente que ya es de B pero con pendientes de A SI cambia")
    void laComprobacionEsPorRegistroNoGlobal() {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 2, 0);
        UUID expediente = ids.get(0);
        // El expediente pasa a B, pero sus pendientes se quedan a nombre de A.
        jdbc.sql("UPDATE judicial_case SET owner_id = :b WHERE id = :id")
                .param("b", abogadoB).param("id", expediente).update();

        var resultado = reasignaciones.reasignarExpediente(Tipo.JUDICIAL, expediente, abogadoB,
                version(expediente), jefa);

        assertThat(resultado.correcto())
                .as("no es un no-op: quedaban dos pendientes de A")
                .isTrue();
        assertThat(resultado.pendientesMovidos()).isEqualTo(2);
        assertThat(entradasDeReasignacion())
                .as("dos entradas de pendiente; el expediente no cambio y no lleva la suya")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("cuando nada cambia en absoluto, tampoco se toca la version")
    void nadaCambiaNiLaVersion() {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 1, 0);
        long antes = version(ids.get(0));

        reasignaciones.reasignarExpediente(Tipo.JUDICIAL, ids.get(0), abogadaA, antes, jefa);

        assertThat(version(ids.get(0))).isEqualTo(antes);
    }
}
