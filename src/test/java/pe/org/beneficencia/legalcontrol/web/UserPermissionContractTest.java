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
 * Administrar cuentas es exclusivo de JEFA, tambien ante peticiones directas.
 *
 * <p>Y las pantallas que muestran un codigo no deben quedar en cache: con el
 * boton atras, un codigo visible en un equipo compartido es un codigo entregado
 * a quien pase por ahi.
 */
@AutoConfigureMockMvc
class UserPermissionContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession abogado;
    private MockHttpSession jefa;
    private UUID cuentaAjena;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        SesionDePrueba.crearCuenta(jdbc, encoder, "respaldo@ejemplo.test", "HEAD");
        cuentaAjena = SesionDePrueba.crearCuenta(jdbc, encoder, "otro@ejemplo.test", "LAWYER");
        SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");

        abogado = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
        jefa = SesionDePrueba.entrar(mvc, "jefa@ejemplo.test");
    }

    @Test
    @DisplayName("un abogado obtiene 403 en todas las rutas de cuentas")
    void abogadoSinAcceso() throws Exception {
        mvc.perform(get("/usuarios").session(abogado)).andExpect(status().isForbidden());
        mvc.perform(get("/usuarios/nuevo").session(abogado)).andExpect(status().isForbidden());
        mvc.perform(post("/usuarios").session(abogado).with(csrf())
                        .param("name", "Colado").param("email", "colado@x.test")
                        .param("role", "HEAD"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/usuarios/" + cuentaAjena + "/desactivar").session(abogado).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/usuarios/" + cuentaAjena + "/restablecer").session(abogado).with(csrf()))
                .andExpect(status().isForbidden());

        Integer cuentas = jdbc.sql("SELECT count(*) FROM app_user").query(Integer.class).single();
        assertThat(cuentas).as("no debe haberse creado ninguna cuenta").isEqualTo(4);
    }

    @Test
    @DisplayName("la jefa da de alta y el codigo se muestra una sola vez")
    void jefaDaDeAlta() throws Exception {
        var respuesta = mvc.perform(post("/usuarios").session(jefa).with(csrf())
                        .param("name", "Abogado Nuevo")
                        .param("email", "nuevo@ejemplo.test")
                        .param("role", "LAWYER"))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertThat(respuesta.getContentAsString()).contains("se muestra una sola vez");
        assertThat(respuesta.getHeader("Cache-Control")).contains("no-store");
        assertThat(respuesta.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    }

    @Test
    @DisplayName("la pantalla de canje tampoco queda en cache")
    void canjeSinCache() throws Exception {
        var respuesta = mvc.perform(get("/acceso/canjear"))
                .andExpect(status().isOk()).andReturn().getResponse();

        assertThat(respuesta.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    @DisplayName("cualquiera puede cambiar su propia contrasena, sin ser jefa")
    void cambioPropioAbierto() throws Exception {
        mvc.perform(get("/cuenta/contrasena").session(abogado)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("un rol invalido en el alta se rechaza")
    void rolInvalido() throws Exception {
        String html = mvc.perform(post("/usuarios").session(jefa).with(csrf())
                        .param("name", "X").param("email", "x@ejemplo.test")
                        .param("role", "SUPERADMIN"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("rol indicado no es válido");
    }
}
