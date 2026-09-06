package pe.org.beneficencia.legalcontrol.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import pe.org.beneficencia.legalcontrol.integration.PostgresIntegrationTest;

/**
 * Toda escritura exige CSRF.
 *
 * <p>Sin esto, una pagina cualquiera podria hacer que el navegador de un abogado
 * con sesion abierta enviara un formulario a este sistema sin que el se entere.
 */
@AutoConfigureMockMvc
class CsrfContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("un POST sin token CSRF se rechaza")
    void sinTokenSeRechaza() throws Exception {
        mvc.perform(post("/login").param("email", "x@y.test").param("password", "z"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un POST con token CSRF no se rechaza por ese motivo")
    void conTokenNoSeRechazaPorCsrf() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("email", "x@y.test").param("password", "z"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("el cierre de sesion tambien exige CSRF")
    void logoutExigeCsrf() throws Exception {
        mvc.perform(post("/logout")).andExpect(status().isForbidden());
    }
}
