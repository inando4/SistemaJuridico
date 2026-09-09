package pe.org.beneficencia.legalcontrol.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Contrato de la pantalla de configuracion (insumo, seccion 36).
 *
 * <p>Lo que de verdad se comprueba aqui es la asimetria: los catalogos se enlazan para
 * todos porque cualquiera puede leerlos, y las cuentas solo para la jefatura porque
 * {@code /usuarios} rechaza a los demas ya en el {@code GET}. Y que ocultar el enlace
 * <b>no</b> es lo que protege esa pantalla.
 */
@AutoConfigureMockMvc
class ConfiguracionContractTest extends PostgresIntegrationTest {

    /** Los seis destinos que ve cualquiera. */
    private static final String[] COMUNES = {
            "/dias-no-laborables", "/tipos-de-pendiente", "/prioridades",
            "/estados-de-pendiente", "/estados-procesales", "/estados-administrativos"};

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession comoJefa;
    private MockHttpSession comoAbogado;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        comoJefa = SesionDePrueba.entrar(mvc, "jefa@ejemplo.test");
        comoAbogado = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
    }

    private String pantalla(MockHttpSession sesion) throws Exception {
        return mvc.perform(get("/configuracion").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("cualquier cuenta activa llega a los seis destinos comunes")
    void destinosComunesParaTodos() throws Exception {
        for (String destino : COMUNES) {
            assertThat(pantalla(comoAbogado))
                    .as("%s no tenia ningun enlace entrante antes de esta pantalla", destino)
                    .contains(destino);
            assertThat(pantalla(comoJefa)).contains(destino);
        }
    }

    @Test
    @DisplayName("solo la jefatura ve el enlace a cuentas")
    void cuentasSoloParaLaJefa() throws Exception {
        assertThat(pantalla(comoJefa)).contains("/usuarios");

        assertThat(pantalla(comoAbogado))
                .as("un enlace que siempre devolveria rechazo es peor que ningun enlace")
                .doesNotContain("/usuarios");
    }

    @Test
    @DisplayName("ocultar el enlace no es la autorizacion: /usuarios sigue rechazando")
    void ocultarNoEsAutorizar() throws Exception {
        // Pedido directamente, sin pasar por la pantalla. Es la leccion de la 005.
        mvc.perform(get("/usuarios").session(comoAbogado))
                .andExpect(status().is4xxClientError());

        mvc.perform(get("/usuarios").session(comoJefa))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("cada destino lleva una linea que dice para que sirve")
    void cadaEnlaceSeExplica() throws Exception {
        String html = pantalla(comoJefa);

        // Quien entra por primera vez no deberia tener que abrir las siete para saber
        // cual necesita.
        assertThat(html).contains("Los feriados de ley");
        assertThat(html).contains("Es lo que se elige al registrar");
        assertThat(html).contains("Solo la jefatura");
    }

    @Test
    @DisplayName("sin sesion se va al acceso")
    void sinSesion() throws Exception {
        mvc.perform(get("/configuracion")).andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("la navegacion marca la seccion en curso")
    void seccionEnCurso() throws Exception {
        assertThat(pantalla(comoJefa)).contains("aria-current");
    }
}
