package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

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
 * La configuracion no consulta nada (principio IV).
 *
 * <p>Es el unico presupuesto del sistema expresado como un <b>cero</b>. Los enlaces son
 * fijos y no dependen de ningun dato: leer los catalogos para pintarlos seria pagar
 * viajes a la base por una lista que esta escrita en la plantilla.
 *
 * <p>Existe porque es justo el tipo de cosa que se añade sin pensar —«ya que estamos,
 * enseñemos cuantos tipos hay»— y que nadie nota hasta que la pantalla mas simple del
 * sistema cuesta cinco viajes.
 */
@AutoConfigureMockMvc
@Import(ContadorDeConsultas.class)
class ConfiguracionQueryBudgetIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;

    @BeforeEach
    void preparar() throws Exception {
        if (jdbc.sql("SELECT count(*) FROM app_user WHERE email = 'jefa@ejemplo.test'")
                .query(Integer.class).single() == 0) {
            SesionDePrueba.limpiar(jdbc);
            SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        }
        sesion = SesionDePrueba.entrar(mvc, "jefa@ejemplo.test");
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
    @DisplayName("la configuracion no gasta ninguna consulta propia")
    void ningunaConsultaPropia() {
        long coste = costeDe("/configuracion");
        System.out.printf("Configuracion: %d consultas%n", coste);

        assertThat(coste)
                .as("los enlaces estan en la plantilla; no hay nada que leer de la base")
                .isLessThanOrEqualTo(2L);
    }

    @Test
    @DisplayName("cuesta lo mismo con el sistema lleno de datos que vacio")
    void invarianteFrenteAlVolumen() {
        long conPocosDatos = costeDe("/configuracion");

        DatosSinteticos.sembrarCatalogo(jdbc, "pending_task_type",
                "Tipo " + java.util.UUID.randomUUID(), 40,
                jdbc.sql("SELECT id FROM app_user WHERE email = 'jefa@ejemplo.test'")
                        .query(java.util.UUID.class).single(),
                java.sql.Timestamp.from(java.time.Instant.now()));

        long conMuchos = costeDe("/configuracion");

        assertThat(conMuchos)
                .as("cuarenta tipos de pendiente no pueden cambiar el coste de esta pantalla")
                .isEqualTo(conPocosDatos);
    }
}
