package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultMatcher;

import pe.org.beneficencia.legalcontrol.access.CuentaActual;

/** Una redireccion al login nunca cuenta como una pantalla rapida o barata. */
public final class PantallaDePrueba {

    private PantallaDePrueba() { }

    public static ResultMatcher autenticada() {
        return resultado -> {
            status().isOk().match(resultado);
            content().contentTypeCompatibleWith(MediaType.TEXT_HTML).match(resultado);
            var sesion = resultado.getRequest().getSession(false);
            assertThat(sesion).isNotNull();
            assertThat(sesion.getAttribute(CuentaActual.ATRIBUTO_SESION))
                    .isInstanceOf(CuentaActual.class);
            assertThat(resultado.getModelAndView()).isNotNull();
            assertThat(resultado.getModelAndView().getViewName())
                    .doesNotContain("login", "redirect:", "error");
            assertThat(resultado.getResponse().getContentAsString()).contains("<main");
        };
    }
}
