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

import pe.org.beneficencia.legalcontrol.assignment.ReassignmentService;
import pe.org.beneficencia.legalcontrol.assignment.ReassignmentService.Tipo;

/**
 * O cambia todo, o no cambia nada.
 *
 * <p>El fallo se provoca de verdad, no se simula: se ejecuta con un actor que no
 * existe en {@code app_user}, asi que los {@code UPDATE} salen bien y despues
 * revienta la clave foranea al escribir el historial. Es el punto exacto que
 * importa —a mitad de la operacion, con los cambios ya aplicados— y comprueba de
 * paso que la evidencia se persiste en la misma transaccion que el cambio
 * (principio VII).
 *
 * <p>Sin atomicidad quedarian pendientes cuyo responsable ya no tiene el
 * expediente, y {@code PendingTaskAuthorization} daria permiso de escritura a
 * quien no corresponde.
 */
class ReasignacionAtomicaIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ReassignmentService reasignaciones;

    private UUID abogadaA;
    private UUID abogadoB;

    @BeforeEach
    void preparar() {
        SesionDePrueba.limpiar(jdbc);
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        abogadaA = SesionDePrueba.crearCuenta(jdbc, encoder, "a@ejemplo.test", "LAWYER");
        abogadoB = SesionDePrueba.crearCuenta(jdbc, encoder, "b@ejemplo.test", "LAWYER");
    }

    private long version(UUID id) {
        return jdbc.sql("SELECT version FROM judicial_case WHERE id = :id")
                .param("id", id).query(Long.class).single();
    }

    @Test
    @DisplayName("un fallo a mitad no deja ni el expediente ni un solo pendiente cambiado")
    void todoONada() {
        List<UUID> ids = DatosSinteticos.sembrarExpedienteConPendientes(jdbc, abogadaA, 3, 2);
        UUID expediente = ids.get(0);
        long versionInicial = version(expediente);

        // Jefa por rol, pero su id no existe en app_user: el historial no podra
        // escribirse y la transaccion caera con los UPDATE ya aplicados.
        var fantasma = ReasignacionIT.cuenta(UUID.randomUUID(), "HEAD");

        assertThatThrownBy(() -> reasignaciones.reasignarExpediente(Tipo.JUDICIAL, expediente,
                abogadoB, versionInicial, fantasma))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThat(jdbc.sql("SELECT owner_id FROM judicial_case WHERE id = :id")
                .param("id", expediente).query(UUID.class).single())
                .as("el expediente vuelve a su responsable")
                .isEqualTo(abogadaA);
        assertThat(version(expediente))
                .as("ni la version se queda subida")
                .isEqualTo(versionInicial);

        Integer movidos = jdbc.sql("""
                SELECT count(*) FROM pending_task
                WHERE judicial_case_id = :exp AND owner_id <> :a
                """).param("exp", expediente).param("a", abogadaA).query(Integer.class).single();
        assertThat(movidos)
                .as("ni uno solo de los cinco puede quedarse cambiado")
                .isZero();

        Integer historial = jdbc.sql(
                "SELECT count(*) FROM audit_event WHERE action = 'REASSIGN'")
                .query(Integer.class).single();
        assertThat(historial)
                .as("tampoco puede quedar evidencia de un cambio que no ocurrio")
                .isZero();
    }
}
