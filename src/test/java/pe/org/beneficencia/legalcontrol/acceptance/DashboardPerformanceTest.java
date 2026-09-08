package pe.org.beneficencia.legalcontrol.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

import pe.org.beneficencia.legalcontrol.integration.DatosSinteticos;
import pe.org.beneficencia.legalcontrol.integration.PostgresIntegrationTest;
import pe.org.beneficencia.legalcontrol.integration.SesionDePrueba;

/**
 * Coste de servidor del panel y las alertas con 5.000 pendientes (principio IV).
 *
 * <p>Mide <b>lo que el servidor tarda</b>, no lo que ve el navegador: el equipo de
 * quien ejecuta la prueba no es el equipo de referencia del area, y una cifra que
 * dependa de la maquina no sirve de presupuesto. Lo que se comprueba es que la
 * consulta y la plantilla no son el cuello de botella.
 */
@AutoConfigureMockMvc
class DashboardPerformanceTest extends PostgresIntegrationTest {

    private static final int PENDIENTES = 5_000;
    private static final int MEDICIONES = 20;

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

    /** @return percentil 95 en milisegundos, tras un par de vueltas de calentamiento */
    private long p95De(String ruta) throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get(ruta).session(sesion));
        }
        List<Long> tiempos = new ArrayList<>();
        for (int i = 0; i < MEDICIONES; i++) {
            long inicio = System.nanoTime();
            mvc.perform(get(ruta).session(sesion));
            tiempos.add(Duration.ofNanos(System.nanoTime() - inicio).toMillis());
        }
        Collections.sort(tiempos);
        return tiempos.get((int) Math.ceil(MEDICIONES * 0.95) - 1);
    }

    @Test
    @DisplayName("el panel del dia abre en menos de 300 ms con 5.000 pendientes")
    void panelDentroDelPresupuesto() throws Exception {
        long p95 = p95De("/");
        System.out.printf("Panel del dia con %d pendientes: p95 %d ms%n", PENDIENTES, p95);

        assertThat(p95)
                .as("es la pantalla que mas veces se abre al dia")
                .isLessThan(300L);
    }

    @Test
    @DisplayName("las alertas abren en menos de 400 ms con 5.000 pendientes")
    void alertasDentroDelPresupuesto() throws Exception {
        long p95 = p95De("/alertas");
        System.out.printf("Alertas con %d pendientes: p95 %d ms%n", PENDIENTES, p95);

        assertThat(p95).isLessThan(400L);
    }

    @Test
    @DisplayName("el panel no se degrada al crecer el volumen")
    void panelEstableConMasFilas() throws Exception {
        long conTodos = p95De("/");

        // La mitad de las filas: si el coste dependiera del volumen, se notaria.
        jdbc.sql("DELETE FROM pending_task WHERE id IN "
                + "(SELECT id FROM pending_task LIMIT :cuantos)")
                .param("cuantos", PENDIENTES / 2).update();

        long conLaMitad = p95De("/");
        System.out.printf("Panel: p95 %d ms con %d filas, %d ms con %d%n",
                conTodos, PENDIENTES, conLaMitad, PENDIENTES / 2);

        // No se exige que sean iguales —hay ruido de medicion— sino que quitar la
        // mitad de las filas no cambie el orden de magnitud.
        assertThat(conTodos)
                .as("el coste lo domina la agregacion, no el numero de filas")
                .isLessThan(Math.max(conLaMitad * 4, 300L));
    }
}
