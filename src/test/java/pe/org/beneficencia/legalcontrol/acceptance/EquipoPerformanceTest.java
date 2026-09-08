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
 * Coste de la vista de equipo con 5.000 pendientes (principio IV).
 *
 * <p>Mismo volumen que midio la 004 para poder comparar. Se mide el tiempo de
 * servidor, no el del navegador: el equipo de quien ejecuta la prueba no es el de
 * referencia del area, y una cifra que dependa de la maquina no sirve de
 * presupuesto. Lo que se comprueba es que la consulta agrupada y la plantilla no
 * son el cuello de botella.
 */
@AutoConfigureMockMvc
class EquipoPerformanceTest extends PostgresIntegrationTest {

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
            UUID jefa = SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
            int ano = LocalDate.now().getYear();
            DatosSinteticos.sembrarCalendario(jdbc, jefa, ano - 1, ano, ano + 1);

            // Cuatro abogados mas la jefa: el tamaño real del area.
            List<UUID> equipo = new ArrayList<>(List.of(jefa));
            for (int i = 1; i <= 4; i++) {
                equipo.add(SesionDePrueba.crearCuenta(jdbc, encoder,
                        "abogado" + i + "@ejemplo.test", "LAWYER"));
            }
            // Se siembra una vez y se reparte despues: sembrarPendientes crea sus
            // catalogos con nombres fijos, y llamarlo cinco veces choca con la
            // restriccion de nombre unico.
            DatosSinteticos.sembrarPendientes(jdbc, jefa, PENDIENTES, 10);
            int porPersona = PENDIENTES / equipo.size();
            for (int i = 0; i < equipo.size(); i++) {
                jdbc.sql("""
                        UPDATE pending_task SET owner_id = :quien
                        WHERE id IN (SELECT id FROM pending_task
                                     ORDER BY id OFFSET :salto LIMIT :cuantos)
                        """)
                        .param("quien", equipo.get(i))
                        .param("salto", i * porPersona)
                        .param("cuantos", porPersona)
                        .update();
            }
        }
        sesion = SesionDePrueba.entrar(mvc, "jefa@ejemplo.test");
    }

    private boolean sinDatos() {
        Integer total = jdbc.sql("SELECT count(*) FROM pending_task").query(Integer.class).single();
        return total == null || total < PENDIENTES - 10;
    }

    /** @return percentil 95 en milisegundos, tras unas vueltas de calentamiento */
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
    @DisplayName("la vista de equipo abre en menos de 400 ms con 5.000 pendientes")
    void dentroDelPresupuesto() throws Exception {
        long p95 = p95De("/equipo");
        System.out.printf("Vista de equipo con %d pendientes y 5 personas: p95 %d ms%n",
                PENDIENTES, p95);

        assertThat(p95)
                .as("una sola consulta agrupada sobre 5.000 filas no puede tardar mas")
                .isLessThan(400L);
    }
}
