package pe.org.beneficencia.legalcontrol.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import pe.org.beneficencia.legalcontrol.stickynote.StickyNoteService;

/** Contrato de los recordatorios del panel: pegar, quitar, propiedad y CSRF. */
@AutoConfigureMockMvc
class RecordatoriosContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID otro;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        SesionDePrueba.crearCuenta(jdbc, encoder, "yo@ejemplo.test", "LAWYER");
        otro = SesionDePrueba.crearCuenta(jdbc, encoder, "otro@ejemplo.test", "LAWYER");
        sesion = SesionDePrueba.entrar(mvc, "yo@ejemplo.test");
    }

    private int pegadas() {
        return jdbc.sql("SELECT count(*) FROM sticky_note WHERE removed_at IS NULL")
                .query(Integer.class).single();
    }

    private String panel() throws Exception {
        return mvc.perform(get("/").session(sesion)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private UUID pegarComo(UUID autor, String texto, Instant cuando) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO sticky_note (id, owner_id, content, created_at)
                VALUES (:id, :autor, :texto, :cuando)
                """)
                .param("id", id).param("autor", autor).param("texto", texto)
                .param("cuando", Timestamp.from(cuando)).update();
        return id;
    }

    @Test
    @DisplayName("una nota pegada aparece en el panel y vuelve a los recordatorios")
    void pegarYVer() throws Exception {
        mvc.perform(post("/recordatorios").with(csrf()).session(sesion)
                        .param("content", "  Llamar al notario  "))
                .andExpect(redirectedUrl("/#recordatorios"));

        assertThat(pegadas()).isEqualTo(1);
        assertThat(panel()).contains("Llamar al notario").contains("Anotado hoy");
    }

    @Test
    @DisplayName("quitar la saca del panel sin borrar la fila")
    void quitarNoBorra() throws Exception {
        UUID yo = jdbc.sql("SELECT id FROM app_user WHERE email = 'yo@ejemplo.test'")
                .query(UUID.class).single();
        UUID id = pegarComo(yo, "Pedir copia en mesa de partes", Instant.now());

        mvc.perform(post("/recordatorios/" + id + "/quitar").with(csrf()).session(sesion))
                .andExpect(redirectedUrl("/#recordatorios"));
        // El segundo clic de un doble clic no es un error.
        mvc.perform(post("/recordatorios/" + id + "/quitar").with(csrf()).session(sesion))
                .andExpect(redirectedUrl("/#recordatorios"));

        assertThat(pegadas()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM sticky_note").query(Integer.class).single())
                .isEqualTo(1);
        assertThat(panel()).doesNotContain("Pedir copia en mesa de partes");
    }

    @Test
    @DisplayName("las notas de otra persona ni se ven ni se pueden quitar")
    void notasAjenas() throws Exception {
        UUID ajena = pegarComo(otro, "Nota privada de otro", Instant.now());

        assertThat(panel()).doesNotContain("Nota privada de otro");

        // Enviado directamente: ocultar el boton no es autorizacion.
        mvc.perform(post("/recordatorios/" + ajena + "/quitar").with(csrf()).session(sesion))
                .andExpect(status().isNotFound());
        assertThat(pegadas()).isEqualTo(1);
    }

    @Test
    @DisplayName("un texto en blanco se rechaza y no se pega nada")
    void textoEnBlanco() throws Exception {
        mvc.perform(post("/recordatorios").with(csrf()).session(sesion).param("content", "   "))
                .andExpect(redirectedUrl("/#recordatorios"))
                .andExpect(flash().attribute("errorRecordatorio",
                        "Escriba el recordatorio antes de agregarlo."));

        assertThat(pegadas()).isZero();
    }

    @Test
    @DisplayName("un texto demasiado largo se rechaza y se conserva lo escrito")
    void textoLargo() throws Exception {
        String largo = "x".repeat(StickyNoteService.MAXIMO_CARACTERES + 1);

        mvc.perform(post("/recordatorios").with(csrf()).session(sesion).param("content", largo))
                .andExpect(flash().attributeExists("errorRecordatorio"))
                .andExpect(flash().attribute("textoRecordatorio", largo));

        assertThat(pegadas()).isZero();
    }

    @Test
    @DisplayName("con el tope alcanzado no se pega una mas")
    void tope() throws Exception {
        UUID yo = jdbc.sql("SELECT id FROM app_user WHERE email = 'yo@ejemplo.test'")
                .query(UUID.class).single();
        for (int i = 0; i < StickyNoteService.MAXIMO_PEGADAS; i++) {
            pegarComo(yo, "Nota " + i, Instant.now());
        }

        mvc.perform(post("/recordatorios").with(csrf()).session(sesion).param("content", "Una mas"))
                .andExpect(flash().attributeExists("errorRecordatorio"));

        assertThat(pegadas()).isEqualTo(StickyNoteService.MAXIMO_PEGADAS);
    }

    @Test
    @DisplayName("la nota dice cuanto lleva pegada, en dias naturales")
    void antiguedad() throws Exception {
        UUID yo = jdbc.sql("SELECT id FROM app_user WHERE email = 'yo@ejemplo.test'")
                .query(UUID.class).single();
        pegarComo(yo, "Nota vieja", Instant.now().minus(5, ChronoUnit.DAYS));

        assertThat(panel()).contains("Anotado hace 5 días");
    }

    @Test
    @DisplayName("sin elegir color la nota sale amarilla")
    void colorPorDefecto() throws Exception {
        mvc.perform(post("/recordatorios").with(csrf()).session(sesion)
                .param("content", "Sin color elegido"));

        assertThat(jdbc.sql("SELECT color FROM sticky_note").query(String.class).single())
                .isEqualTo("YELLOW");
        assertThat(panel()).contains("class=\"nota nota-amarillo\"");
    }

    @Test
    @DisplayName("el color elegido se guarda y pinta la nota")
    void colorElegido() throws Exception {
        mvc.perform(post("/recordatorios").with(csrf()).session(sesion)
                .param("content", "Nota rosa").param("color", "PINK"));

        assertThat(jdbc.sql("SELECT color FROM sticky_note").query(String.class).single())
                .isEqualTo("PINK");
        assertThat(panel()).contains("class=\"nota nota-rosa\"")
                .contains("data-confirmar-color=\"rosa\"");
    }

    @Test
    @DisplayName("un color fuera de la lista se rechaza y se conserva lo escrito")
    void colorFueraDeLaLista() throws Exception {
        mvc.perform(post("/recordatorios").with(csrf()).session(sesion)
                        .param("content", "Nota roja").param("color", "RED"))
                .andExpect(flash().attribute("errorRecordatorio", "Elija uno de los colores de la lista."))
                .andExpect(flash().attribute("textoRecordatorio", "Nota roja"));

        assertThat(pegadas()).isZero();
    }

    @Test
    @DisplayName("quitar pide confirmacion con el texto de la nota")
    void quitarPideConfirmacion() throws Exception {
        UUID yo = jdbc.sql("SELECT id FROM app_user WHERE email = 'yo@ejemplo.test'")
                .query(UUID.class).single();
        pegarComo(yo, "Revisar <b>cédulas</b>", Instant.now());

        String html = panel();
        // El texto viaja escapado: una nota no puede inyectar marcado en el dialogo.
        assertThat(html).contains("data-confirmar=\"Revisar &lt;b&gt;cédulas&lt;/b&gt;\"")
                .contains("id=\"dialogo-confirmar\"");
    }

    @Test
    @DisplayName("el POST exige CSRF")
    void exigeCsrf() throws Exception {
        mvc.perform(post("/recordatorios").session(sesion).param("content", "Sin token"))
                .andExpect(status().isForbidden());

        assertThat(pegadas()).isZero();
    }
}
