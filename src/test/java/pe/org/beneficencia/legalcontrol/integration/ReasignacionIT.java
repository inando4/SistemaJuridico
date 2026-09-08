package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;

/**
 * El expediente viaja completo (insumo, seccion 5.3).
 *
 * <p>Lo que mas importa aqui es que los <b>cumplidos</b> tambien se muevan. El
 * fallo natural es filtrar por activos y dejar atras el historial de trabajo, y el
 * insumo dice literalmente lo contrario.
 */
class ReasignacionIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ReassignmentService reasignaciones;

    private UUID jefaId;
    private CuentaActual jefa;
    private UUID abogadaA;
    private UUID abogadoB;

    @BeforeEach
    void preparar() {
        SesionDePrueba.limpiar(jdbc);
        jefaId = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        abogadaA = SesionDePrueba.crearCuenta(jdbc, encoder, "a@ejemplo.test", "LAWYER");
        abogadoB = SesionDePrueba.crearCuenta(jdbc, encoder, "b@ejemplo.test", "LAWYER");
        jefa = cuenta(jefaId, "HEAD");
    }

    static CuentaActual cuenta(UUID id, String rol) {
        return new CuentaActual(id, "Usuario", "u@ejemplo.test", rol, 1L,
                java.time.Instant.now().getEpochSecond());
    }

    private UUID responsableDe(String tabla, UUID id) {
        return jdbc.sql("SELECT owner_id FROM " + tabla + " WHERE id = :id")
                .param("id", id).query(UUID.class).single();
    }

    private long versionDe(String tabla, UUID id) {
        return jdbc.sql("SELECT version FROM " + tabla + " WHERE id = :id")
                .param("id", id).query(Long.class).single();
    }

    @Test
    @DisplayName("el expediente y sus pendientes, cumplidos incluidos, cambian de manos")
    void expedienteCompleto() {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 3, 2);
        UUID expediente = ids.get(0);

        var resultado = reasignaciones.reasignarExpediente(Tipo.JUDICIAL, expediente, abogadoB,
                versionDe("judicial_case", expediente), jefa);

        assertThat(resultado.correcto()).isTrue();
        assertThat(resultado.pendientesMovidos())
                .as("los cinco: tres activos y dos cumplidos")
                .isEqualTo(5);
        assertThat(responsableDe("judicial_case", expediente)).isEqualTo(abogadoB);

        Integer deA = jdbc.sql("""
                SELECT count(*) FROM pending_task
                WHERE judicial_case_id = :exp AND owner_id = :a
                """).param("exp", expediente).param("a", abogadaA).query(Integer.class).single();
        assertThat(deA)
                .as("ninguno puede quedarse atras, y menos los cumplidos")
                .isZero();
    }

    @Test
    @DisplayName("los cumplidos siguen cumplidos: la reasignacion no toca su estado")
    void noAlteraElTrabajoHecho() {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 3, 2);

        reasignaciones.reasignarExpediente(Tipo.JUDICIAL, ids.get(0), abogadoB,
                versionDe("judicial_case", ids.get(0)), jefa);

        Integer cumplidos = jdbc.sql("""
                SELECT count(*) FROM pending_task
                WHERE judicial_case_id = :exp AND completed_at IS NOT NULL
                """).param("exp", ids.get(0)).query(Integer.class).single();
        assertThat(cumplidos)
                .as("cambia de quien es, no que se hizo")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("un procedimiento administrativo se reasigna igual que uno judicial")
    void administrativoIgualQueJudicial() {
        DatosSinteticos.sembrarAdministrativos(jdbc, abogadaA, 1, 1);
        // El ayudante devuelve el catalogo, no los procedimientos: se busca el creado.
        UUID procedimiento = jdbc.sql("SELECT id FROM administrative_procedure LIMIT 1")
                .query(UUID.class).single();

        var resultado = reasignaciones.reasignarExpediente(Tipo.ADMINISTRATIVO, procedimiento,
                abogadoB, versionDe("administrative_procedure", procedimiento), jefa);

        assertThat(resultado.correcto()).isTrue();
        assertThat(responsableDe("administrative_procedure", procedimiento)).isEqualTo(abogadoB);
    }

    @Test
    @DisplayName("un expediente sin pendientes se reasigna sin error")
    void expedienteVacio() {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 0, 0);

        var resultado = reasignaciones.reasignarExpediente(Tipo.JUDICIAL, ids.get(0), abogadoB,
                versionDe("judicial_case", ids.get(0)), jefa);

        assertThat(resultado.correcto()).isTrue();
        assertThat(resultado.pendientesMovidos()).isZero();
    }

    @Test
    @DisplayName("un expediente inexistente no se reasigna: se avisa")
    void expedienteInexistente() {
        assertThatThrownBy(() -> reasignaciones.reasignarExpediente(
                Tipo.JUDICIAL, UUID.randomUUID(), abogadoB, 1L, jefa))
                .isInstanceOf(ErrorHandling.NoEncontrado.class);
    }
}
