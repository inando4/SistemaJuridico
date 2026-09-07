package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.audit.AuditQueryRepository;
import pe.org.beneficencia.legalcontrol.pendingtask.PendingTaskActionService;
import pe.org.beneficencia.legalcontrol.shared.Paging;

/**
 * El historial de un pendiente es inmutable y distingue autor de responsable.
 *
 * <p>Reutiliza {@code audit_event}, de modo que hereda la garantia que la base ya
 * daba: la aplicacion solo puede leer e insertar.
 */
class CompletionHistoryIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private PendingTaskActionService acciones;
    @Autowired private AuditQueryRepository historial;

    private UUID pendiente;
    private UUID abogado;
    private CuentaActual jefa;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        UUID idJefa = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        jefa = new CuentaActual(idJefa, "Jefa", "jefa@ejemplo.test", "HEAD", 1,
                Instant.now().getEpochSecond());

        pendiente = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, registered_at, scheduled_for,
                                          active, created_at, updated_at, version)
                VALUES (:id, :owner, 'Tarea del abogado', :hoy, :hoy, true, :ahora, :ahora, 1)
                """).param("id", pendiente).param("owner", abogado)
                .param("hoy", LocalDate.now()).param("ahora", ahora).update();
    }

    private long version() {
        return jdbc.sql("SELECT version FROM pending_task WHERE id = :id")
                .param("id", pendiente).query(Long.class).single();
    }

    @Test
    @DisplayName("cuando la jefatura interviene, el historial distingue autor de responsable")
    void autorDistintoDeResponsable() {
        acciones.marcarCumplido(pendiente, version(), jefa);

        var entradas = historial.deEntidad("PENDING_TASK", pendiente, Paging.of(0));

        assertThat(entradas).hasSize(1);
        assertThat(entradas.get(0).actorName()).contains("jefa");
        assertThat(entradas.get(0).ownerId()).as("el responsable sigue siendo el abogado")
                .isEqualTo(abogado);
        assertThat(entradas.get(0).intervencionAjena()).isTrue();
    }

    @Test
    @DisplayName("el historial no se puede alterar ni borrar")
    void historialInmutable() throws Exception {
        acciones.marcarCumplido(pendiente, version(), jefa);

        try (Connection app = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), "sistema_juridico_app", "test");
             Statement st = app.createStatement()) {

            assertThatThrownBy(() -> st.execute(
                    "UPDATE audit_event SET reason = 'falsificado'")).hasMessageContaining("denied");
            assertThatThrownBy(() -> st.execute(
                    "DELETE FROM audit_event")).hasMessageContaining("denied");
        }

        assertThat(historial.deEntidad("PENDING_TASK", pendiente, Paging.of(0))).hasSize(1);
    }

    @Test
    @DisplayName("el historial se lee del movimiento mas reciente al mas antiguo")
    void ordenCronologicoInverso() {
        acciones.marcarCumplido(pendiente, version(), jefa);
        acciones.revertirCumplimiento(pendiente, version(), "Primera correccion", jefa);
        acciones.marcarCumplido(pendiente, version(), jefa);

        var entradas = historial.deEntidad("PENDING_TASK", pendiente, Paging.of(0));

        assertThat(entradas).hasSize(3);
        assertThat(entradas.get(0).action()).isEqualTo("COMPLETE");
        assertThat(entradas.get(1).action()).isEqualTo("REVERT_COMPLETION");
        assertThat(entradas.get(2).action()).isEqualTo("COMPLETE");
    }

    @Test
    @DisplayName("consultar el historial no genera entradas")
    void consultarNoEscribe() {
        acciones.marcarCumplido(pendiente, version(), jefa);

        historial.deEntidad("PENDING_TASK", pendiente, Paging.of(0));
        historial.deEntidad("PENDING_TASK", pendiente, Paging.of(0));

        assertThat(historial.deEntidad("PENDING_TASK", pendiente, Paging.of(0))).hasSize(1);
    }
}
