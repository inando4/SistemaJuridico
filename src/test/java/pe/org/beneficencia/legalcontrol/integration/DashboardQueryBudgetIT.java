package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Presupuesto de consultas del panel y las alertas (principio IV).
 *
 * <p>Seis tarjetas no pueden ser seis consultas, ni cinco niveles cinco. Con la
 * base en otra region cada viaje cuesta, y la pantalla de entrada es la que mas
 * veces se abre al dia.
 */
@AutoConfigureMockMvc
@Import(ContadorDeConsultas.class)
class DashboardQueryBudgetIT extends PostgresIntegrationTest {

    private static final int PENDIENTES = 500;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;

    @BeforeEach
    void preparar() throws Exception {
        if (sinDatos()) {
            SesionDePrueba.limpiar(jdbc);
            UUID usuario = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test",
                    "LAWYER");
            int ano = LocalDate.now().getYear();
            DatosSinteticos.sembrarCalendario(jdbc, usuario, ano - 1, ano, ano + 1);
            DatosSinteticos.sembrarPendientes(jdbc, usuario, PENDIENTES, 10);
        }
        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
    }

    private boolean sinDatos() {
        Integer total = jdbc.sql("SELECT count(*) FROM pending_task").query(Integer.class).single();
        return total == null || total < PENDIENTES;
    }

    private long costeDe(String ruta) {
        return ContadorDeConsultas.contar(() -> {
            try {
                mvc.perform(get(ruta).session(sesion));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("el panel resuelve las seis tarjetas sin una consulta por tarjeta")
    void panelEnUnaConsulta() {
        long coste = costeDe("/");
        System.out.printf("Panel del dia: %d consultas%n", coste);

        assertThat(coste)
                .as("seis tarjetas en una agregacion; una por tarjeta serian seis viajes")
                .isLessThanOrEqualTo(4L);
    }

    @Test
    @DisplayName("las alertas clasifican los cinco niveles en una sola consulta")
    void alertasEnUnaConsulta() {
        long coste = costeDe("/alertas");
        System.out.printf("Alertas: %d consultas%n", coste);

        assertThat(coste)
                .as("un CASE, no cinco selects unidos")
                .isLessThanOrEqualTo(5L);
    }

    @Test
    @DisplayName("el coste del panel no crece con el numero de pendientes")
    void panelNoEscalaConLasFilas() {
        long conQuinientos = costeDe("/");

        // Se duplican las filas reutilizando los catalogos que ya existen: volver a
        // llamar al sembrador chocaria con la unicidad de sus nombres.
        jdbc.sql("""
                INSERT INTO pending_task
                    (id, owner_id, title, description, pending_task_type_id, priority_id,
                     pending_task_status_id, received_at, registered_at, scheduled_for,
                     deadline, notes, active, created_at, updated_at, version)
                SELECT gen_random_uuid(), owner_id, title || ' (copia)', description,
                       pending_task_type_id, priority_id, pending_task_status_id,
                       received_at, registered_at, scheduled_for, deadline, notes,
                       active, created_at, updated_at, 1
                FROM pending_task
                """).update();

        long conMil = costeDe("/");
        System.out.printf("Panel: %d consultas con 500, %d con 1000%n", conQuinientos, conMil);

        assertThat(conMil)
                .as("duplicar las filas no puede anadir ni una consulta")
                .isEqualTo(conQuinientos);
    }

    @Test
    @DisplayName("abrir el panel y las alertas no escribe historial")
    void consultarNoEscribe() {
        Integer antes = jdbc.sql("SELECT count(*) FROM audit_event").query(Integer.class).single();

        for (String ruta : List.of("/", "/alertas", "/", "/alertas", "/")) {
            costeDe(ruta);
        }

        Integer despues = jdbc.sql("SELECT count(*) FROM audit_event")
                .query(Integer.class).single();
        assertThat(despues)
                .as("consultar es leer: el historial es de lo que se cambia (FR-019)")
                .isEqualTo(antes);
    }

    @Test
    @DisplayName("no se persiste ningun valor derivado del panel")
    void nadaDerivadoSePersiste() {
        List<String> columnas = jdbc.sql("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'pending_task'
                """).query(String.class).list();

        assertThat(columnas)
                .as("las cifras se calculan al abrir la pantalla (principio V)")
                .doesNotContain("nivel_alerta", "es_urgente", "dias_restantes", "antiguedad",
                        "urgentes_hoy", "vencidos");

        List<String> tablas = jdbc.sql("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = current_schema()
                """).query(String.class).list();

        assertThat(tablas)
                .as("una alerta no es un registro: es una lectura del estado actual")
                .doesNotContain("alert", "alerta", "alertas", "dashboard_summary");
    }
}
