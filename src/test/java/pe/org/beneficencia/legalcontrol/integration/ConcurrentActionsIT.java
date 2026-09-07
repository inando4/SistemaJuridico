package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
 * Dos personas actuando a la vez sobre el mismo pendiente.
 *
 * <p>Es el escenario real de un area de cinco personas donde la jefa puede tocar
 * cualquier registro: alguien lo cumple mientras otro lo reprograma. Solo una de
 * las dos acciones puede prosperar, y el estado resultante tiene que ser coherente.
 */
class ConcurrentActionsIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private PendingTaskActionService acciones;
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
                VALUES (:id, :owner, 'Tarea disputada', :hoy, :hoy, true, :ahora, :ahora, 1)
                """).param("id", pendiente).param("owner", id)
                .param("hoy", LocalDate.now()).param("ahora", ahora).update();
    }

    private long version() {
        return jdbc.sql("SELECT version FROM pending_task WHERE id = :id")
                .param("id", pendiente).query(Long.class).single();
    }

    private boolean cumplido() {
        return jdbc.sql("SELECT completed_at FROM pending_task WHERE id = :id")
                .param("id", pendiente).query().singleRow().get("completed_at") != null;
    }

    private boolean intentar(Runnable accion) {
        try {
            accion.run();
            return true;
        } catch (Exception rechazada) {
            return false;
        }
    }

    @Test
    @DisplayName("cumplir y reprogramar a la vez: solo prospera una")
    void cumplirYReprogramarALaVez() throws Exception {
        long version = version();

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> tareas = List.of(
                    () -> intentar(() -> acciones.marcarCumplido(pendiente, version, jefa)),
                    () -> intentar(() -> acciones.reprogramar(
                            pendiente, version, LocalDate.now().plusDays(5), null, jefa)));

            long exitos = pool.invokeAll(tareas).stream().filter(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    return false;
                }
            }).count();

            assertThat(exitos).as("ambas partieron de la misma version: solo una puede valer")
                    .isEqualTo(1);
        }

        // Sea cual sea la que gano, el estado debe ser coherente.
        assertThat(historial.deEntidad("PENDING_TASK", pendiente, Paging.of(0)))
                .as("una accion, una entrada de historial").hasSize(1);
    }

    @Test
    @DisplayName("dos cumplidos simultaneos no duplican la entrada de historial")
    void dosCumplidosSimultaneos() throws Exception {
        long version = version();

        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<Callable<Boolean>> tareas = java.util.stream.IntStream.range(0, 4)
                    .<Callable<Boolean>>mapToObj(i -> () ->
                            intentar(() -> acciones.marcarCumplido(pendiente, version, jefa)))
                    .toList();
            pool.invokeAll(tareas);
        }

        assertThat(cumplido()).isTrue();
        assertThat(historial.deEntidad("PENDING_TASK", pendiente, Paging.of(0)))
                .as("cuatro intentos, un solo cumplimiento registrado").hasSize(1);
    }

    @Test
    @DisplayName("cumplir y revertir a la vez deja un estado coherente")
    void cumplirYRevertirALaVez() throws Exception {
        acciones.marcarCumplido(pendiente, version(), jefa);
        long version = version();

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> tareas = List.of(
                    () -> intentar(() -> acciones.revertirCumplimiento(
                            pendiente, version, "Primera reversion", jefa)),
                    () -> intentar(() -> acciones.revertirCumplimiento(
                            pendiente, version, "Segunda reversion", jefa)));

            long exitos = pool.invokeAll(tareas).stream().filter(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    return false;
                }
            }).count();

            assertThat(exitos).as("revertir dos veces lo mismo no puede prosperar dos veces")
                    .isEqualTo(1);
        }

        assertThat(cumplido()).isFalse();
        assertThat(historial.deEntidad("PENDING_TASK", pendiente, Paging.of(0)))
                .as("el cumplimiento y una sola reversion").hasSize(2);
    }

    @Test
    @DisplayName("la version avanza con cada accion, de una en una")
    void versionAvanzaDeUnaEnUna() {
        long inicial = version();

        acciones.reprogramar(pendiente, version(), LocalDate.now().plusDays(2), null, jefa);
        assertThat(version()).isEqualTo(inicial + 1);

        acciones.marcarCumplido(pendiente, version(), jefa);
        assertThat(version()).isEqualTo(inicial + 2);
    }
}
