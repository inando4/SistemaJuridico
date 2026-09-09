package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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

/**
 * Actuar sobre la ultima fila activa de una pagina la deja vacia.
 *
 * <p>No es un fallo: el listado por defecto muestra el trabajo que queda, y ese ya no
 * queda. Pero es el tipo de comportamiento que, sin estar escrito, se descubre en
 * produccion y se denuncia como error, asi que se fija aqui.
 */
@AutoConfigureMockMvc
class UltimaFilaDeLaPaginaIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID yo;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        yo = SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");
        sesion = SesionDePrueba.entrar(mvc, "yo@ejemplo.test");

        // 26 pendientes: 25 llenan la primera pagina y uno solo queda en la segunda.
        for (int i = 0; i < 26; i++) {
            Timestamp ahora = Timestamp.from(Instant.now());
            jdbc.sql("""
                    INSERT INTO pending_task (id, owner_id, title, registered_at, active,
                                              created_at, updated_at, version)
                    VALUES (:id, :o, :t, :hoy, true, :ts, :ts, 1)
                    """)
                    .param("id", UUID.randomUUID()).param("o", yo)
                    .param("t", String.format("Escrito %02d", i))
                    .param("hoy", LocalDate.now()).param("ts", ahora).update();
        }
    }

    private String pagina(String query) throws Exception {
        return mvc.perform(get("/pendientes" + query).session(sesion))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("cumplir la unica fila de la ultima pagina devuelve a esa pagina, vacia")
    void laPaginaQuedaVacia() throws Exception {
        String segunda = pagina("?sort=title&page=1");
        assertThat(segunda).as("de partida hay una sola fila").contains("Escrito 25");

        UUID elUltimo = jdbc.sql("""
                SELECT id FROM pending_task WHERE title = 'Escrito 25'
                """).query(UUID.class).single();

        mvc.perform(post("/pendientes/" + elUltimo + "/cumplir").with(csrf())
                        .param("version", "1")
                        .param("filtros", "?sort=title&page=1")
                        .session(sesion))
                .andExpect(redirectedUrl("/pendientes?sort=title&page=1"));

        String despues = pagina("?sort=title&page=1");

        assertThat(despues)
                .as("se vuelve a la misma pagina aunque ya no tenga nada")
                .doesNotContain("Escrito 25")
                .as("y no es un error: hay salida a la vista")
                .contains("Quitar los filtros");
        assertThat(despues)
                .as("una pagina fuera de rango no puede ser un 404: se perderian los filtros")
                .doesNotContain("Página no encontrada");
    }

    @Test
    @DisplayName("cancelar tambien deja la pagina vacia sin romper nada")
    void cancelarIgual() throws Exception {
        UUID elUltimo = jdbc.sql("""
                SELECT id FROM pending_task WHERE title = 'Escrito 25'
                """).query(UUID.class).single();

        mvc.perform(post("/pendientes/" + elUltimo + "/cancelar").with(csrf())
                        .param("version", "1")
                        .param("filtros", "?sort=title&page=1")
                        .session(sesion))
                .andExpect(redirectedUrl("/pendientes?sort=title&page=1"));

        assertThat(pagina("?sort=title&page=1")).doesNotContain("Escrito 25");
    }

    @Test
    @DisplayName("lo cumplido sigue alcanzable: no se ha perdido, ha cambiado de sitio")
    void loCumplidoSigueEnSuPantalla() throws Exception {
        UUID elUltimo = jdbc.sql("""
                SELECT id FROM pending_task WHERE title = 'Escrito 25'
                """).query(UUID.class).single();

        mvc.perform(post("/pendientes/" + elUltimo + "/cumplir").with(csrf())
                .param("version", "1").session(sesion));

        assertThat(mvc.perform(get("/cumplidos").session(sesion))
                        .andReturn().getResponse().getContentAsString())
                .contains("Escrito 25");
    }

    @Test
    @DisplayName("las filas de la primera pagina no se mueven por lo que pase en la segunda")
    void laPrimeraPaginaNoSeAltera() throws Exception {
        List<String> antes = titulosDe(pagina("?sort=title&page=0"));

        UUID elUltimo = jdbc.sql("""
                SELECT id FROM pending_task WHERE title = 'Escrito 25'
                """).query(UUID.class).single();
        mvc.perform(post("/pendientes/" + elUltimo + "/cumplir").with(csrf())
                .param("version", "1").session(sesion));

        assertThat(titulosDe(pagina("?sort=title&page=0"))).isEqualTo(antes);
    }

    private List<String> titulosDe(String html) {
        return java.util.regex.Pattern.compile("Escrito \\d\\d").matcher(html)
                .results().map(java.util.regex.MatchResult::group).distinct().toList();
    }
}
