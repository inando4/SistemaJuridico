package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** La entrada al sistema es el panel del dia, y se vuelve a el desde donde sea. */
@AutoConfigureMockMvc
class EntradaAlPanelIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;

    @BeforeEach
    void preparar() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        var usuario = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        int ano = LocalDate.now().getYear();
        DatosSinteticos.sembrarCalendario(jdbc, usuario, ano - 1, ano, ano + 1);
        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
    }

    @Test
    @DisplayName("al iniciar sesion se aterriza en el panel del dia")
    void inicioDeSesionLlevaAlPanel() throws Exception {
        var respuesta = mvc.perform(
                        org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestBuilders.formLogin("/login")
                                .user("email", "abogado@ejemplo.test")
                                .password(SesionDePrueba.CONTRASENA))
                .andReturn().getResponse();

        assertThat(respuesta.getRedirectedUrl())
                .as("la primera pregunta al entrar es «que tengo que hacer hoy»")
                .isEqualTo("/");
    }

    @Test
    @DisplayName("quien no tiene sesion no ve el panel")
    void sinSesionNoHayPanel() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("quien no tiene sesion tampoco ve las alertas")
    void sinSesionNoHayAlertas() throws Exception {
        mvc.perform(get("/alertas"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("desde cualquier pantalla se puede volver al panel")
    void navegacionPresenteEnTodas() throws Exception {
        for (String ruta : List.of("/", "/alertas", "/pendientes", "/pendientes/hoy",
                "/cumplidos", "/judiciales", "/administrativos")) {
            String html = mvc.perform(get(ruta).session(sesion))
                    .andReturn().getResponse().getContentAsString();

            assertThat(html)
                    .as("«%s» debe ofrecer la vuelta al panel: un panel al que hay que "
                            + "escribir la URL no sirve de pantalla de entrada", ruta)
                    .contains(">Panel<");
            assertThat(html)
                    .as("«%s» debe enlazar tambien a las alertas", ruta)
                    .contains(">Alertas<");
        }
    }
}
