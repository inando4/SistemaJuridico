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
import pe.org.beneficencia.legalcontrol.calendar.CalendarReviewService;
import pe.org.beneficencia.legalcontrol.calendar.CalendarService;
import pe.org.beneficencia.legalcontrol.calendar.NonWorkingDayRepository;
import pe.org.beneficencia.legalcontrol.calendar.NonWorkingDayRepository.Tipo;
import pe.org.beneficencia.legalcontrol.pendingtask.PendingTaskActionService;
import pe.org.beneficencia.legalcontrol.shared.Paging;

/**
 * «No cumpli»: el sistema lleva la tarea al siguiente dia habil.
 *
 * <p>Lo que mas importa es la ultima prueba: <b>sin calendario revisado no se
 * elige fecha</b>. Reprogramar a un dia que resulto ser feriado es peor que no
 * reprogramar, porque nadie se entera hasta el vencimiento.
 */
class NotCompletedIT extends PostgresIntegrationTest {

    // Viernes 4 de septiembre de 2026.
    private static final LocalDate VIERNES = LocalDate.of(2026, 9, 4);

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private PendingTaskActionService acciones;
    @Autowired private CalendarService calendario;
    @Autowired private CalendarReviewService revisiones;
    @Autowired private NonWorkingDayRepository dias;
    @Autowired private AuditQueryRepository historial;

    private UUID pendiente;
    private CuentaActual jefa;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
        UUID id = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        jefa = new CuentaActual(id, "Jefa", "jefa@ejemplo.test", "HEAD", 1,
                Instant.now().getEpochSecond());

        pendiente = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, registered_at, scheduled_for,
                                          active, created_at, updated_at, version)
                VALUES (:id, :owner, 'Presentar escrito', :hoy, :viernes,
                        true, :ahora, :ahora, 1)
                """)
                .param("id", pendiente).param("owner", id).param("hoy", LocalDate.now())
                .param("viernes", VIERNES).param("ahora", ahora).update();
    }

    private void prepararCalendario(LocalDate... feriados) {
        for (int ano : new int[]{2026, 2027}) {
            calendario.agregar(LocalDate.of(ano, 1, 1), "Ano nuevo", Tipo.NATIONAL_HOLIDAY, jefa);
        }
        for (LocalDate feriado : feriados) {
            calendario.agregar(feriado, "Feriado de prueba", Tipo.NATIONAL_HOLIDAY, jefa);
        }
        for (int ano : new int[]{2026, 2027}) {
            revisiones.confirmar(ano, dias.revisionActual(ano).orElse(0L), true, true, jefa);
        }
    }

    private long version() {
        return jdbc.sql("SELECT version FROM pending_task WHERE id = :id")
                .param("id", pendiente).query(Long.class).single();
    }

    private LocalDate programada() {
        var v = jdbc.sql("SELECT scheduled_for FROM pending_task WHERE id = :id")
                .param("id", pendiente).query().singleRow().get("scheduled_for");
        return v == null ? null : ((java.sql.Date) v).toLocalDate();
    }

    @Test
    @DisplayName("de viernes pasa al lunes")
    void viernesALunes() {
        prepararCalendario();

        assertThat(acciones.declararNoCumplido(pendiente, version(), null, jefa)).isEmpty();

        assertThat(programada()).isEqualTo(LocalDate.of(2026, 9, 7));
    }

    @Test
    @DisplayName("si el lunes es feriado, salta al martes")
    void saltaElFeriado() {
        prepararCalendario(LocalDate.of(2026, 9, 7));

        acciones.declararNoCumplido(pendiente, version(), null, jefa);

        assertThat(programada()).isEqualTo(LocalDate.of(2026, 9, 8));
    }

    @Test
    @DisplayName("el pendiente sigue activo tras reprogramarse")
    void sigueActivo() {
        prepararCalendario();
        acciones.declararNoCumplido(pendiente, version(), null, jefa);

        Boolean activo = jdbc.sql("SELECT active FROM pending_task WHERE id = :id")
                .param("id", pendiente).query(Boolean.class).single();
        assertThat(activo).isTrue();
    }

    @Test
    @DisplayName("el historial guarda la fecha anterior y la nueva")
    void historialConAmbasFechas() {
        prepararCalendario();
        acciones.declararNoCumplido(pendiente, version(), null, jefa);

        var entradas = historial.deEntidad("PENDING_TASK", pendiente, Paging.of(0));

        assertThat(entradas).hasSize(1);
        assertThat(entradas.get(0).action()).isEqualTo("NOT_COMPLETED");
        assertThat(entradas.get(0).beforeValues()).contains("2026-09-04");
        assertThat(entradas.get(0).afterValues()).contains("2026-09-07");
    }

    @Test
    @DisplayName("sin cobertura de calendario NO se elige fecha: se avisa")
    void sinCalendarioNoReprograma() {
        // Sin preparar el calendario: no hay revision confirmada.
        var problema = acciones.declararNoCumplido(pendiente, version(), null, jefa);

        assertThat(problema).get().asString().contains("calendario");
        assertThat(programada()).as("la fecha no se modifico").isEqualTo(VIERNES);
        assertThat(historial.deEntidad("PENDING_TASK", pendiente, Paging.of(0)))
                .as("tampoco se registro nada").isEmpty();
    }
}
