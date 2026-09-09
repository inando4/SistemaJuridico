package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.sql.Timestamp;
import java.time.Instant;
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

/**
 * Un valor de filtro que deja de ofrecerse no puede llevarse por delante los registros
 * que ya lo tenian (RF-021).
 *
 * <p>Son dos casos con la misma forma: un catalogo deshabilitado y una cuenta
 * desactivada. En los dos, lo que ya existe sigue existiendo.
 */
@AutoConfigureMockMvc
class FiltrosConCatalogoDeshabilitadoIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID laQueSalio;
    private UUID estadoRetirado;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        UUID yo = SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");
        laQueSalio = SesionDePrueba.crearCuenta(jdbc, encoder, "salio@ejemplo.test", "LAWYER");
        jdbc.sql("UPDATE app_user SET name = 'Abogada Que Salio' WHERE id = :id")
                .param("id", laQueSalio).update();
        sesion = SesionDePrueba.entrar(mvc, "yo@ejemplo.test");

        Timestamp ahora = Timestamp.from(Instant.now());
        estadoRetirado = DatosSinteticos
                .sembrarCatalogo(jdbc, "procedural_status", "Estado", 1, yo, ahora).get(0);

        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, procedural_status_id,
                                           active, created_at, updated_at, version)
                VALUES (:id, :o, 'EXP-HUERFANO-2026', :e, true, :a, :a, 1)
                """)
                .param("id", UUID.randomUUID()).param("o", laQueSalio)
                .param("e", estadoRetirado).param("a", ahora).update();
    }

    private String pantalla(String ruta) throws Exception {
        return mvc.perform(get(ruta).session(sesion))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("un catalogo deshabilitado sale del desplegable pero no de los registros")
    void catalogoDeshabilitado() throws Exception {
        jdbc.sql("UPDATE procedural_status SET enabled = false WHERE id = :id")
                .param("id", estadoRetirado).update();

        String html = pantalla("/judiciales");

        assertThat(html)
                .as("el expediente sigue en el listado con su estado")
                .contains("EXP-HUERFANO-2026")
                .contains("Estado 1");

        // Deshabilitado deja de ofrecerse como opcion nueva, pero filtrar por el sigue
        // funcionando: es la unica forma de encontrar lo que ya lo tiene.
        assertThat(pantalla("/judiciales?proceduralStatusId=" + estadoRetirado))
                .contains("EXP-HUERFANO-2026");
    }

    @Test
    @DisplayName("una cuenta desactivada sigue en el desplegable y su trabajo se encuentra")
    void cuentaDesactivada() throws Exception {
        jdbc.sql("UPDATE app_user SET status = 'INACTIVE' WHERE id = :id")
                .param("id", laQueSalio).update();

        String html = pantalla("/judiciales");

        assertThat(html)
                .as("sin su nombre aqui, su trabajo seria inencontrable y pareceria de nadie")
                .contains("Abogada Que Salio (desactivada)");

        assertThat(pantalla("/judiciales?ownerId=" + laQueSalio))
                .contains("EXP-HUERFANO-2026");
    }

    @Test
    @DisplayName("un identificador de filtro inexistente da vacio, no un error")
    void identificadorInexistente() throws Exception {
        assertThat(pantalla("/judiciales?ownerId=00000000-0000-0000-0000-000000000000"))
                .doesNotContain("EXP-HUERFANO-2026");
    }
}
