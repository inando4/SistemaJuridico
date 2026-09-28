package pe.org.beneficencia.legalcontrol.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.DigestUtils;
import org.springframework.web.servlet.resource.ResourceUrlProvider;

import pe.org.beneficencia.legalcontrol.integration.ContadorDeConsultas;
import pe.org.beneficencia.legalcontrol.integration.PostgresIntegrationTest;
import pe.org.beneficencia.legalcontrol.integration.SesionDePrueba;

@AutoConfigureMockMvc
@Import(ContadorDeConsultas.class)
class RecursosEstaticosContractTest extends PostgresIntegrationTest {

    private static final List<String> RECURSOS = List.of("/css/app.css", "/css/presentacion.css",
            "/css/estandar.css", "/js/presentacion.js", "/js/formularios.js", "/vendor/htmx.min.js");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ResourceUrlProvider recursos;
    private UUID usuario;
    private MockHttpSession sesion;

    @BeforeEach
    void preparar() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        usuario = SesionDePrueba.crearCuenta(jdbc, encoder, "recursos@ejemplo.test", "LAWYER");
        sesion = SesionDePrueba.entrar(mvc, "recursos@ejemplo.test");
    }

    @Test
    void losRecursosPublicosNoConsultanLaCuentaNiRevocanLaSesion() throws Exception {
        for (String estado : List.of("anonimo", "activo", "revocado")) {
            if (estado.equals("revocado")) {
                jdbc.sql("UPDATE app_user SET auth_version = auth_version + 1 WHERE id = :id")
                        .param("id", usuario).update();
            }
            for (String recurso : RECURSOS) {
                for (String ruta : List.of(recurso, recursos.getForLookupPath(recurso))) {
                    for (HttpMethod metodo : List.of(HttpMethod.GET, HttpMethod.HEAD)) {
                        var peticion = request(metodo, ruta).servletPath(ruta);
                        if (!estado.equals("anonimo")) peticion.session(sesion);
                        ContadorDeConsultas.reiniciar();
                        var respuesta = mvc.perform(peticion).andExpect(status().isOk())
                                .andReturn().getResponse();
                        assertThat(ContadorDeConsultas.total()).as("%s %s (%s)", metodo, ruta, estado)
                                .isZero();
                        assertThat(respuesta.getHeader("Cache-Control"))
                                .contains("max-age=31536000", "public").doesNotContain("no-store");
                    }
                }
            }
        }
        mvc.perform(get("/pendientes").servletPath("/pendientes").session(sesion))
                .andExpect(redirectedUrl("/login?expirada"));
        assertThat(sesion.isInvalid()).isTrue();
    }

    @Test
    void laHuellaDependeDelContenidoYSeEscribeEnElHtml() throws Exception {
        String html = mvc.perform(get("/login")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (String recurso : RECURSOS) {
            try (var entrada = new ClassPathResource("static" + recurso).getInputStream()) {
                String huella = DigestUtils.md5DigestAsHex(entrada);
                int extension = recurso.lastIndexOf('.');
                String versionada = recurso.substring(0, extension) + "-" + huella + recurso.substring(extension);
                assertThat(recursos.getForLookupPath(recurso)).isEqualTo(versionada);
                if (!recurso.startsWith("/vendor/")) assertThat(html).contains(versionada);
                // Una version que ya no corresponde al archivo nunca entrega contenido distinto.
                String inexistente = versionada.replace(huella, "00000000000000000000000000000000");
                mvc.perform(get(inexistente).servletPath(inexistente)).andExpect(status().isNotFound());
            }
        }
        assertThat(html).doesNotContain("htmx.min.js");
    }

    @Test
    void elHtmlPublicoYPrivadoSigueSinAlmacenarse() throws Exception {
        for (String ruta : List.of("/login", "/acceso/canjear", "/", "/pendientes")) {
            var respuesta = mvc.perform(get(ruta).session(sesion)).andExpect(status().isOk())
                    .andReturn().getResponse();
            assertThat(respuesta.getHeader("Cache-Control")).as(ruta).contains("no-store");
        }
    }

    @Test
    void elHelperDePruebasRechazaUnAccesoSinCuenta() {
        assertThatThrownBy(() -> SesionDePrueba.entrar(mvc, "ausente@ejemplo.test"))
                .isInstanceOf(AssertionError.class);
    }
}
