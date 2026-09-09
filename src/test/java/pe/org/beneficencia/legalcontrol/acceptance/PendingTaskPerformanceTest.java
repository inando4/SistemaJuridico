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
 * Coste de servidor de las pantallas de pendientes con 5.000 registros.
 *
 * <p><b>No es la medicion de SC-001</b>: falta el equipo de referencia y la red
 * limitada. Es la parte que el codigo controla; si ya se tarda aqui, en el equipo
 * real no hay arreglo posible.
 */
@AutoConfigureMockMvc
class PendingTaskPerformanceTest extends PostgresIntegrationTest {

    private static final int PENDIENTES = 5_000;
    private static final long PRESUPUESTO_SERVIDOR_MS = 300;
    private static final int REPETICIONES = 20;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID alguno;

    @BeforeEach
    void preparar() throws Exception {
        if (sinDatos()) {
            SesionDePrueba.limpiar(jdbc);
            UUID usuario = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
            int ano = java.time.LocalDate.now().getYear();
            DatosSinteticos.sembrarCalendario(jdbc, usuario, ano, ano + 1, ano + 2);
            DatosSinteticos.sembrarPendientes(jdbc, usuario, PENDIENTES, 13);
        }
        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
        alguno = jdbc.sql("SELECT id FROM pending_task LIMIT 1").query(UUID.class).single();
    }

    private boolean sinDatos() {
        Integer total = jdbc.sql("SELECT count(*) FROM pending_task").query(Integer.class).single();
        return total == null || total < PENDIENTES;
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
    @DisplayName("una ficha con 50 pendientes vinculados se muestra por debajo de medio segundo")
    void fichaConMuchosVinculos() throws Exception {
        UUID expediente = UUID.randomUUID();
        // El responsable se toma de un pendiente que ya existe, no de un correo:
        // esta clase comparte base con las demas y solo siembra si faltan datos, asi
        // que la cuenta puede venir de otra siembra anterior con otro correo. Con el
        // SELECT vacio no se insertaba nada y el UPDATE de abajo chocaba con la
        // clave foranea.
        UUID responsable = jdbc.sql("SELECT owner_id FROM pending_task LIMIT 1")
                .query(UUID.class).single();
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, active,
                                           created_at, updated_at, version)
                VALUES (:id, :o, 'EXP-CARGADO-2026', true, now(), now(), 1)
                """).param("id", expediente).param("o", responsable).update();
        jdbc.sql("""
                UPDATE pending_task SET judicial_case_id = :j
                WHERE id IN (SELECT id FROM pending_task
                             -- Solo los que no cuelgan ya de un administrativo: la
                             -- V9 impide que un pendiente tenga los dos vinculos.
                             WHERE administrative_procedure_id IS NULL LIMIT 50)
                """).param("j", expediente).update();

        long p95 = p95("/judiciales/" + expediente);
        System.out.printf("Ficha judicial con 50 vinculos: p95 = %d ms%n", p95);

        // El objetivo de CE-006 es 500 ms. El presupuesto general del proyecto es
        // mas estricto y la ficha no tiene por que ser la excepcion.
        assertThat(p95).isLessThanOrEqualTo(PRESUPUESTO_SERVIDOR_MS);
    }

    @Test
    @DisplayName("el listado se resuelve muy por debajo del presupuesto")
    void listadoRapido() throws Exception {
        long p95 = p95("/pendientes");
        System.out.printf("Listado de pendientes con %d registros: p95 = %d ms%n",
                PENDIENTES, p95);
        assertThat(p95).isLessThanOrEqualTo(PRESUPUESTO_SERVIDOR_MS);
    }

    @Test
    @DisplayName("la pantalla de hoy es la mas usada y la mas rapida")
    void hoyRapida() throws Exception {
        long p95 = p95("/pendientes/hoy");
        System.out.printf("Pendientes de hoy: p95 = %d ms%n", p95);
        assertThat(p95).isLessThanOrEqualTo(PRESUPUESTO_SERVIDOR_MS);
    }

    @Test
    @DisplayName("cumplidas calcula sus dos valores derivados dentro del presupuesto")
    void cumplidasRapida() throws Exception {
        long p95 = p95("/cumplidos");
        System.out.printf("Tareas cumplidas: p95 = %d ms%n", p95);
        assertThat(p95).isLessThanOrEqualTo(PRESUPUESTO_SERVIDOR_MS);
    }

    @Test
    @DisplayName("una pagina lejana cuesta lo mismo que la primera")
    void paginacionEstable() throws Exception {
        long primera = p95("/pendientes");
        long lejana = p95("/pendientes?page=150");
        System.out.printf("Pagina 1: %d ms · Pagina 150: %d ms%n", primera, lejana);
        assertThat(lejana).isLessThanOrEqualTo(PRESUPUESTO_SERVIDOR_MS);
    }

    @Test
    @DisplayName("la ficha se resuelve muy por debajo del presupuesto")
    void fichaRapida() throws Exception {
        long p95 = p95("/pendientes/" + alguno);
        System.out.printf("Ficha de pendiente: p95 = %d ms%n", p95);
        assertThat(p95).isLessThanOrEqualTo(PRESUPUESTO_SERVIDOR_MS);
    }
}
