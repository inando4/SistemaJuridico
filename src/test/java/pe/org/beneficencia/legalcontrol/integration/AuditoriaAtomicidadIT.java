package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.pendingtask.PendingTaskActionService;
import pe.org.beneficencia.legalcontrol.pendingtask.PendingTaskForm;
import pe.org.beneficencia.legalcontrol.pendingtask.PendingTaskService;
import pe.org.beneficencia.legalcontrol.administrativeprocedure.AdministrativeProcedureForm;
import pe.org.beneficencia.legalcontrol.administrativeprocedure.AdministrativeProcedureService;

class AuditoriaAtomicidadIT extends PostgresIntegrationTest {
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired PendingTaskService pendientes;
    @Autowired PendingTaskActionService acciones;
    @Autowired AdministrativeProcedureService administrativos;
    CuentaActual jefa;

    @BeforeEach
    void preparar() {
        SesionDePrueba.limpiar(jdbc);
        UUID id = SesionDePrueba.crearCuenta(jdbc, encoder, "auditoria@ejemplo.test", "HEAD");
        jefa = new CuentaActual(id, "Jefatura", "auditoria@ejemplo.test", "HEAD", 1,
                Instant.now().getEpochSecond());
    }

    private void impedirAuditoria() {
        jdbc.sql("""
                CREATE FUNCTION auditoria_rechazar_prueba() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'Fallo de auditoría para comprobar rollback'; END;
                $$
                """).update();
        jdbc.sql("CREATE TRIGGER auditoria_rechazar_prueba BEFORE INSERT ON audit_event "
                + "FOR EACH ROW EXECUTE FUNCTION auditoria_rechazar_prueba()").update();
    }

    private void quitarFallo() {
        jdbc.sql("DROP TRIGGER IF EXISTS auditoria_rechazar_prueba ON audit_event").update();
        jdbc.sql("DROP FUNCTION IF EXISTS auditoria_rechazar_prueba()").update();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancelarYDevolverRetrocedenSiLaEvidenciaFalla(boolean estabaActivo) {
        var form = new PendingTaskForm("Pendiente de prueba", null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        UUID id = pendientes.crear(form, jefa.id()).id();
        jdbc.sql("UPDATE pending_task SET active = :activo WHERE id = :id")
                .param("activo", estabaActivo).param("id", id).update();
        int eventos = jdbc.sql("SELECT count(*) FROM audit_event").query(Integer.class).single();
        impedirAuditoria();
        try {
            assertThatThrownBy(() -> {
                if (estabaActivo) acciones.cancelar(id, 1, jefa);
                else acciones.devolver(id, 1, jefa);
            }).isInstanceOf(DataAccessException.class);
            var fila = jdbc.sql("SELECT active, version FROM pending_task WHERE id=:id")
                    .param("id", id).query().singleRow();
            assertThat(fila.get("active")).isEqualTo(estabaActivo);
            assertThat(((Number) fila.get("version")).longValue()).isEqualTo(1);
            assertThat(jdbc.sql("SELECT count(*) FROM audit_event").query(Integer.class).single())
                    .isEqualTo(eventos);
        } finally {
            quitarFallo();
        }
    }

    @Test
    void elAltaAdministrativaAsignadaTambienEsAtomica() {
        UUID abogada = SesionDePrueba.crearCuenta(jdbc, encoder, "abogada@ejemplo.test", "LAWYER");
        var form = new AdministrativeProcedureForm(null, "ADM-ATOMICIDAD", null, null,
                null, null, null, null, null);
        impedirAuditoria();
        try {
            assertThatThrownBy(() -> administrativos.crear(form, abogada, jefa.id()))
                    .isInstanceOf(DataAccessException.class);
            assertThat(jdbc.sql("SELECT count(*) FROM administrative_procedure")
                    .query(Integer.class).single()).isZero();
        } finally {
            quitarFallo();
        }
    }
}
