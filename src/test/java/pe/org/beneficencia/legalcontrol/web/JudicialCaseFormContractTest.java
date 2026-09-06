package pe.org.beneficencia.legalcontrol.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
 * Contrato del formulario de alta.
 *
 * <p>Lo que se comprueba de verdad: que un dato invalido <b>no crea un registro
 * a medias</b> y que el formulario vuelve con lo que la persona habia escrito.
 * Perder media pagina de datos por un monto mal tecleado es la clase de detalle
 * que hace que la gente vuelva al Excel.
 */
@AutoConfigureMockMvc
class JudicialCaseFormContractTest extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;

    @BeforeEach
    void preparar() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");
    }

    private int expedientes() {
        return jdbc.sql("SELECT count(*) FROM judicial_case").query(Integer.class).single();
    }

    @Test
    @DisplayName("un alta con solo el numero de expediente se guarda")
    void altaMinima() throws Exception {
        mvc.perform(post("/judicial-cases").session(sesion).with(csrf())
                        .param("caseNumber", "EXP-0001-2026"))
                .andExpect(status().is3xxRedirection());

        assertThat(expedientes()).isEqualTo(1);
    }

    @Test
    @DisplayName("el numero conserva ceros iniciales, guiones y mayusculas")
    void numeroSeConservaTalCual() throws Exception {
        mvc.perform(post("/judicial-cases").session(sesion).with(csrf())
                .param("caseNumber", "00123-2026-0-0401-JR-CI-01"));

        String guardado = jdbc.sql("SELECT case_number FROM judicial_case")
                .query(String.class).single();
        assertThat(guardado).isEqualTo("00123-2026-0-0401-JR-CI-01");
    }

    @Test
    @DisplayName("un alta con todos los datos los recupera intactos")
    void altaCompleta() throws Exception {
        mvc.perform(post("/judicial-cases").session(sesion).with(csrf())
                .param("caseNumber", "EXP-0002-2026")
                .param("claimant", "Sociedad de Beneficencia")
                .param("respondent", "Ocupante precario")
                .param("subject", "Desalojo")
                .param("deadline", "2026-12-31")
                .param("amount", "15000,50")
                .param("notes", "Observacion de prueba"));

        var fila = jdbc.sql("""
                SELECT claimant, respondent, subject, deadline, amount, notes
                FROM judicial_case WHERE case_number = 'EXP-0002-2026'
                """).query().singleRow();

        assertThat(fila.get("claimant")).isEqualTo("Sociedad de Beneficencia");
        assertThat(fila.get("subject")).isEqualTo("Desalojo");
        assertThat(fila.get("amount").toString()).isEqualTo("15000.50");
    }

    @Test
    @DisplayName("numero vacio, fecha imposible o monto no numerico no crean nada")
    void datosInvalidosNoCreanRegistroParcial() throws Exception {
        String[][] casos = {
                {"", "2026-12-31", "100"},
                {"EXP-X", "2026-02-31", "100"},
                {"EXP-Y", "2026-12-31", "no es un monto"},
                {"EXP-Z", "2026-12-31", "10.123"},
        };
        for (String[] caso : casos) {
            mvc.perform(post("/judicial-cases").session(sesion).with(csrf())
                            .param("caseNumber", caso[0])
                            .param("deadline", caso[1])
                            .param("amount", caso[2]))
                    .andExpect(status().isOk());   // vuelve al formulario, no redirige
        }
        assertThat(expedientes()).as("ningun dato invalido debe dejar registro").isZero();
    }

    @Test
    @DisplayName("ante un error, el formulario devuelve lo que la persona escribio")
    void conservaLoEscrito() throws Exception {
        String html = mvc.perform(post("/judicial-cases").session(sesion).with(csrf())
                        .param("caseNumber", "")
                        .param("claimant", "Sociedad de Beneficencia")
                        .param("subject", "Desalojo"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Sociedad de Beneficencia").contains("Desalojo");
    }
}
