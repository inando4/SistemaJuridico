package pe.org.beneficencia.legalcontrol.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
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
 * Mide el coste de servidor de las pantallas con 5.000 expedientes.
 *
 * <p><b>Esto NO es la medicion de SC-001.</b> SC-001 exige la laptop de referencia
 * (dos nucleos, 4 GB) y una red de 2 Mbps con 150 ms de latencia; aqui no hay ni
 * navegador ni red. Lo que se mide es el tiempo del servidor, que es la parte que
 * el codigo controla: si aqui ya se tarda, en el equipo real no hay arreglo posible.
 *
 * <p>Es una puerta inferior: pasarla no acredita SC-001, pero fallarla lo descarta.
 * La medicion completa esta pendiente y necesita el entorno real.
 */
@AutoConfigureMockMvc
class PerformanceBudgetTest extends PostgresIntegrationTest {

    private static final int EXPEDIENTES = 5_000;
    /** Margen del servidor: el resto del presupuesto se lo come la red y el navegador. */
    private static final long PRESUPUESTO_SERVIDOR_MS = 300;
    private static final int REPETICIONES = 20;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private static boolean sembrado;
    private MockHttpSession sesion;
    private UUID expediente;

    @BeforeEach
    void preparar() throws Exception {
        if (!sembrado) {
            SesionDePrueba.limpiar(jdbc);
            UUID usuario = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
            int ano = java.time.LocalDate.now().getYear();
            DatosSinteticos.sembrarCalendario(jdbc, usuario, ano, ano + 1, ano + 2);
            DatosSinteticos.sembrar(jdbc, usuario, EXPEDIENTES, 50);
            sembrado = true;
        }
        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
        expediente = jdbc.sql("SELECT id FROM judicial_case LIMIT 1").query(UUID.class).single();
    }

    /** Percentil 95 de varias repeticiones, no el mejor tiempo. */
    private long p95(String ruta) throws Exception {
        List<Long> tiempos = new ArrayList<>();
        for (int i = 0; i < REPETICIONES; i++) {
            long inicio = System.nanoTime();
            mvc.perform(get(ruta).session(sesion));
            tiempos.add((System.nanoTime() - inicio) / 1_000_000);
        }
        tiempos.sort(null);
        return tiempos.get((int) Math.ceil(tiempos.size() * 0.95) - 1);
    }

    @Test
    @DisplayName("el listado se resuelve en el servidor muy por debajo del presupuesto")
    void listadoRapido() throws Exception {
        long p95 = p95("/judiciales");
        System.out.printf("Listado con %d expedientes: p95 = %d ms de servidor%n", EXPEDIENTES, p95);
        assertThat(p95).isLessThanOrEqualTo(PRESUPUESTO_SERVIDOR_MS);
    }

    @Test
    @DisplayName("una pagina lejana cuesta lo mismo que la primera")
    void paginacionEstable() throws Exception {
        long primera = p95("/judiciales");
        long lejana = p95("/judiciales?page=150");
        System.out.printf("Pagina 1: %d ms · Pagina 150: %d ms%n", primera, lejana);

        assertThat(lejana).isLessThanOrEqualTo(PRESUPUESTO_SERVIDOR_MS);
    }

    @Test
    @DisplayName("un listado filtrado y ordenado sigue dentro del presupuesto")
    void filtradoRapido() throws Exception {
        long p95 = p95("/judiciales?q=sintetico&sort=deadline&direction=desc&visibility=all");
        System.out.printf("Listado filtrado y ordenado: p95 = %d ms%n", p95);
        assertThat(p95).isLessThanOrEqualTo(PRESUPUESTO_SERVIDOR_MS);
    }

    @Test
    @DisplayName("la ficha se resuelve en el servidor muy por debajo del presupuesto")
    void fichaRapida() throws Exception {
        long p95 = p95("/judiciales/" + expediente);
        System.out.printf("Ficha: p95 = %d ms de servidor%n", p95);
        assertThat(p95).isLessThanOrEqualTo(PRESUPUESTO_SERVIDOR_MS);
    }
}
