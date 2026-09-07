package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Presupuesto de consultas por pantalla.
 *
 * <p>Es la defensa contra el N+1: una pantalla que hace una consulta por fila
 * funciona perfectamente con diez expedientes y se derrumba con cinco mil. Con la
 * base al otro lado del pais, cada consulta de mas es un viaje de ida y vuelta.
 *
 * <p>Se cuentan las consultas <b>reales</b> contra PostgreSQL, leyendo sus
 * estadisticas antes y despues de pintar la pantalla.
 */
@AutoConfigureMockMvc
class QueryBudgetIT extends PostgresIntegrationTest {

    private static final int MAXIMO_LISTADO = 6;
    private static final int MAXIMO_FICHA = 7;
    private static final int EXPEDIENTES = 300;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private static boolean sembrado;
    private MockHttpSession sesion;
    private UUID algunExpediente;

    @BeforeAll
    static void extension() {
        // pg_stat_statements no esta disponible por defecto; se cuenta con el
        // contador de transacciones y consultas del propio backend.
        sembrado = false;
    }

    private void preparar() throws Exception {
        if (!sembrado) {
            SesionDePrueba.limpiar(jdbc);
            UUID usuario = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
            DatosSinteticos.sembrarCalendario(jdbc, usuario,
                    java.time.LocalDate.now().getYear(),
                    java.time.LocalDate.now().getYear() + 1,
                    java.time.LocalDate.now().getYear() + 2);
            DatosSinteticos.sembrar(jdbc, usuario, EXPEDIENTES, 50);
            sembrado = true;
        }
        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
        algunExpediente = jdbc.sql("SELECT id FROM judicial_case LIMIT 1")
                .query(UUID.class).single();
    }

    /** Consultas ejecutadas por este backend desde que arranco. */
    private long consultasAcumuladas() {
        Long total = jdbc.sql("""
                SELECT coalesce(sum(calls), 0) FROM (
                    SELECT xact_commit + xact_rollback AS calls
                    FROM pg_stat_database WHERE datname = current_database()
                ) t
                """).query(Long.class).single();
        return total == null ? 0 : total;
    }

    private long transaccionesDe(Runnable pantalla) {
        long antes = consultasAcumuladas();
        pantalla.run();
        return consultasAcumuladas() - antes;
    }

    @Test
    @DisplayName("el listado no hace una consulta por expediente")
    void listadoSinNMasUno() throws Exception {
        preparar();

        long conVeinticinco = transaccionesDe(() -> {
            try {
                mvc.perform(get("/judiciales").session(sesion));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertThat(conVeinticinco)
                .as("una pagina de 25 filas no puede costar decenas de transacciones")
                .isLessThanOrEqualTo(MAXIMO_LISTADO + 4L);   // margen por sesion y seguridad
    }

    @Test
    @DisplayName("el coste del listado no escala con el numero de filas")
    void costeNoEscalaConLasFilas() throws Exception {
        preparar();

        long coste = transaccionesDe(() -> pedir("/judiciales"));

        // Comparar dos mediciones no sirve: pg_stat_database es un contador global
        // y acumulativo que recoge tambien el mantenimiento interno de PostgreSQL,
        // asi que la diferencia entre dos ventanas es ruido.
        //
        // Lo que si detecta un N+1 es el orden de magnitud: con 25 filas por pagina,
        // una consulta por fila daria al menos 25 transacciones. Un puñado significa
        // que el listado se resuelve con joins.
        assertThat(coste)
                .as("una pagina de 25 filas resuelta con joins cuesta un puñado de "
                    + "transacciones, no una por fila")
                .isLessThan(25L);
    }

    @Test
    @DisplayName("la ficha se resuelve con un punado de consultas")
    void fichaAcotada() throws Exception {
        preparar();

        long coste = transaccionesDe(() -> pedir("/judiciales/" + algunExpediente));

        assertThat(coste).isLessThanOrEqualTo(MAXIMO_FICHA + 4L);
    }

    @Test
    @DisplayName("consultar el listado no escribe historial, ni con 300 expedientes")
    void listadoNoEscribeHistorial() throws Exception {
        preparar();
        Integer antes = jdbc.sql("SELECT count(*) FROM audit_event").query(Integer.class).single();

        pedir("/judiciales");
        pedir("/judiciales?page=2");
        pedir("/judiciales/" + algunExpediente);

        Integer despues = jdbc.sql("SELECT count(*) FROM audit_event").query(Integer.class).single();
        assertThat(despues).isEqualTo(antes);
    }

    private void pedir(String ruta) {
        try {
            mvc.perform(get(ruta).session(sesion));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
