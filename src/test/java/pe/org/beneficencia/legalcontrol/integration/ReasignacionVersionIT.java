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
 * Reasignar sube la version, y eso es lo buscado.
 *
 * <p>Quien tuviera abierto el formulario de uno de esos pendientes lo cargo cuando
 * el registro era de otra persona. Que reciba el aviso de conflicto en vez de
 * sobrescribir en silencio es correcto, no un efecto colateral: por eso tiene
 * prueba propia.
 */
class ReasignacionVersionIT extends PostgresIntegrationTest {

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

    private long version(String tabla, UUID id) {
        return jdbc.sql("SELECT version FROM " + tabla + " WHERE id = :id")
                .param("id", id).query(Long.class).single();
    }

    @Test
    @DisplayName("la version sube en el expediente y en cada pendiente movido")
    void subeLaVersionDeTodos() {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 2, 1);
        UUID expediente = ids.get(0);
        long antes = version("judicial_case", expediente);

        reasignaciones.reasignarExpediente(Tipo.JUDICIAL, expediente, abogadoB, antes, jefa);

        assertThat(version("judicial_case", expediente)).isEqualTo(antes + 1);
        for (UUID pendiente : ids.subList(1, ids.size())) {
            assertThat(version("pending_task", pendiente))
                    .as("pendiente %s", pendiente)
                    .isEqualTo(2L);
        }
    }

    @Test
    @DisplayName("con una version desfasada se avisa del conflicto en vez de pisar")
    void versionDesfasadaAvisa() {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 1, 0);
        UUID expediente = ids.get(0);

        assertThatThrownBy(() -> reasignaciones.reasignarExpediente(Tipo.JUDICIAL, expediente,
                abogadoB, version("judicial_case", expediente) + 5, jefa))
                .isInstanceOf(ErrorHandling.ConflictoDeEdicion.class);

        assertThat(jdbc.sql("SELECT owner_id FROM judicial_case WHERE id = :id")
                .param("id", expediente).query(UUID.class).single())
                .isEqualTo(abogadaA);
    }
}
