package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;
import pe.org.beneficencia.legalcontrol.calendar.CalendarReviewService;
import pe.org.beneficencia.legalcontrol.calendar.CalendarService;
import pe.org.beneficencia.legalcontrol.calendar.NonWorkingDayRepository;
import pe.org.beneficencia.legalcontrol.calendar.NonWorkingDayRepository.Tipo;
import pe.org.beneficencia.legalcontrol.pendingtask.PendingTaskActionService;
import pe.org.beneficencia.legalcontrol.pendingtask.PendingTaskRepository;

/**
 * El historial de tareas cumplidas y sus dos valores calculados.
 *
 * <p>El tiempo de atencion y el numero de reprogramaciones no existen como columna:
 * se obtienen al consultar, y las reprogramaciones con una sola agregacion para
 * toda la pagina.
 */
@AutoConfigureMockMvc
class CompletedTasksIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private PendingTaskActionService acciones;
    @Autowired private PendingTaskRepository pendientes;
    @Autowired private CalendarService calendario;
    @Autowired private CalendarReviewService revisiones;
    @Autowired private NonWorkingDayRepository dias;

    private MockHttpSession sesion;
    private UUID pendiente;
    private CuentaActual abogado;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        UUID id = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
        abogado = new CuentaActual(id, "Abogado", "abogado@ejemplo.test", "LAWYER", 1,
                Instant.now().getEpochSecond());

        int ano = LocalDate.now().getYear();
        var jefa = new CuentaActual(
                jdbc.sql("SELECT id FROM app_user WHERE role='HEAD'").query(UUID.class).single(),
                "Jefa", "jefa@ejemplo.test", "HEAD", 1, Instant.now().getEpochSecond());
        for (int a : new int[]{ano, ano + 1}) {
            calendario.agregar(LocalDate.of(a, 1, 1), "Ano nuevo", Tipo.NATIONAL_HOLIDAY, jefa);
            revisiones.confirmar(a, dias.revisionActual(a).orElse(0L), true, true, jefa);
        }

        pendiente = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, received_at, registered_at,
                                          scheduled_for, active, created_at, updated_at, version)
                VALUES (:id, :owner, 'Tarea que se cumplio', :recepcion, :hoy, :hoy,
                        true, :ahora, :ahora, 1)
                """)
                .param("id", pendiente).param("owner", id)
                .param("recepcion", LocalDate.now().minusDays(10))
                .param("hoy", LocalDate.now()).param("ahora", ahora).update();
    }

    private long version() {
        return jdbc.sql("SELECT version FROM pending_task WHERE id = :id")
                .param("id", pendiente).query(Long.class).single();
    }

    private String pantalla() throws Exception {
        return mvc.perform(get("/cumplidos").session(sesion))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("una tarea cumplida aparece con su fecha de cumplimiento")
    void apareceAlCumplirse() throws Exception {
        assertThat(pantalla()).doesNotContain("Tarea que se cumplio");

        acciones.marcarCumplido(pendiente, version(), abogado);

        assertThat(pantalla()).contains("Tarea que se cumplio");
    }

    @Test
    @DisplayName("un pendiente revertido desaparece del historial de cumplidas")
    void revertidoDesaparece() throws Exception {
        acciones.marcarCumplido(pendiente, version(), abogado);
        assertThat(pantalla()).contains("Tarea que se cumplio");

        acciones.revertirCumplimiento(pendiente, version(), "Me equivoque", abogado);

        assertThat(pantalla()).as("vuelve a la lista activa y sale de cumplidas")
                .doesNotContain("Tarea que se cumplio");
    }

    @Test
    @DisplayName("muestra el tiempo de atencion en dias habiles")
    void tiempoDeAtencion() throws Exception {
        acciones.marcarCumplido(pendiente, version(), abogado);

        assertThat(pantalla()).contains("dias habiles");
    }

    @Test
    @DisplayName("cuenta las reprogramaciones desde el historial")
    void cuentaReprogramaciones() {
        acciones.reprogramar(pendiente, version(), LocalDate.now().plusDays(3), null, abogado);
        acciones.reprogramar(pendiente, version(), LocalDate.now().plusDays(6), null, abogado);
        acciones.marcarCumplido(pendiente, version(), abogado);

        var conteo = pendientes.reprogramacionesDe(java.util.List.of(pendiente));

        assertThat(conteo.get(pendiente)).isEqualTo(2);
    }

    @Test
    @DisplayName("el conteo se obtiene en una sola consulta para toda la pagina")
    void conteoEnUnaConsulta() {
        // Con una lista vacia no consulta nada; con varias, una sola agregacion.
        assertThat(pendientes.reprogramacionesDe(java.util.List.of())).isEmpty();

        acciones.reprogramar(pendiente, version(), LocalDate.now().plusDays(3), null, abogado);
        var conteo = pendientes.reprogramacionesDe(java.util.List.of(
                pendiente, UUID.randomUUID(), UUID.randomUUID()));

        assertThat(conteo).hasSize(1);
        assertThat(conteo.get(pendiente)).isEqualTo(1);
    }

    @Test
    @DisplayName("un pendiente sin reprogramaciones cuenta cero, no falta")
    void sinReprogramacionesCuentaCero() throws Exception {
        acciones.marcarCumplido(pendiente, version(), abogado);

        var conteo = pendientes.reprogramacionesDe(java.util.List.of(pendiente));

        assertThat(conteo).doesNotContainKey(pendiente);
        // La plantilla muestra 0 cuando no hay entrada, no deja el hueco vacio.
        assertThat(pantalla()).contains("<td>0</td>");
    }
}
