package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import pe.org.beneficencia.legalcontrol.audit.AuditRecorder;

/**
 * La evidencia solo recoge modificaciones efectivas, y va atada a ellas.
 *
 * <p>Lo importante no es que se escriba cuando toca, sino que <b>no</b> se escriba
 * cuando no toca, y que un fallo al auditar impida guardar el cambio. Sin eso,
 * el historial seria una sugerencia.
 */
class AuditImmutabilityIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private AuditRecorder auditoria;
    @Autowired private TransactionTemplate tx;
    @Autowired private Clock clock;

    private UUID usuario;

    @BeforeEach
    void prepararUsuario() {
        jdbc.sql("DELETE FROM case_history_status_reference").update();
        jdbc.sql("DELETE FROM audit_event").update();
        jdbc.sql("DELETE FROM judicial_case").update();
        jdbc.sql("DELETE FROM app_user").update();

        usuario = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO app_user (id, name, email, role, status, auth_version, version,
                                      created_by, created_at, updated_at)
                VALUES (:id, 'Jefa de prueba', 'jefa@ejemplo.test', 'HEAD', 'ACTIVE', 1, 1,
                        :id, :ahora, :ahora)
                """)
                .param("id", usuario).param("ahora", ahora).update();
    }

    private int eventos() {
        return jdbc.sql("SELECT count(*) FROM audit_event").query(Integer.class).single();
    }

    @Test
    @DisplayName("un guardado sin cambios no escribe evidencia")
    void noOpNoEscribe() {
        Map<String, Object> valores = Map.of("subject", "Desalojo");

        var escribio = auditoria.registrar(
                "JUDICIAL_CASE", UUID.randomUUID(), "UPDATE", usuario, usuario,
                valores, valores, null);

        assertThat(escribio).as("un guardado sin cambios no escribe evidencia").isEmpty();
        assertThat(eventos()).isZero();
    }

    @Test
    @DisplayName("una modificacion real escribe una entrada con autor y responsable")
    void cambioRealEscribe() {
        UUID expediente = UUID.randomUUID();

        var escribio = auditoria.registrar(
                "JUDICIAL_CASE", expediente, "UPDATE", usuario, usuario,
                Map.of("subject", "Desalojo"), Map.of("subject", "Desalojo y pago"), null);

        assertThat(escribio).isPresent();
        assertThat(eventos()).isEqualTo(1);

        String accion = jdbc.sql("SELECT action FROM audit_event WHERE entity_id = :id")
                .param("id", expediente).query(String.class).single();
        assertThat(accion).isEqualTo("UPDATE");
    }

    @Test
    @DisplayName("si falla la evidencia, tampoco se guarda el cambio")
    void fallaLaEvidenciaYSeRevierteElCambio() {
        UUID expediente = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(clock.instant());

        assertThatThrownBy(() -> tx.executeWithoutResult(estado -> {
            jdbc.sql("""
                    INSERT INTO judicial_case (id, owner_id, case_number, created_at, updated_at)
                    VALUES (:id, :owner, 'EXP-001-2026', :ahora, :ahora)
                    """)
                    .param("id", expediente).param("owner", usuario).param("ahora", ahora).update();

            // Tipo de entidad invalido: la restriccion CHECK lo rechaza. Simula
            // cualquier fallo al escribir la evidencia.
            auditoria.registrar("ENTIDAD_INVENTADA", expediente, "CREATE", usuario, usuario,
                    null, Map.of("caseNumber", "EXP-001-2026"), null);
        })).isInstanceOf(Exception.class);

        Integer expedientes = jdbc.sql("SELECT count(*) FROM judicial_case WHERE id = :id")
                .param("id", expediente).query(Integer.class).single();

        assertThat(expedientes).as("el expediente no debe existir si su evidencia no pudo escribirse")
                .isZero();
        assertThat(eventos()).isZero();
    }

    @Test
    @DisplayName("consultar no genera evidencia")
    void consultarNoEscribe() {
        jdbc.sql("SELECT count(*) FROM judicial_case").query(Integer.class).single();
        jdbc.sql("SELECT count(*) FROM app_user").query(Integer.class).single();
        assertThat(eventos()).isZero();
    }
}
