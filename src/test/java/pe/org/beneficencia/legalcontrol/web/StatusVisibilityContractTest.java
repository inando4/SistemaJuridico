package pe.org.beneficencia.legalcontrol.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import pe.org.beneficencia.legalcontrol.integration.PostgresIntegrationTest;
import pe.org.beneficencia.legalcontrol.integration.SesionDePrueba;

/**
 * Un estado deshabilitado sigue viendose donde se uso, pero no se ofrece de nuevo.
 *
 * <p>Es la diferencia entre «dejamos de usar esto» y «esto nunca existio». Lo
 * segundo reescribiria la historia de expedientes ya registrados.
 */
@AutoConfigureMockMvc
class StatusVisibilityContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession jefa;
    private MockHttpSession abogado;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        jefa = SesionDePrueba.entrar(mvc, "jefa@ejemplo.test");
        abogado = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
    }

    private UUID crearEstado(String nombre) throws Exception {
        mvc.perform(post("/estados-procesales").session(jefa).with(csrf())
                .param("name", nombre));
        return jdbc.sql("SELECT id FROM procedural_status WHERE name = :n")
                .param("n", nombre).query(UUID.class).single();
    }

    @Test
    @DisplayName("un abogado consulta el catalogo pero no lo modifica")
    void abogadoSoloConsulta() throws Exception {
        mvc.perform(get("/estados-procesales").session(abogado)).andExpect(status().isOk());
        mvc.perform(post("/estados-procesales").session(abogado).with(csrf())
                        .param("name", "Colado"))
                .andExpect(status().isForbidden());

        Integer total = jdbc.sql("SELECT count(*) FROM procedural_status")
                .query(Integer.class).single();
        assertThat(total).isZero();
    }

    @Test
    @DisplayName("un estado deshabilitado no se ofrece en el formulario de alta")
    void deshabilitadoNoSeOfrece() throws Exception {
        UUID id = crearEstado("En tramite");
        long version = jdbc.sql("SELECT version FROM procedural_status WHERE id = :id")
                .param("id", id).query(Long.class).single();

        String antes = mvc.perform(get("/judiciales/nuevo").session(jefa))
                .andReturn().getResponse().getContentAsString();
        assertThat(antes).contains("En tramite");

        mvc.perform(post("/estados-procesales/" + id + "/disponibilidad").session(jefa).with(csrf())
                .param("enabled", "false").param("version", String.valueOf(version)));

        String despues = mvc.perform(get("/judiciales/nuevo").session(jefa))
                .andReturn().getResponse().getContentAsString();
        assertThat(despues).doesNotContain("En tramite");
    }

    @Test
    @DisplayName("un estado deshabilitado sigue apareciendo en el catalogo, marcado como tal")
    void deshabilitadoSigueVisible() throws Exception {
        UUID id = crearEstado("Archivado");
        long version = jdbc.sql("SELECT version FROM procedural_status WHERE id = :id")
                .param("id", id).query(Long.class).single();

        mvc.perform(post("/estados-procesales/" + id + "/disponibilidad").session(jefa).with(csrf())
                .param("enabled", "false").param("version", String.valueOf(version)));

        String html = mvc.perform(get("/estados-procesales").session(abogado))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Archivado").contains("ya no se ofrece");
    }

    @Test
    @DisplayName("el catalogo vacio explica que hay que llenarlo")
    void catalogoVacioOrienta() throws Exception {
        String html = mvc.perform(get("/estados-procesales").session(jefa))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("catalogo esta vacio").contains("Concluido");
    }
}
