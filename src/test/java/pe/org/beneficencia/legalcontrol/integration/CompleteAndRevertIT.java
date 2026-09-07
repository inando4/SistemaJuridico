package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import pe.org.beneficencia.legalcontrol.shared.ErrorHandling;
import pe.org.beneficencia.legalcontrol.shared.Paging;

/**
 * Cumplir un pendiente y deshacerlo.
 *
 * <p>El insumo senala expresamente que hoy no hay salida: una vez cumplido, el
 * pendiente desaparece de la lista activa (seccion 20.1). Estas pruebas comprueban
 * que la vuelta atras existe, que exige motivo, y que <b>no borra la historia</b>.
 */
class CompleteAndRevertIT extends PostgresIntegrationTest {

    private static final LocalDate PROGRAMADA = LocalDate.of(2026, 9, 10);

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
                VALUES (:id, :owner, 'Elaborar informe legal', :hoy, :programada,
                        true, :ahora, :ahora, 1)
                """)
                .param("id", pendiente).param("owner", id).param("hoy", LocalDate.now())
                .param("programada", PROGRAMADA).param("ahora", ahora).update();
    }

    private long version() {
        return jdbc.sql("SELECT version FROM pending_task WHERE id = :id")
                .param("id", pendiente).query(Long.class).single();
    }

    private Object completadoEn() {
        return jdbc.sql("SELECT completed_at FROM pending_task WHERE id = :id")
                .param("id", pendiente).query().singleRow().get("completed_at");
    }

    private LocalDate programadaActual() {
        var v = jdbc.sql("SELECT scheduled_for FROM pending_task WHERE id = :id")
                .param("id", pendiente).query().singleRow().get("scheduled_for");
        return v == null ? null : ((java.sql.Date) v).toLocalDate();
    }

    @Test
    @DisplayName("marcar cumplido fija el instante real")
    void cumplirFijaElInstante() {
        assertThat(acciones.marcarCumplido(pendiente, version(), abogado)).isEmpty();

        assertThat(completadoEn()).isNotNull();
    }

    @Test
    @DisplayName("marcar cumplido NO toca la fecha programada")
    void cumplirNoTocaLaFechaProgramada() {
        acciones.marcarCumplido(pendiente, version(), abogado);

        assertThat(programadaActual())
                .as("asi, al revertir, la fecha no hay que recuperarla: nunca se perdio")
                .isEqualTo(PROGRAMADA);
    }

    @Test
    @DisplayName("revertir sin motivo se rechaza")
    void revertirSinMotivoSeRechaza() {
        acciones.marcarCumplido(pendiente, version(), abogado);

        var problema = acciones.revertirCumplimiento(pendiente, version(), "  ", abogado);

        assertThat(problema).get().asString().contains("motivo");
        assertThat(completadoEn()).as("sigue cumplido: no se cambio nada").isNotNull();
    }

    @Test
    @DisplayName("revertir con motivo devuelve el pendiente a la lista activa")
    void revertirConMotivo() {
        acciones.marcarCumplido(pendiente, version(), abogado);

        assertThat(acciones.revertirCumplimiento(pendiente, version(),
                "Me equivoque de tarea", abogado)).isEmpty();

        assertThat(completadoEn()).isNull();
        assertThat(programadaActual()).as("conserva la fecha que tenia antes")
                .isEqualTo(PROGRAMADA);
    }

    @Test
    @DisplayName("revertir deja DOS entradas: la original no se borra ni se edita")
    void revertirConservaLaHistoria() {
        acciones.marcarCumplido(pendiente, version(), abogado);
        acciones.revertirCumplimiento(pendiente, version(), "Me equivoque", abogado);

        var entradas = historial.deEntidad("PENDING_TASK", pendiente, Paging.of(0));

        assertThat(entradas).hasSize(2);
        assertThat(entradas.get(0).action()).isEqualTo("REVERT_COMPLETION");
        assertThat(entradas.get(0).reason()).isEqualTo("Me equivoque");
        assertThat(entradas.get(1).action())
                .as("el cumplimiento original sigue ahi").isEqualTo("COMPLETE");
    }

    @Test
    @DisplayName("revertir algo no cumplido se rechaza")
    void revertirLoNoCumplidoSeRechaza() {
        assertThatThrownBy(() -> acciones.revertirCumplimiento(
                pendiente, version(), "Un motivo", abogado))
                .isInstanceOf(ErrorHandling.ConflictoDeEdicion.class);
    }

    @Test
    @DisplayName("cumplir dos veces es no-op y no duplica historial")
    void cumplirDosVecesNoDuplica() {
        acciones.marcarCumplido(pendiente, version(), abogado);
        acciones.marcarCumplido(pendiente, version(), abogado);

        assertThat(historial.deEntidad("PENDING_TASK", pendiente, Paging.of(0))).hasSize(1);
    }

    @Test
    @DisplayName("si la fecha recuperada ya paso, el pendiente reaparece vencido")
    void alRevertirPuedeReaparecerVencido() {
        // Fecha del pasado: es correcto y es lo que el abogado necesita ver.
        jdbc.sql("UPDATE pending_task SET scheduled_for = DATE '2020-01-15' WHERE id = :id")
                .param("id", pendiente).update();

        acciones.marcarCumplido(pendiente, version(), abogado);
        acciones.revertirCumplimiento(pendiente, version(), "Revision", abogado);

        assertThat(programadaActual()).isEqualTo(LocalDate.of(2020, 1, 15));
    }
}
