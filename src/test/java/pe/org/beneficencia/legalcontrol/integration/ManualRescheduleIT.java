package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

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

/** Reprogramacion manual: la persona elige la fecha (insumo, seccion 22). */
class ManualRescheduleIT extends PostgresIntegrationTest {

    private static final LocalDate ORIGINAL = LocalDate.of(2026, 9, 3);
    private static final LocalDate ELEGIDA = LocalDate.of(2026, 9, 8);

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private PendingTaskActionService acciones;
    @Autowired private AuditQueryRepository historial;

    private UUID pendiente;
    private CuentaActual abogado;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        UUID id = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        abogado = new CuentaActual(id, "Abogado", "abogado@ejemplo.test", "LAWYER", 1,
                Instant.now().getEpochSecond());

        pendiente = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, registered_at, scheduled_for,
                                          active, created_at, updated_at, version)
                VALUES (:id, :owner, 'Revisar convenio', :hoy, :original,
                        true, :ahora, :ahora, 1)
                """)
                .param("id", pendiente).param("owner", id).param("hoy", LocalDate.now())
                .param("original", ORIGINAL).param("ahora", ahora).update();
    }

    private long version() {
        return jdbc.sql("SELECT version FROM pending_task WHERE id = :id")
                .param("id", pendiente).query(Long.class).single();
    }

    @Test
    @DisplayName("el historial guarda fecha anterior, nueva e instante")
    void guardaAmbasFechas() {
        assertThat(acciones.reprogramar(pendiente, version(), ELEGIDA, null, abogado)).isEmpty();

        var entradas = historial.deEntidad("PENDING_TASK", pendiente, Paging.of(0));

        assertThat(entradas).hasSize(1);
        assertThat(entradas.get(0).action()).isEqualTo("RESCHEDULE");
        assertThat(entradas.get(0).beforeValues()).contains("2026-09-03");
        assertThat(entradas.get(0).afterValues()).contains("2026-09-08");
        assertThat(entradas.get(0).occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("el motivo es opcional en la reprogramacion manual")
    void motivoOpcional() {
        assertThat(acciones.reprogramar(pendiente, version(), ELEGIDA, null, abogado)).isEmpty();

        var entradas = historial.deEntidad("PENDING_TASK", pendiente, Paging.of(0));
        assertThat(entradas.get(0).reason())
                .as("solo revertir un cumplido lo exige").isNull();
    }

    @Test
    @DisplayName("si se indica motivo, se guarda")
    void motivoSeGuarda() {
        acciones.reprogramar(pendiente, version(), ELEGIDA, "La contraparte pidio prorroga", abogado);

        var entradas = historial.deEntidad("PENDING_TASK", pendiente, Paging.of(0));
        assertThat(entradas.get(0).reason()).isEqualTo("La contraparte pidio prorroga");
    }

    @Test
    @DisplayName("reprogramar a la misma fecha es no-op sin historial")
    void mismaFechaNoOp() {
        assertThat(acciones.reprogramar(pendiente, version(), ORIGINAL, null, abogado)).isEmpty();

        assertThat(historial.deEntidad("PENDING_TASK", pendiente, Paging.of(0))).isEmpty();
    }

    @Test
    @DisplayName("sin fecha se rechaza")
    void sinFechaSeRechaza() {
        assertThat(acciones.reprogramar(pendiente, version(), null, null, abogado))
                .get().asString().contains("fecha");
    }
}
