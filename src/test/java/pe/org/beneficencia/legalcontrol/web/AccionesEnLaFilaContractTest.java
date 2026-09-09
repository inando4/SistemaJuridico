package pe.org.beneficencia.legalcontrol.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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

/** Las acciones rapidas por fila de la seccion 25. */
@AutoConfigureMockMvc
class AccionesEnLaFilaContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession mia;
    private UUID yo;
    private UUID mio;
    private UUID ajeno;
    private UUID cumplido;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        yo = SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");
        UUID otra = SesionDePrueba.crearCuenta(jdbc, encoder, "otra@ejemplo.test", "LAWYER");
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        mia = SesionDePrueba.entrar(mvc, "yo@ejemplo.test");

        mio = pendiente(yo, "Escrito propio", false, true);
        ajeno = pendiente(otra, "Escrito de otra", false, true);
        cumplido = pendiente(yo, "Escrito ya presentado", true, true);
    }

    private UUID pendiente(UUID owner, String titulo, boolean yaCumplido, boolean visible) {
        UUID id = UUID.randomUUID();
        Timestamp ahora = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO pending_task (id, owner_id, title, registered_at, completed_at,
                                          active, created_at, updated_at, version)
                VALUES (:id, :o, :t, :hoy, :fin, :vis, :ts, :ts, 1)
                """)
                .param("id", id).param("o", owner).param("t", titulo)
                .param("hoy", LocalDate.now()).param("fin", yaCumplido ? ahora : null)
                .param("vis", visible).param("ts", ahora).update();
        return id;
    }

    private String listado(String query) throws Exception {
        return mvc.perform(get("/pendientes" + query).session(mia))
                .andReturn().getResponse().getContentAsString();
    }

    /** El trozo de HTML de la fila de un pendiente, para no afirmar sobre toda la página. */
    private String filaDe(String html, String titulo) {
        int fin = html.indexOf(titulo);
        assertThat(fin).as("la fila de «%s» tiene que estar en la pagina", titulo)
                .isGreaterThan(-1);
        int inicio = html.lastIndexOf("<tr", fin);
        int cierre = html.indexOf("</tr>", fin);
        return html.substring(inicio, cierre);
    }

    @Test
    @DisplayName("cada fila ofrece ver, editar, cumplido y cancelar (RF-010)")
    void lasCuatroAcciones() throws Exception {
        String fila = filaDe(listado(""), "Escrito propio");

        assertThat(fila)
                .contains(">Ver<")
                .contains(">Editar<")
                .contains(">Cumplido<")
                .contains(">Cancelar<");
    }

    @Test
    @DisplayName("«No cumplido» y «Reprogramar» NO estan en la fila (RF-011)")
    void loQueNoVaEnLaFila() throws Exception {
        // Piden una fecha o un motivo. Un formulario desplegado dentro de una tabla de
        // veinticinco filas estorba mas de lo que ahorra.
        assertThat(listado(""))
                .doesNotContain("/no-cumplido")
                .doesNotContain("/reprogramar");
    }

    @Test
    @DisplayName("cumplir desde la fila devuelve a la misma pagina y los mismos filtros (RF-012)")
    void vuelveDondeEstaba() throws Exception {
        mvc.perform(post("/pendientes/" + mio + "/cumplir").with(csrf())
                        .param("version", "1")
                        .param("filtros", "?sort=title&page=1")
                        .session(mia))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pendientes?sort=title&page=1"));
    }

    @Test
    @DisplayName("sin filtros, cumplir sigue volviendo a la ficha como siempre")
    void desdeLaFichaNoCambia() throws Exception {
        mvc.perform(post("/pendientes/" + mio + "/cumplir").with(csrf())
                        .param("version", "1").session(mia))
                .andExpect(redirectedUrl("/pendientes/" + mio));
    }

    @Test
    @DisplayName("cancelar desde la fila tambien conserva el sitio")
    void cancelarConservaElSitio() throws Exception {
        mvc.perform(post("/pendientes/" + mio + "/cancelar").with(csrf())
                        .param("version", "1")
                        .param("filtros", "?ownerId=" + yo + "&page=2")
                        .session(mia))
                .andExpect(redirectedUrl("/pendientes?ownerId=" + yo + "&page=2"));
    }

    @Test
    @DisplayName("la fila de otra persona no ofrece las acciones (RF-013)")
    void nadaSobreLoAjeno() throws Exception {
        String fila = filaDe(listado("?visibility=all"), "Escrito de otra");

        assertThat(fila)
                .as("leer es compartido: el titulo sigue enlazando")
                .contains(">Ver<")
                .as("pero no se ofrece lo que el servidor va a rechazar")
                .doesNotContain(">Cumplido<")
                .doesNotContain(">Cancelar<")
                .doesNotContain(">Editar<");
    }

    @Test
    @DisplayName("a la jefa si se le ofrecen sobre lo ajeno (RF-013)")
    void laJefaSiVeLasAcciones() throws Exception {
        MockHttpSession deLaJefa = SesionDePrueba.entrar(mvc, "jefa@ejemplo.test");
        String html = mvc.perform(get("/pendientes?visibility=all").session(deLaJefa))
                .andReturn().getResponse().getContentAsString();

        assertThat(filaDe(html, "Escrito de otra")).contains(">Cumplido<").contains(">Cancelar<");
    }

    @Test
    @DisplayName("un pendiente ya cumplido no ofrece cumplirse otra vez (RF-014)")
    void loCumplidoNoSeCumpleDosVeces() throws Exception {
        String fila = filaDe(listado("?visibility=all"), "Escrito ya presentado");

        assertThat(fila)
                .doesNotContain(">Cumplido<")
                .as("pero si se puede cancelar: sigue siendo un registro que puede sobrar")
                .contains(">Cancelar<");
    }

    @Test
    @DisplayName("un pendiente cancelado ofrece devolver, no cancelar (RF-014)")
    void loCanceladoOfreceDevolver() throws Exception {
        jdbc.sql("UPDATE pending_task SET active = false WHERE id = :id")
                .param("id", mio).update();

        String fila = filaDe(listado("?visibility=inactive"), "Escrito propio");

        assertThat(fila)
                .contains(">Devolver<")
                .doesNotContain(">Cancelar<");
    }

    @Test
    @DisplayName("el servidor sigue rechazando aunque el boton no se dibuje (D5)")
    void elServidorNoSeFiaDeLaPantalla() throws Exception {
        // Es lo que separa «no ofrecer» de «no permitir». La fila decide que se pinta;
        // quien puede lo decide el servicio.
        mvc.perform(post("/pendientes/" + ajeno + "/cumplir").with(csrf())
                        .param("version", "1")
                        .param("filtros", "?page=0")
                        .session(mia))
                .andExpect(status().isForbidden());

        mvc.perform(post("/pendientes/" + ajeno + "/cancelar").with(csrf())
                        .param("version", "1").session(mia))
                .andExpect(status().isForbidden());
    }
}
