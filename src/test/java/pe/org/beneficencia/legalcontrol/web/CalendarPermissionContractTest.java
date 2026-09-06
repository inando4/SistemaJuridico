package pe.org.beneficencia.legalcontrol.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * El calendario lo consulta cualquiera y lo administra solo JEFA.
 *
 * <p>La consulta abierta es deliberada: todos necesitan poder entender por que
 * un plazo cuenta lo que cuenta. Si no, el numero parece salido de la nada.
 */
@AutoConfigureMockMvc
class CalendarPermissionContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession abogado;
    private MockHttpSession jefa;

    @BeforeEach
    void datos() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        abogado = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
        jefa = SesionDePrueba.entrar(mvc, "jefa@ejemplo.test");
    }

    private int dias() {
        return jdbc.sql("SELECT count(*) FROM non_working_day").query(Integer.class).single();
    }

    @Test
    @DisplayName("un abogado consulta el calendario")
    void abogadoConsulta() throws Exception {
        mvc.perform(get("/non-working-days").session(abogado)).andExpect(status().isOk());
        mvc.perform(get("/non-working-days?year=2027").session(abogado)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("un abogado NO puede agregar dias, ni por peticion directa")
    void abogadoNoAgrega() throws Exception {
        mvc.perform(post("/non-working-days").session(abogado).with(csrf())
                        .param("day", "2027-07-28").param("description", "Colado")
                        .param("kind", "NATIONAL_HOLIDAY"))
                .andExpect(status().isForbidden());

        assertThat(dias()).isZero();
    }

    @Test
    @DisplayName("un abogado NO puede confirmar cobertura")
    void abogadoNoConfirma() throws Exception {
        mvc.perform(post("/non-working-days/2027/review").session(abogado).with(csrf())
                        .param("revision", "1").param("fullYearReviewed", "true"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("la jefa agrega y el dia queda registrado")
    void jefaAgrega() throws Exception {
        mvc.perform(post("/non-working-days").session(jefa).with(csrf())
                        .param("day", "2027-07-28").param("description", "Fiestas Patrias")
                        .param("kind", "NATIONAL_HOLIDAY"))
                .andExpect(status().is3xxRedirection());

        assertThat(dias()).isEqualTo(1);
    }

    @Test
    @DisplayName("la pantalla avisa cuando el ano no esta revisado")
    void avisaSinRevision() throws Exception {
        String html = mvc.perform(get("/non-working-days").session(abogado))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("no esta revisado").contains("Calculo no disponible");
    }

    @Test
    @DisplayName("la casilla de declaracion nunca llega marcada")
    void declaracionSinPreseleccionar() throws Exception {
        mvc.perform(post("/non-working-days").session(jefa).with(csrf())
                .param("day", "2027-01-01").param("description", "Ano nuevo")
                .param("kind", "NATIONAL_HOLIDAY"));

        String html = mvc.perform(get("/non-working-days?year=2027").session(jefa))
                .andReturn().getResponse().getContentAsString();

        var casilla = java.util.regex.Pattern
                .compile("<input[^>]*name=\"fullYearReviewed\"[^>]*>").matcher(html);
        assertThat(casilla.find()).isTrue();
        assertThat(casilla.group()).doesNotContain("checked");
    }
}
