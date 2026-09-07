package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

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
 * Presupuesto de consultas del listado administrativo.
 *
 * <p>Defensa contra el N+1: una pantalla que consulta por fila funciona con diez
 * registros y se derrumba con cinco mil. Con la base en otra region, cada consulta
 * de mas es un viaje de ida y vuelta.
 *
 * <p>Se cuentan transacciones reales contra PostgreSQL. El contador es global y
 * acumulativo, asi que sirve para el orden de magnitud —detectar una consulta por
 * fila— pero no para comparar dos ventanas entre si.
 */
@AutoConfigureMockMvc
@Import(ContadorDeConsultas.class)
class ProcedureQueryBudgetIT extends PostgresIntegrationTest {

    private static final int PROCEDIMIENTOS = 300;
    private static final int FILAS_POR_PAGINA = 25;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private static boolean sembrado;
    private MockHttpSession sesion;
    private UUID algunProcedimiento;

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
        algunProcedimiento = jdbc.sql("SELECT id FROM administrative_procedure LIMIT 1")
                .query(UUID.class).single();
    }

    /** Otra clase pudo vaciar las tablas: la bandera estatica sola no basta. */
    private boolean sinDatos() {
        Integer total = jdbc.sql("SELECT count(*) FROM administrative_procedure")
                .query(Integer.class).single();
        return total == null || total == 0;
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
    @DisplayName("el listado no hace una consulta por procedimiento")
    void listadoSinNMasUno() {
        long coste = costeDe("/administrativos");

        System.out.printf("Listado administrativo: %d consultas%n", coste);
        assertThat(coste)
                .as("una consulta por fila daria al menos %d", FILAS_POR_PAGINA)
                .isLessThanOrEqualTo(10L);
    }

    @Test
    @DisplayName("una pagina lejana tampoco escala con las filas")
    void paginaLejanaAcotada() {
        assertThat(costeDe("/administrativos?page=8")).isLessThanOrEqualTo(10L);
    }

    @Test
    @DisplayName("el listado filtrado y ordenado sigue acotado")
    void filtradoAcotado() {
        assertThat(costeDe("/administrativos?q=sintetico&sort=deadline&visibility=all"))
                .isLessThanOrEqualTo(10L);
    }

    @Test
    @DisplayName("la ficha se resuelve con un punado de consultas")
    void fichaAcotada() {
        assertThat(costeDe("/administrativos/" + algunProcedimiento)).isLessThanOrEqualTo(11L);
    }

    @Test
    @DisplayName("consultar no escribe historial, ni con 300 procedimientos")
    void consultarNoEscribeHistorial() {
        Integer antes = jdbc.sql("SELECT count(*) FROM audit_event").query(Integer.class).single();

        costeDe("/administrativos");
        costeDe("/administrativos?page=3");
        costeDe("/administrativos/" + algunProcedimiento);
        costeDe("/administrativos/" + algunProcedimiento + "/historial");

        Integer despues = jdbc.sql("SELECT count(*) FROM audit_event").query(Integer.class).single();
        assertThat(despues).isEqualTo(antes);
    }
}
