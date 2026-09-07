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
 * Coste de servidor del listado administrativo con 5.000 procedimientos.
 *
 * <p><b>No es la medicion de SC-001.</b> Falta el equipo de referencia y la red
 * limitada; aqui no hay navegador. Lo que se mide es la parte que el codigo
 * controla: si ya se tarda aqui, en el equipo real no hay arreglo posible.
 */
@AutoConfigureMockMvc
class ProcedurePerformanceTest extends PostgresIntegrationTest {

    private static final int PROCEDIMIENTOS = 5_000;
    private static final long PRESUPUESTO_SERVIDOR_MS = 300;
    private static final int REPETICIONES = 20;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private static boolean sembrado;
    private MockHttpSession sesion;
    private UUID procedimiento;

    @BeforeEach
    void preparar() throws Exception {
        if (!sembrado || sinDatos()) {
            SesionDePrueba.limpiar(jdbc);
            UUID usuario = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
            int ano = java.time.LocalDate.now().getYear();
            DatosSinteticos.sembrarCalendario(jdbc, usuario, ano, ano + 1, ano + 2);
            DatosSinteticos.sembrarAdministrativos(jdbc, usuario, PROCEDIMIENTOS, 20);
            sembrado = true;
        }
        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
        procedimiento = jdbc.sql("SELECT id FROM administrative_procedure LIMIT 1")
                .query(UUID.class).single();
    }

    private boolean sinDatos() {
        Integer total = jdbc.sql("SELECT count(*) FROM administrative_procedure")
                .query(Integer.class).single();
        return total == null || total < PROCEDIMIENTOS;
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
    @DisplayName("el listado se resuelve muy por debajo del presupuesto")
    void listadoRapido() throws Exception {
        long p95 = p95("/administrativos");
        System.out.printf("Listado administrativo con %d registros: p95 = %d ms%n",
                PROCEDIMIENTOS, p95);
        assertThat(p95).isLessThanOrEqualTo(PRESUPUESTO_SERVIDOR_MS);
    }

    @Test
    @DisplayName("una pagina lejana cuesta lo mismo que la primera")
    void paginacionEstable() throws Exception {
        long primera = p95("/administrativos");
        long lejana = p95("/administrativos?page=150");
        System.out.printf("Pagina 1: %d ms · Pagina 150: %d ms%n", primera, lejana);
        assertThat(lejana).isLessThanOrEqualTo(PRESUPUESTO_SERVIDOR_MS);
    }

    @Test
    @DisplayName("un listado filtrado y ordenado sigue dentro del presupuesto")
    void filtradoRapido() throws Exception {
        long p95 = p95("/administrativos?q=sintetico&sort=deadline&direction=desc&visibility=all");
        System.out.printf("Listado filtrado: p95 = %d ms%n", p95);
        assertThat(p95).isLessThanOrEqualTo(PRESUPUESTO_SERVIDOR_MS);
    }

    @Test
    @DisplayName("la ficha se resuelve muy por debajo del presupuesto")
    void fichaRapida() throws Exception {
        long p95 = p95("/administrativos/" + procedimiento);
        System.out.printf("Ficha administrativa: p95 = %d ms%n", p95);
        assertThat(p95).isLessThanOrEqualTo(PRESUPUESTO_SERVIDOR_MS);
    }
}
